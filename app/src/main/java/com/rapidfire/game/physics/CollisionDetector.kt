package com.rapidfire.game.physics

import android.graphics.RectF
import com.rapidfire.game.model.Ball
import com.rapidfire.game.model.Brick
import com.rapidfire.game.util.Constants
import kotlin.math.abs
import kotlin.math.sqrt

data class CollisionResult(
    val hit: Boolean,
    val newVx: Float,
    val newVy: Float,
    val brick: Brick? = null
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
        // Row 0 is at the bottom, so we invert: screen y decreases as row increases
        val y = offsetY + (Constants.GRID_ROWS - 1 - row) * cellHeight + spacing / 2
        return RectF(x, y, x + cellWidth - spacing, y + cellHeight - spacing)
    }

    /** Check ball collision with walls. Returns reflected velocity if hit. */
    fun checkWallCollision(ball: Ball): CollisionResult {
        val r = Constants.BALL_RADIUS
        var vx = ball.vx
        var vy = ball.vy
        var hit = false

        // Left wall (play area left edge)
        if (ball.x - r <= leftBound && vx < 0f) {
            vx = -vx
            ball.x = leftBound + r
            hit = true
        }
        // Right wall (play area right edge)
        if (ball.x + r >= rightBound && vx > 0f) {
            vx = -vx
            ball.x = rightBound - r
            hit = true
        }
        // Top wall (play area top edge)
        if (ball.y - r <= topBound && vy < 0f) {
            vy = -vy
            ball.y = topBound + r
            hit = true
        }

        return CollisionResult(hit, vx, vy)
    }

    /** Check if ball has fallen below the baseline (despawn zone). */
    fun isBelowBaseline(ball: Ball): Boolean {
        return ball.y + Constants.BALL_RADIUS >= bottomBound
    }

    /**
     * Return the (row, col) pairs of grid cells the ball could overlap.
     * Only bricks in these cells need collision checking.
     */
    fun getNearbyGridCells(ball: Ball): List<Pair<Int, Int>> {
        if (cellWidth <= 0f || cellHeight <= 0f) return emptyList()
        val r = Constants.BALL_RADIUS
        // Convert ball screen position to grid coordinates
        val minCol = ((ball.x - r - offsetX) / cellWidth).toInt().coerceIn(0, Constants.GRID_COLUMNS - 1)
        val maxCol = ((ball.x + r - offsetX) / cellWidth).toInt().coerceIn(0, Constants.GRID_COLUMNS - 1)
        // Y axis is inverted: row 0 is at the bottom, screen Y increases downward
        val minScreenRow = ((ball.y - r - offsetY) / cellHeight).toInt().coerceIn(0, Constants.GRID_ROWS - 1)
        val maxScreenRow = ((ball.y + r - offsetY) / cellHeight).toInt().coerceIn(0, Constants.GRID_ROWS - 1)
        // Convert screen rows to game rows (inverted)
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

    /** Check ball collision with a single brick. Returns collision result. */
    fun checkBrickCollision(ball: Ball, brick: Brick): CollisionResult {
        val rect = getBrickRect(brick.row, brick.col)
        val r = Constants.BALL_RADIUS
        val cornerR = Constants.CORNER_RADIUS

        // Quick broad-phase: expanded rect check
        val expandedRect = RectF(rect.left - r, rect.top - r, rect.right + r, rect.bottom + r)
        if (!expandedRect.contains(ball.x, ball.y)) {
            return CollisionResult(false, ball.vx, ball.vy)
        }

        // Check corner collisions first (corners have small radius)
        val corners = BrickShapeGeometry.getCorners(brick.shape, rect)
        for ((cx, cy) in corners) {
            val dx = ball.x - cx
            val dy = ball.y - cy
            val dist = sqrt(dx * dx + dy * dy)
            if (dist <= r + cornerR) {
                val (nvx, nvy) = ReflectionCalculator.reflectOffCorner(
                    ball.vx, ball.vy, ball.x, ball.y, cx, cy
                )
                return CollisionResult(true, nvx, nvy, brick)
            }
        }

        // Check edge collisions
        val edges = BrickShapeGeometry.getEdges(brick.shape, rect)
        for (edge in edges) {
            val dist = pointToSegmentDistance(ball.x, ball.y, edge.x1, edge.y1, edge.x2, edge.y2)
            if (dist <= r) {
                val (nvx, nvy) = ReflectionCalculator.reflect(
                    ball.vx, ball.vy, edge.normalX, edge.normalY
                )
                return CollisionResult(true, nvx, nvy, brick)
            }
        }

        // Check if ball center is inside the brick shape (tunneling protection)
        if (BrickShapeGeometry.containsPoint(brick.shape, rect, ball.x, ball.y)) {
            // Find closest edge and reflect
            var minDist = Float.MAX_VALUE
            var bestEdge = edges[0]
            for (edge in edges) {
                val dist = pointToSegmentDistance(ball.x, ball.y, edge.x1, edge.y1, edge.x2, edge.y2)
                if (dist < minDist) {
                    minDist = dist
                    bestEdge = edge
                }
            }
            val (nvx, nvy) = ReflectionCalculator.reflect(
                ball.vx, ball.vy, bestEdge.normalX, bestEdge.normalY
            )
            // Push ball out of the brick
            ball.x += bestEdge.normalX * (r + 1f)
            ball.y += bestEdge.normalY * (r + 1f)
            return CollisionResult(true, nvx, nvy, brick)
        }

        return CollisionResult(false, ball.vx, ball.vy)
    }

    private fun pointToSegmentDistance(
        px: Float, py: Float,
        x1: Float, y1: Float,
        x2: Float, y2: Float
    ): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        val lengthSq = dx * dx + dy * dy
        if (lengthSq < 0.001f) {
            val ddx = px - x1
            val ddy = py - y1
            return sqrt(ddx * ddx + ddy * ddy)
        }
        var t = ((px - x1) * dx + (py - y1) * dy) / lengthSq
        t = t.coerceIn(0f, 1f)
        val closestX = x1 + t * dx
        val closestY = y1 + t * dy
        val ddx = px - closestX
        val ddy = py - closestY
        return sqrt(ddx * ddx + ddy * ddy)
    }
}
