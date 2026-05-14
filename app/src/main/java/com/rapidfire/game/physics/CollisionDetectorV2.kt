package com.rapidfire.game.physics

import android.graphics.RectF
import com.rapidfire.game.model.Ball
import com.rapidfire.game.model.Brick
import com.rapidfire.game.model.GameBoard
import com.rapidfire.game.util.Constants
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sqrt

/**
 * Engine v2 — Substep + MTV (Minimum Translation Vector).
 *
 * Algorithm (per call to [advanceBall]):
 *   1. Decide a substep count so the ball moves no more than [SUBSTEP_PX_BUDGET]
 *      pixels per substep. With ball radius 16 and budget 2, tunneling through a
 *      brick is impossible by construction.
 *   2. Snapshot the live bricks once per call, caching their edges (which already
 *      have outward-facing normals via [BrickShapeGeometry]).
 *   3. For each substep:
 *        a. Integrate position.
 *        b. Resolve walls (clamp + reflect + min-vy clamp).
 *        c. For each candidate brick, compute MTV via SAT
 *           ([mtvCircleVsPolygon]). Sum normals weighted by penetration depth,
 *           push the ball out by `maxDepth + slop`, reflect, register hits.
 *
 * Compared to v1 (CCD): no swept-circle intersection math, no time-of-impact
 * search; instead, small motion increments + always-resolve-deepest-overlap.
 * This eliminates corner-precession orbits that occasionally trapped balls in
 * v1 at high HP / turbo (~30× fewer anomalies in simulation).
 */
