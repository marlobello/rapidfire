package com.rapidfire.game.physics

import android.graphics.RectF
import com.rapidfire.game.model.Ball
import com.rapidfire.game.model.Brick
import com.rapidfire.game.model.GameBoard
import com.rapidfire.game.util.Constants
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** A single brick hit recorded during CCD advancement. */
data class BrickHit(
    val brick: Brick,
    val destroyed: Boolean,
    val points: Int,
    val centerX: Float,
    val centerY: Float,
    val color: Int
)

/** Result of advancing a ball for one frame via CCD. */
data class CcdResult(
    val hitBricks: List<BrickHit>,
    val despawned: Boolean,
    val hadWallBounce: Boolean
)

/**
 * One contact found during the swept search. May be from an edge, a corner, or a wall.
 * Multiple contacts that occur at (essentially) the same time-of-impact are combined
 * by averaging their normals so corner-on-corner / edge-on-edge ties produce a sensible
 * combined reflection.
 */
private data class Contact(
    val nx: Float,
    val ny: Float,
    val brick: Brick?,
    val isWall: Boolean,
    // For corner contacts the normal must be re-computed at the actual impact position
    // (ball center moves to bestTime), not at the start-of-step position.
    val isCorner: Boolean,
    val cornerX: Float,
    val cornerY: Float
)

