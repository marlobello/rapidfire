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

class CollisionDetector(
    private var leftBound: Float = 0f,
    private var rightBound: Float = 0f,
    private var topBound: Float = 0f,
    private var bottomBound: Float = 0f,
    private var cellWidth: Float = 0f,
    private var cellHeight: Float = 0f,
    private var offsetX: Float = 0f,
    private var offsetY: Float = 0f
) {
    companion object {
        private const val EPSILON = 0.0001f
        private const val NUDGE = 0.01f
        private const val MAX_BOUNCES = 10
    }

    fun updateDimensions(
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
    fun getBrickRect(row: Int, col: Int): RectF {
        val spacing = Constants.BRICK_SPACING
        val x = offsetX + col * cellWidth + spacing / 2
        val y = offsetY + (Constants.GRID_ROWS - 1 - row) * cellHeight + spacing / 2
        return RectF(x, y, x + cellWidth - spacing, y + cellHeight - spacing)
    }

    /**
     * Advance a ball through one frame using Continuous Collision Detection.
     * Finds the exact first collision along the ball's path, reflects, and repeats.
     */
    fun advanceBall(ball: Ball, dt: Float, board: GameBoard): CcdResult {
        val r = Constants.BALL_RADIUS
        val cornerR = Constants.CORNER_RADIUS
        var remainingTime = dt
        val hitBricks = mutableListOf<BrickHit>()
        var bounceCount = 0
        var hadWallBounce = false

        while (remainingTime > EPSILON && bounceCount < MAX_BOUNCES) {
            // Safety: correct any out-of-bounds position (nudge overshoot near walls)
            if (ball.x < leftBound + r) { ball.x = leftBound + r; if (ball.vx < 0f) ball.vx = -ball.vx }
            if (ball.x > rightBound - r) { ball.x = rightBound - r; if (ball.vx > 0f) ball.vx = -ball.vx }
            if (ball.y < topBound + r) { ball.y = topBound + r; if (ball.vy < 0f) ball.vy = -ball.vy }

            var bestTime = Float.MAX_VALUE
            var bestNx = 0f
            var bestNy = 0f
            var bestBrick: Brick? = null
            var bestIsBaseline = false
            var bestIsCorner = false
            var bestCornerX = 0f
            var bestCornerY = 0f
            var bestIsWall = false

            // --- Wall collisions (1D ray casts) ---
            // Left wall
            if (ball.vx < 0f) {
                val t = (leftBound + r - ball.x) / ball.vx
                if (t > EPSILON && t < bestTime && t <= remainingTime) {
                    bestTime = t; bestNx = 1f; bestNy = 0f
                    bestBrick = null; bestIsBaseline = false; bestIsCorner = false; bestIsWall = true
                }
            }
            // Right wall
            if (ball.vx > 0f) {
                val t = (rightBound - r - ball.x) / ball.vx
                if (t > EPSILON && t < bestTime && t <= remainingTime) {
                    bestTime = t; bestNx = -1f; bestNy = 0f
                    bestBrick = null; bestIsBaseline = false; bestIsCorner = false; bestIsWall = true
                }
            }
            // Top wall
            if (ball.vy < 0f) {
                val t = (topBound + r - ball.y) / ball.vy
                if (t > EPSILON && t < bestTime && t <= remainingTime) {
                    bestTime = t; bestNx = 0f; bestNy = 1f
                    bestBrick = null; bestIsBaseline = false; bestIsCorner = false; bestIsWall = true
                }
            }
            // Baseline (despawn)
            if (ball.vy > 0f) {
                val t = (bottomBound - r - ball.y) / ball.vy
                if (t > EPSILON && t < bestTime && t <= remainingTime) {
                    bestTime = t; bestIsBaseline = true
                    bestBrick = null; bestIsCorner = false; bestIsWall = false
                }
            }

            // --- Brick collisions (swept circle vs edges & corners) ---
            val endX = ball.x + ball.vx * remainingTime
            val endY = ball.y + ball.vy * remainingTime
            val cells = getCellsAlongPath(ball.x, ball.y, endX, endY, r + cornerR)

            for ((row, col) in cells) {
                val brick = board.getBrick(row, col) ?: continue
                if (brick.isDestroyed) continue
                val rect = getBrickRect(row, col)

                // Edges
                val edges = BrickShapeGeometry.getEdges(brick.shape, rect)
                for (edge in edges) {
                    val t = sweepCircleEdge(ball.x, ball.y, ball.vx, ball.vy, r, edge)
                    if (t > EPSILON && t < bestTime && t <= remainingTime) {
                        bestTime = t; bestNx = edge.normalX; bestNy = edge.normalY
                        bestBrick = brick; bestIsBaseline = false; bestIsCorner = false; bestIsWall = false
                    }
                }

                // Corners
                val corners = BrickShapeGeometry.getCorners(brick.shape, rect)
                for ((cx, cy) in corners) {
                    val t = sweepCirclePoint(ball.x, ball.y, ball.vx, ball.vy, r + cornerR, cx, cy)
                    if (t > EPSILON && t < bestTime && t <= remainingTime) {
                        bestTime = t; bestBrick = brick; bestIsBaseline = false
                        bestIsCorner = true; bestCornerX = cx; bestCornerY = cy; bestIsWall = false
                    }
                }
            }

            // --- No collision found: move to end position ---
            if (bestTime == Float.MAX_VALUE) {
                ball.x += ball.vx * remainingTime
                ball.y += ball.vy * remainingTime
                break
            }

            // --- Move to collision point ---
            ball.x += ball.vx * bestTime
            ball.y += ball.vy * bestTime
            remainingTime -= bestTime

            // Despawn
            if (bestIsBaseline) {
                return CcdResult(hitBricks, despawned = true, hadWallBounce)
            }

            // Track wall bounces for sound
            if (bestIsWall) hadWallBounce = true

            // Reflect and nudge away from surface
            if (bestIsCorner) {
                val (nvx, nvy) = ReflectionCalculator.reflectOffCorner(
                    ball.vx, ball.vy, ball.x, ball.y, bestCornerX, bestCornerY
                )
                ball.vx = nvx; ball.vy = nvy
                val dx = ball.x - bestCornerX
                val dy = ball.y - bestCornerY
                val dist = sqrt(dx * dx + dy * dy)
                if (dist > EPSILON) {
                    ball.x += (dx / dist) * NUDGE
                    ball.y += (dy / dist) * NUDGE
                }
            } else {
                val (nvx, nvy) = ReflectionCalculator.reflect(
                    ball.vx, ball.vy, bestNx, bestNy
                )
                ball.vx = nvx; ball.vy = nvy
                ball.x += bestNx * NUDGE
                ball.y += bestNy * NUDGE
            }

            // Record and process brick hit
            if (bestBrick != null) {
                val rect = getBrickRect(bestBrick.row, bestBrick.col)
                val color = bestBrick.color
                bestBrick.hit()
                val destroyed = bestBrick.isDestroyed
                hitBricks.add(BrickHit(
                    brick = bestBrick,
                    destroyed = destroyed,
                    points = bestBrick.originalValue,
                    centerX = rect.centerX(),
                    centerY = rect.centerY(),
                    color = color
                ))
                if (destroyed) {
                    board.removeBrick(bestBrick.row, bestBrick.col)
                }
            }

            bounceCount++
        }

        // Final safety: ensure ball ends within play area
        if (ball.x < leftBound + r) { ball.x = leftBound + r; if (ball.vx < 0f) ball.vx = -ball.vx }
        if (ball.x > rightBound - r) { ball.x = rightBound - r; if (ball.vx > 0f) ball.vx = -ball.vx }
        if (ball.y < topBound + r) { ball.y = topBound + r; if (ball.vy < 0f) ball.vy = -ball.vy }

        return CcdResult(hitBricks, despawned = false, hadWallBounce)
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

        // Ball must be moving toward the edge
        val vDotN = vx * nx + vy * ny
        if (vDotN >= 0f) return Float.MAX_VALUE

        // Signed distance from ball center to the edge line
        val signedDist = (px - edge.x1) * nx + (py - edge.y1) * ny

        // Time for ball center to reach distance 'radius' from the edge line
        val t = (radius - signedDist) / vDotN
        if (t < 0f) return Float.MAX_VALUE

        // Contact point on the original edge line
        val contactX = px + vx * t - radius * nx
        val contactY = py + vy * t - radius * ny

        // Check if contact point is within segment bounds
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
        // Solve |P + t*V - C|^2 = radius^2
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