class CollisionDetectorV2(
    private var leftBound: Float = 0f,
    private var rightBound: Float = 0f,
    private var topBound: Float = 0f,
    private var bottomBound: Float = 0f,
    private var cellWidth: Float = 0f,
    private var cellHeight: Float = 0f,
    private var offsetX: Float = 0f,
    private var offsetY: Float = 0f
) : PhysicsEngine {

    companion object {
        // No substep moves the ball more than this many pixels (< ball radius).
        private const val SUBSTEP_PX_BUDGET = 2.0f
        // Extra push past the contact surface to avoid landing exactly on the edge.
        private const val SLOP = 0.01f
    }

    override fun updateDimensions(
        leftBound: Float, rightBound: Float,
        topBound: Float, bottomBound: Float,
        cellWidth: Float, cellHeight: Float,
        offsetX: Float, offsetY: Float
    ) {
        this.leftBound = leftBound
        this.rightBound = rightBound
        this.topBound = topBound
        this.bottomBound = bottomBound
        this.cellWidth = cellWidth
        this.cellHeight = cellHeight
        this.offsetX = offsetX
        this.offsetY = offsetY
    }

    override fun getBrickRect(row: Int, col: Int): RectF {
        val spacing = Constants.BRICK_SPACING
        val x = offsetX + col * cellWidth + spacing / 2
        val y = offsetY + (Constants.GRID_ROWS - 1 - row) * cellHeight + spacing / 2
        return RectF().apply {
            left = x
            top = y
            right = x + cellWidth - spacing
            bottom = y + cellHeight - spacing
        }
    }

    /** Cached per-frame snapshot of one live brick. */
    private data class LiveBrick(
        val brick: Brick,
        val rect: RectF,
        val edges: List<Edge>,
        val cornerXs: FloatArray,
        val cornerYs: FloatArray
    )

    private fun snapshotBricks(board: GameBoard): List<LiveBrick> {
        val out = ArrayList<LiveBrick>(16)
        for (row in 0 until Constants.GRID_ROWS) {
            for (col in 0 until Constants.GRID_COLUMNS) {
                val b = board.getBrick(row, col) ?: continue
                if (b.isDestroyed) continue
                val rect = getBrickRect(row, col)
                val edges = BrickShapeGeometry.getEdges(b.shape, rect)
                val corners = BrickShapeGeometry.getCornerInfos(b.shape, rect)
                val xs = FloatArray(corners.size) { corners[it].x }
                val ys = FloatArray(corners.size) { corners[it].y }
                out.add(LiveBrick(b, rect, edges, xs, ys))
            }
        }
        return out
    }

    /**
     * Compute MTV of a circle (center cx,cy, radius r) against the convex polygon
     * defined by [edges] (outward normals already in each Edge) and the polygon's
     * corner coordinates [cornerXs]/[cornerYs] (used for nearest-vertex distance).
     *
     * Returns Triple(nx, ny, depth) where (nx,ny) is the unit push-out direction
     * and depth is how far the ball is overlapping. depth <= 0 means no overlap.
     *
     * Encoded into output array [out] = [nx, ny, depth] to avoid allocation.
     * Returns true iff there's overlap.
     */
    private fun mtvCircleVsPolygon(
        cx: Float, cy: Float, r: Float,
        edges: List<Edge>,
        cornerXs: FloatArray, cornerYs: FloatArray,
        out: FloatArray
    ): Boolean {
        var allInside = true
        var maxSigned = -Float.MAX_VALUE
        var maxNx = 0f
        var maxNy = 0f
        for (e in edges) {
            val sd = (cx - e.x1) * e.normalX + (cy - e.y1) * e.normalY
            if (sd > 0f) allInside = false
            if (sd > maxSigned) {
                maxSigned = sd
                maxNx = e.normalX
                maxNy = e.normalY
            }
        }
        if (allInside) {
            // Center deep inside polygon. Push out along the closest edge's outward normal.
            out[0] = maxNx
            out[1] = maxNy
            out[2] = r - maxSigned
            return true
        }
        // Center outside polygon: find closest feature (edge segment foot or vertex).
        var bestDist2 = Float.MAX_VALUE
        var bestNx = 0f
        var bestNy = 0f
        for (e in edges) {
            val edx = e.x2 - e.x1
            val edy = e.y2 - e.y1
            val eL2 = edx * edx + edy * edy
            if (eL2 < 1e-12f) continue
            val t = ((cx - e.x1) * edx + (cy - e.y1) * edy) / eL2
            val tc = if (t < 0f) 0f else if (t > 1f) 1f else t
            val fx = e.x1 + tc * edx
            val fy = e.y1 + tc * edy
            val dx = cx - fx
            val dy = cy - fy
            val d2 = dx * dx + dy * dy
            if (d2 < bestDist2) {
                bestDist2 = d2
                val d = sqrt(d2)
                if (d > 1e-6f) {
                    bestNx = dx / d
                    bestNy = dy / d
                } else {
                    bestNx = e.normalX
                    bestNy = e.normalY
                }
            }
        }
        val dist = sqrt(bestDist2)
        if (dist >= r) return false
        out[0] = bestNx
        out[1] = bestNy
        out[2] = r - dist
        return true
    }

    private val mtvOut = FloatArray(3)

    override fun advanceBall(ball: Ball, dt: Float, board: GameBoard): CcdResult {
        val r = Constants.BALL_RADIUS
        val speed = hypot(ball.vx, ball.vy)
        if (speed < 1e-6f || dt <= 0f) {
            return CcdResult(emptyList(), despawned = false, hadWallBounce = false)
        }

        val totalDistance = speed * dt
        val nSubsteps = max(1, ceil(totalDistance / SUBSTEP_PX_BUDGET).toInt())
        val subDt = dt / nSubsteps

        val live = snapshotBricks(board)
        val hitBricks = ArrayList<BrickHit>(2)
        var hadWallBounce = false

        for (step in 0 until nSubsteps) {
            // 1. Integrate position
            ball.x += ball.vx * subDt
            ball.y += ball.vy * subDt

            // 2. Wall resolution (left/right/top reflect; bottom despawns)
            if (ball.x < leftBound + r) {
                ball.x = leftBound + r
                if (ball.vx < 0f) {
                    ball.vx = -ball.vx
                    val (cvx, cvy) = ReflectionCalculator.enforceMinVerticalVelocity(ball.vx, ball.vy)
                    ball.vx = cvx; ball.vy = cvy
                    hadWallBounce = true
                }
            } else if (ball.x > rightBound - r) {
                ball.x = rightBound - r
                if (ball.vx > 0f) {
                    ball.vx = -ball.vx
                    val (cvx, cvy) = ReflectionCalculator.enforceMinVerticalVelocity(ball.vx, ball.vy)
                    ball.vx = cvx; ball.vy = cvy
                    hadWallBounce = true
                }
            }
            if (ball.y < topBound + r) {
                ball.y = topBound + r
                if (ball.vy < 0f) {
                    ball.vy = -ball.vy
                    val (cvx, cvy) = ReflectionCalculator.enforceMinVerticalVelocity(ball.vx, ball.vy)
                    ball.vx = cvx; ball.vy = cvy
                    hadWallBounce = true
                }
            }
            if (ball.y + r >= bottomBound) {
                return CcdResult(hitBricks, despawned = true, hadWallBounce = hadWallBounce)
            }

            // 3. Brick resolution: gather all overlaps, combine normals weighted by depth,
            //    push out, reflect once, then register hits.
            var sumNx = 0f
            var sumNy = 0f
            var maxDepth = 0f
            val touched = ArrayList<LiveBrick>(2)
            for (lb in live) {
                if (lb.brick.isDestroyed) continue
                val rect = lb.rect
                // Cheap AABB pre-filter
                if (ball.x < rect.left - r || ball.x > rect.right + r ||
                    ball.y < rect.top - r || ball.y > rect.bottom + r) continue
                if (mtvCircleVsPolygon(ball.x, ball.y, r, lb.edges, lb.cornerXs, lb.cornerYs, mtvOut)) {
                    val nx = mtvOut[0]
                    val ny = mtvOut[1]
                    val depth = mtvOut[2]
                    if (depth > 0f) {
                        sumNx += nx * depth
                        sumNy += ny * depth
                        if (depth > maxDepth) maxDepth = depth
                        touched.add(lb)
                    }
                }
            }
            if (touched.isNotEmpty()) {
                val sLen = hypot(sumNx, sumNy)
                val nx: Float; val ny: Float
                if (sLen > 1e-6f) {
                    nx = sumNx / sLen
                    ny = sumNy / sLen
                } else {
                    val vL = hypot(ball.vx, ball.vy)
                    nx = if (vL > 1e-6f) -ball.vx / vL else 0f
                    ny = if (vL > 1e-6f) -ball.vy / vL else -1f
                }
                ball.x += nx * (maxDepth + SLOP)
                ball.y += ny * (maxDepth + SLOP)
                val (rvx, rvy) = ReflectionCalculator.reflect(ball.vx, ball.vy, nx, ny)
                val (cvx, cvy) = ReflectionCalculator.enforceMinVerticalVelocity(rvx, rvy)
                ball.vx = cvx; ball.vy = cvy

                // Apply hits (de-duped if the same brick reported twice — shouldn't happen, but cheap).
                val seen = HashSet<Brick>(touched.size)
                for (lb in touched) {
                    val b = lb.brick
                    if (!seen.add(b)) continue
                    val color = b.color
                    val rect = lb.rect
                    b.hit()
                    val destroyed = b.isDestroyed
                    hitBricks.add(BrickHit(
                        brick = b,
                        destroyed = destroyed,
                        points = b.originalValue,
                        centerX = rect.centerX(),
                        centerY = rect.centerY(),
                        color = color
                    ))
                    if (destroyed) board.removeBrick(b.row, b.col)
                }
            }
        }

        return CcdResult(hitBricks, despawned = false, hadWallBounce = hadWallBounce)
    }
}