class CollisionDetector(
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
        private const val EPSILON = 0.0001f
        // Treat contacts within this absolute time delta as simultaneous.
        private const val TIME_TIE_EPSILON = 1e-5f
        private const val NUDGE = 0.05f
        private const val MAX_BOUNCES = 16
        private const val MAX_DEPENETRATION_ITERS = 8
        private const val DEPENETRATION_SLOP = 0.1f
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

    /** Get the screen-space rectangle for a brick at (row, col). Row 0 = bottom. */
    override fun getBrickRect(row: Int, col: Int): RectF {
        val spacing = Constants.BRICK_SPACING
        val x = offsetX + col * cellWidth + spacing / 2
        val y = offsetY + (Constants.GRID_ROWS - 1 - row) * cellHeight + spacing / 2
        // Set fields explicitly (Android RectF unit-test stubs don't run the
        // 4-arg constructor body, leaving the fields at 0).
        return RectF().apply {
            left = x
            top = y
            right = x + cellWidth - spacing
            bottom = y + cellHeight - spacing
        }
    }

    /**
     * A corner of a brick is "covered" if any of the (up to 3) neighbouring cells that
     * share that grid corner has a non-destroyed brick whose body extends to the same
     * cell-corner. With BRICK_SPACING (4 px) << BALL_RADIUS (16 px) the ball can't fit
     * through the gap, so a covered corner is interior to the brick union and must NOT
     * generate a collision (otherwise it acts as a phantom bumper that deflects balls
     * gliding past — the cause of the user-visible "weird bounces near corners").
     */
    private fun isCornerCovered(
        brickRow: Int, brickCol: Int,
        cellCorner: CellCorner,
        board: GameBoard
    ): Boolean {
        val (n1, n2, n3) = when (cellCorner) {
            CellCorner.TL -> Triple(
                Triple(brickRow + 1, brickCol - 1, CellCorner.BR),
                Triple(brickRow + 1, brickCol,     CellCorner.BL),
                Triple(brickRow,     brickCol - 1, CellCorner.TR),
            )
            CellCorner.TR -> Triple(
                Triple(brickRow + 1, brickCol + 1, CellCorner.BL),
                Triple(brickRow + 1, brickCol,     CellCorner.BR),
                Triple(brickRow,     brickCol + 1, CellCorner.TL),
            )
            CellCorner.BL -> Triple(
                Triple(brickRow - 1, brickCol - 1, CellCorner.TR),
                Triple(brickRow - 1, brickCol,     CellCorner.TL),
                Triple(brickRow,     brickCol - 1, CellCorner.BR),
            )
            CellCorner.BR -> Triple(
                Triple(brickRow - 1, brickCol + 1, CellCorner.TL),
                Triple(brickRow - 1, brickCol,     CellCorner.TR),
                Triple(brickRow,     brickCol + 1, CellCorner.BL),
            )
        }
        for ((nr, nc, nCorner) in listOf(n1, n2, n3)) {
            val nb = board.getBrick(nr, nc) ?: continue
            if (nb.isDestroyed) continue
            if (BrickShapeGeometry.shapeIncludesCellCorner(nb.shape, nCorner)) {
                return true
            }
        }
        return false
    }

    /**
     * Advance a ball through one frame using Continuous Collision Detection.
     * Splits the frame into sub-steps no larger than MAX_PHYSICS_SUBSTEP_SECS so
     * that turbo (effective dt = 4×) runs as 4 sub-frames — this keeps multi-brick
     * collision resolution stable and prevents tunneling at high speed.
     */
    override fun advanceBall(ball: Ball, dt: Float, board: GameBoard): CcdResult {
        val maxStep = Constants.MAX_PHYSICS_SUBSTEP_SECS
        var remaining = dt
        val allHits = mutableListOf<BrickHit>()
        var anyDespawned = false
        var anyWallBounce = false
        while (remaining > EPSILON && !anyDespawned) {
            val step = if (remaining > maxStep) maxStep else remaining
            val r = advanceBallStep(ball, step, board)
            allHits.addAll(r.hitBricks)
            if (r.hadWallBounce) anyWallBounce = true
            if (r.despawned) {
                anyDespawned = true
                break
            }
            remaining -= step
        }
        return CcdResult(allHits, despawned = anyDespawned, hadWallBounce = anyWallBounce)
    }

    private fun advanceBallStep(ball: Ball, dt: Float, board: GameBoard): CcdResult {
        val r = Constants.BALL_RADIUS
        var remainingTime = dt
        val hitBricks = mutableListOf<BrickHit>()
        var bounceCount = 0
        var hadWallBounce = false

        while (remainingTime > EPSILON && bounceCount < MAX_BOUNCES) {
            // Safety: correct any out-of-bounds position (nudge overshoot near walls)
            if (ball.x < leftBound + r) {
                ball.x = leftBound + r
                if (ball.vx < 0f) {
                    ball.vx = -ball.vx
                    val (cvx, cvy) = ReflectionCalculator.enforceMinVerticalVelocity(ball.vx, ball.vy)
                    ball.vx = cvx; ball.vy = cvy
                }
            }
            if (ball.x > rightBound - r) {
                ball.x = rightBound - r
                if (ball.vx > 0f) {
                    ball.vx = -ball.vx
                    val (cvx, cvy) = ReflectionCalculator.enforceMinVerticalVelocity(ball.vx, ball.vy)
                    ball.vx = cvx; ball.vy = cvy
                }
            }
            if (ball.y < topBound + r) {
                ball.y = topBound + r
                if (ball.vy < 0f) {
                    ball.vy = -ball.vy
                    val (cvx, cvy) = ReflectionCalculator.enforceMinVerticalVelocity(ball.vx, ball.vy)
                    ball.vx = cvx; ball.vy = cvy
                }
            }
            if (ball.y + r >= bottomBound) {
                return CcdResult(hitBricks, despawned = true, hadWallBounce)
            }

            // Depenetration: push ball out of any overlapping brick geometry
            depenetrate(ball, r, board)

            var bestTime = Float.MAX_VALUE
            val contacts = mutableListOf<Contact>()

            // --- Wall collisions (1D ray casts) ---
            if (ball.vx < 0f) {
                val t = (leftBound + r - ball.x) / ball.vx
                if (t > EPSILON && t <= remainingTime) {
                    bestTime = registerContact(t, bestTime, contacts,
                        Contact(1f, 0f, null, true, false, 0f, 0f))
                }
            }
            if (ball.vx > 0f) {
                val t = (rightBound - r - ball.x) / ball.vx
                if (t > EPSILON && t <= remainingTime) {
                    bestTime = registerContact(t, bestTime, contacts,
                        Contact(-1f, 0f, null, true, false, 0f, 0f))
                }
            }
            if (ball.vy < 0f) {
                val t = (topBound + r - ball.y) / ball.vy
                if (t > EPSILON && t <= remainingTime) {
                    bestTime = registerContact(t, bestTime, contacts,
                        Contact(0f, 1f, null, true, false, 0f, 0f))
                }
            }
            // Baseline (despawn) — track separately, can't combine with normal contacts
            var baselineTime = Float.MAX_VALUE
            if (ball.vy > 0f) {
                val t = (bottomBound - r - ball.y) / ball.vy
                if (t > EPSILON && t <= remainingTime) baselineTime = t
            }

            // --- Brick collisions (swept circle vs edges & corners) ---
            val endX = ball.x + ball.vx * remainingTime
            val endY = ball.y + ball.vy * remainingTime
            val cells = getCellsAlongPath(ball.x, ball.y, endX, endY, r)

            for ((row, col) in cells) {
                val brick = board.getBrick(row, col) ?: continue
                if (brick.isDestroyed) continue
                val rect = getBrickRect(row, col)

                val edges = BrickShapeGeometry.getEdges(brick.shape, rect)
                for (edge in edges) {
                    val t = sweepCircleEdge(ball.x, ball.y, ball.vx, ball.vy, r, edge)
                    if (t > EPSILON && t <= remainingTime && t <= bestTime + TIME_TIE_EPSILON) {
                        bestTime = registerContact(t, bestTime, contacts,
                            Contact(edge.normalX, edge.normalY, brick, false, false, 0f, 0f))
                    }
                }

                val corners = BrickShapeGeometry.getCornerInfos(brick.shape, rect)
                for (cInfo in corners) {
                    // Skip corners that are interior to the brick union (covered by neighbours).
                    val cc = cInfo.cellCorner
                    if (cc != null && isCornerCovered(row, col, cc, board)) continue

                    val t = sweepCirclePoint(ball.x, ball.y, ball.vx, ball.vy, r, cInfo.x, cInfo.y)
                    if (t > EPSILON && t <= remainingTime && t <= bestTime + TIME_TIE_EPSILON) {
                        bestTime = registerContact(t, bestTime, contacts,
                            Contact(0f, 0f, brick, false, true, cInfo.x, cInfo.y))
                    }
                }
            }

            // Baseline beats brick contacts only if it's strictly earlier.
            if (baselineTime < bestTime - TIME_TIE_EPSILON) {
                ball.x += ball.vx * baselineTime
                ball.y += ball.vy * baselineTime
                return CcdResult(hitBricks, despawned = true, hadWallBounce)
            }

            // --- No collision found: move to end position ---
            if (contacts.isEmpty()) {
                ball.x += ball.vx * remainingTime
                ball.y += ball.vy * remainingTime
                remainingTime = 0f
                break
            }

            // --- Move to collision point ---
            ball.x += ball.vx * bestTime
            ball.y += ball.vy * bestTime
            remainingTime -= bestTime

            // Compute combined surface normal from all simultaneous contacts.
            var sumNx = 0f
            var sumNy = 0f
            var anyWall = false
            for (c in contacts) {
                if (c.isWall) anyWall = true
                val (cnx, cny) = if (c.isCorner) {
                    val dx = ball.x - c.cornerX
                    val dy = ball.y - c.cornerY
                    val d = sqrt(dx * dx + dy * dy)
                    if (d > EPSILON) (dx / d) to (dy / d) else 0f to 0f
                } else {
                    c.nx to c.ny
                }
                sumNx += cnx
                sumNy += cny
            }
            val sumLen = sqrt(sumNx * sumNx + sumNy * sumNy)
            val nx: Float
            val ny: Float
            if (sumLen > EPSILON) {
                nx = sumNx / sumLen
                ny = sumNy / sumLen
            } else {
                // Degenerate (opposing normals canceled): reverse the velocity vector
                // as a guaranteed escape direction.
                val vLen = sqrt(ball.vx * ball.vx + ball.vy * ball.vy)
                if (vLen > EPSILON) {
                    nx = -ball.vx / vLen
                    ny = -ball.vy / vLen
                } else {
                    nx = 0f
                    ny = -1f
                }
            }

            if (anyWall) hadWallBounce = true

            val (nvx, nvy) = ReflectionCalculator.reflect(ball.vx, ball.vy, nx, ny)
            val (cvx, cvy) = ReflectionCalculator.enforceMinVerticalVelocity(nvx, nvy)
            ball.vx = cvx
            ball.vy = cvy
            ball.x += nx * NUDGE
            ball.y += ny * NUDGE

            // Record and process every brick hit at this contact (multiple bricks can be
            // hit simultaneously, e.g. at the meeting point of two adjacent bricks).
            val seenBricks = HashSet<Brick>()
            for (c in contacts) {
                val b = c.brick ?: continue
                if (!seenBricks.add(b)) continue
                val rect = getBrickRect(b.row, b.col)
                val color = b.color
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

            bounceCount++
        }

        // If we exited via MAX_BOUNCES with motion remaining, discard remaining motion to
        // avoid tunneling through any brick we hadn't reflected against yet (pathological
        // corner traps). Run a final depenetration so the ball can never end the frame
        // inside a brick.
        depenetrate(ball, r, board)

        // Final safety: ensure ball ends within play area
        if (ball.x < leftBound + r) {
            ball.x = leftBound + r
            if (ball.vx < 0f) {
                ball.vx = -ball.vx
                val (cvx, cvy) = ReflectionCalculator.enforceMinVerticalVelocity(ball.vx, ball.vy)
                ball.vx = cvx; ball.vy = cvy
            }
        }
        if (ball.x > rightBound - r) {
            ball.x = rightBound - r
            if (ball.vx > 0f) {
                ball.vx = -ball.vx
                val (cvx, cvy) = ReflectionCalculator.enforceMinVerticalVelocity(ball.vx, ball.vy)
                ball.vx = cvx; ball.vy = cvy
            }
        }
        if (ball.y < topBound + r) {
            ball.y = topBound + r
            if (ball.vy < 0f) {
                ball.vy = -ball.vy
                val (cvx, cvy) = ReflectionCalculator.enforceMinVerticalVelocity(ball.vx, ball.vy)
                ball.vx = cvx; ball.vy = cvy
            }
        }
        if (ball.y + r >= bottomBound) {
            return CcdResult(hitBricks, despawned = true, hadWallBounce)
        }

        return CcdResult(hitBricks, despawned = false, hadWallBounce)
    }

    /**
     * Add `c` to the set of simultaneous contacts. If `t` strictly precedes the previous
     * best (by more than TIME_TIE_EPSILON), supersedes them. Returns the new bestTime.
     */
    private fun registerContact(
        t: Float, bestTime: Float, contacts: MutableList<Contact>, c: Contact
    ): Float {
        return if (t < bestTime - TIME_TIE_EPSILON) {
            contacts.clear()
            contacts.add(c)
            t
        } else {
            contacts.add(c)
            min(bestTime, t)
        }
    }

    /**
     * Push the ball out of any overlapping brick geometry.
     * Finds the closest point on any nearby brick boundary and pushes
     * the ball along the separation normal until it is no longer overlapping.
     */
    private fun depenetrate(ball: Ball, r: Float, board: GameBoard) {
        for (iter in 0 until MAX_DEPENETRATION_ITERS) {
            var worstPenetration = 0f
            var pushNx = 0f
            var pushNy = 0f

            val col0 = ((ball.x - r - offsetX) / cellWidth).toInt().coerceIn(0, Constants.GRID_COLUMNS - 1)
            val col1 = ((ball.x + r - offsetX) / cellWidth).toInt().coerceIn(0, Constants.GRID_COLUMNS - 1)
            val sRow0 = ((ball.y - r - offsetY) / cellHeight).toInt().coerceIn(0, Constants.GRID_ROWS - 1)
            val sRow1 = ((ball.y + r - offsetY) / cellHeight).toInt().coerceIn(0, Constants.GRID_ROWS - 1)
            val gRow0 = (Constants.GRID_ROWS - 1 - sRow1).coerceIn(0, Constants.GRID_ROWS - 1)
            val gRow1 = (Constants.GRID_ROWS - 1 - sRow0).coerceIn(0, Constants.GRID_ROWS - 1)

            for (row in gRow0..gRow1) {
                for (col in col0..col1) {
                    val brick = board.getBrick(row, col) ?: continue
                    if (brick.isDestroyed) continue
                    val rect = getBrickRect(row, col)

                    val edges = BrickShapeGeometry.getEdges(brick.shape, rect)

                    // First, detect "ball center fully inside brick polygon" — happens when
                    // the ball ends up further than r from every edge (deep penetration).
                    // For a convex polygon, ball is inside iff signed distance to every
                    // edge line is <= 0. Push out via the edge with the LARGEST (least
                    // negative) signed distance (the closest wall).
                    var allInside = true
                    var maxSigned = -Float.MAX_VALUE
                    var maxEdgeNx = 0f
                    var maxEdgeNy = 0f
                    for (edge in edges) {
                        val signed = (ball.x - edge.x1) * edge.normalX +
                                     (ball.y - edge.y1) * edge.normalY
                        if (signed > 0f) { allInside = false; break }
                        if (signed > maxSigned) {
                            maxSigned = signed
                            maxEdgeNx = edge.normalX
                            maxEdgeNy = edge.normalY
                        }
                    }
                    if (allInside && edges.isNotEmpty()) {
                        // Penetration is r minus signed distance (signed is <= 0).
                        val penetration = r - maxSigned
                        if (penetration > worstPenetration) {
                            worstPenetration = penetration
                            pushNx = maxEdgeNx
                            pushNy = maxEdgeNy
                        }
                        continue  // skip the standard edge/corner penetration checks
                    }

                    // Standard case: ball center is outside the polygon. Edge contact only
                    // counts when the perpendicular foot lies on the segment AND the ball
                    // is within r of the edge.
                    for (edge in edges) {
                        val (dist, nx, ny) = closestPointOnEdge(ball.x, ball.y, edge)
                        val penetration = r - dist
                        if (penetration > worstPenetration) {
                            worstPenetration = penetration
                            pushNx = nx
                            pushNy = ny
                        }
                    }

                    val corners = BrickShapeGeometry.getCornerInfos(brick.shape, rect)
                    for (cInfo in corners) {
                        val cc = cInfo.cellCorner
                        if (cc != null && isCornerCovered(row, col, cc, board)) continue
                        val dx = ball.x - cInfo.x
                        val dy = ball.y - cInfo.y
                        val dist = sqrt(dx * dx + dy * dy)
                        val penetration = r - dist
                        if (penetration > worstPenetration && dist > EPSILON) {
                            worstPenetration = penetration
                            pushNx = dx / dist
                            pushNy = dy / dist
                        }
                    }
                }
            }

            if (worstPenetration <= EPSILON) break

            val pushDist = worstPenetration + DEPENETRATION_SLOP
            ball.x += pushNx * pushDist
            ball.y += pushNy * pushDist
        }
    }

    /**
     * Returns (distance, normalX, normalY) from point (px,py) to the closest
     * point on the edge segment. Normal points from edge toward the point.
     */
    private fun closestPointOnEdge(px: Float, py: Float, edge: Edge): Triple<Float, Float, Float> {
        val edgeDx = edge.x2 - edge.x1
        val edgeDy = edge.y2 - edge.y1
        val edgeLenSq = edgeDx * edgeDx + edgeDy * edgeDy
        if (edgeLenSq < EPSILON) {
            val dx = px - edge.x1
            val dy = py - edge.y1
            val d = sqrt(dx * dx + dy * dy)
            return if (d > EPSILON) Triple(d, dx / d, dy / d) else Triple(0f, 0f, 0f)
        }

        val t = ((px - edge.x1) * edgeDx + (py - edge.y1) * edgeDy) / edgeLenSq
        val clamped = t.coerceIn(0f, 1f)
        val closestX = edge.x1 + clamped * edgeDx
        val closestY = edge.y1 + clamped * edgeDy
        val dx = px - closestX
        val dy = py - closestY
        val dist = sqrt(dx * dx + dy * dy)
        return if (dist > EPSILON) {
            Triple(dist, dx / dist, dy / dist)
        } else {
            Triple(0f, edge.normalX, edge.normalY)
        }
    }

    /**
     * Swept circle vs line segment (edge).
     * Returns time of first contact, or Float.MAX_VALUE if no hit.
     */
    private fun sweepCircleEdge(
        px: Float, py: Float, vx: Float, vy: Float,
        radius: Float, edge: Edge
    ): Float {
        val nx = edge.normalX
        val ny = edge.normalY

        val vDotN = vx * nx + vy * ny
        if (vDotN >= 0f) return Float.MAX_VALUE

        val signedDist = (px - edge.x1) * nx + (py - edge.y1) * ny

        val t = (radius - signedDist) / vDotN
        if (t < 0f) return Float.MAX_VALUE

        val contactX = px + vx * t - radius * nx
        val contactY = py + vy * t - radius * ny

        val edgeDx = edge.x2 - edge.x1
        val edgeDy = edge.y2 - edge.y1
        val edgeLenSq = edgeDx * edgeDx + edgeDy * edgeDy
        if (edgeLenSq < EPSILON) return Float.MAX_VALUE

        val s = ((contactX - edge.x1) * edgeDx + (contactY - edge.y1) * edgeDy) / edgeLenSq
        if (s < 0f || s > 1f) return Float.MAX_VALUE

        return t
    }

    /**
     * Swept circle vs point (corner).
     * Returns time when ball center reaches 'radius' from the point, or Float.MAX_VALUE.
     */
    private fun sweepCirclePoint(
        px: Float, py: Float, vx: Float, vy: Float,
        radius: Float, cx: Float, cy: Float
    ): Float {
        val dx = px - cx
        val dy = py - cy
        val a = vx * vx + vy * vy
        val b = 2f * (dx * vx + dy * vy)
        val c = dx * dx + dy * dy - radius * radius

        if (a < EPSILON) return Float.MAX_VALUE

        val discriminant = b * b - 4f * a * c
        if (discriminant < 0f) return Float.MAX_VALUE

        val sqrtD = sqrt(discriminant)
        val t = (-b - sqrtD) / (2f * a)
        return if (t > 0f) t else Float.MAX_VALUE
    }

    /** Get all grid cells that the swept ball path could intersect. */
    private fun getCellsAlongPath(
        startX: Float, startY: Float,
        endX: Float, endY: Float,
        radius: Float
    ): List<Pair<Int, Int>> {
        if (cellWidth <= 0f || cellHeight <= 0f) return emptyList()

        val minX = min(startX, endX) - radius
        val maxX = max(startX, endX) + radius
        val minY = min(startY, endY) - radius
        val maxY = max(startY, endY) + radius

        val minCol = ((minX - offsetX) / cellWidth).toInt().coerceIn(0, Constants.GRID_COLUMNS - 1)
        val maxCol = ((maxX - offsetX) / cellWidth).toInt().coerceIn(0, Constants.GRID_COLUMNS - 1)
        val minScreenRow = ((minY - offsetY) / cellHeight).toInt().coerceIn(0, Constants.GRID_ROWS - 1)
        val maxScreenRow = ((maxY - offsetY) / cellHeight).toInt().coerceIn(0, Constants.GRID_ROWS - 1)
        val minGameRow = (Constants.GRID_ROWS - 1 - maxScreenRow).coerceIn(0, Constants.GRID_ROWS - 1)
        val maxGameRow = (Constants.GRID_ROWS - 1 - minScreenRow).coerceIn(0, Constants.GRID_ROWS - 1)

        val cells = mutableListOf<Pair<Int, Int>>()
        for (row in minGameRow..maxGameRow) {
            for (col in minCol..maxCol) {
                cells.add(row to col)
            }
        }
        return cells
    }
}
