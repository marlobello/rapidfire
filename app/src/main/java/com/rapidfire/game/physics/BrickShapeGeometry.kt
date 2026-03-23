package com.rapidfire.game.physics

import android.graphics.RectF
import com.rapidfire.game.model.BrickShape
import kotlin.math.sqrt

data class Edge(val x1: Float, val y1: Float, val x2: Float, val y2: Float) {
    val normalX: Float
    val normalY: Float

    init {
        val dx = x2 - x1
        val dy = y2 - y1
        val len = sqrt(dx * dx + dy * dy)
        // Outward-facing normal (perpendicular, pointing right of edge direction)
        normalX = dy / len
        normalY = -dx / len
    }
}

object BrickShapeGeometry {

    /** Get the edges of a brick shape within the given cell rect. Normals point outward. */
    fun getEdges(shape: BrickShape, rect: RectF): List<Edge> {
        val l = rect.left
        val t = rect.top
        val r = rect.right
        val b = rect.bottom

        return when (shape) {
            BrickShape.SQUARE -> listOf(
                Edge(l, t, r, t), // top edge, normal points up (outward)
                Edge(r, t, r, b), // right edge, normal points right
                Edge(r, b, l, b), // bottom edge, normal points down
                Edge(l, b, l, t)  // left edge, normal points left
            )

            BrickShape.TRIANGLE_TL -> listOf(
                // Right angle at top-left. Vertices: TL, TR, BL. Hypotenuse: TR→BL
                Edge(l, t, r, t), // top edge
                Edge(r, t, l, b), // hypotenuse (TR to BL)
                Edge(l, b, l, t)  // left edge
            )

            BrickShape.TRIANGLE_TR -> listOf(
                // Right angle at top-right. Vertices: TL, TR, BR. Hypotenuse: TL→BR
                Edge(l, t, r, t), // top edge
                Edge(r, t, r, b), // right edge
                Edge(r, b, l, t)  // hypotenuse (BR to TL)
            )

            BrickShape.TRIANGLE_BL -> listOf(
                // Right angle at bottom-left. Vertices: TL, BL, BR. Hypotenuse: TL→BR
                Edge(l, t, r, b), // hypotenuse (TL to BR)
                Edge(r, b, l, b), // bottom edge
                Edge(l, b, l, t)  // left edge
            )

            BrickShape.TRIANGLE_BR -> listOf(
                // Right angle at bottom-right. Vertices: TR, BL, BR. Hypotenuse: BL→TR
                Edge(l, b, r, t), // hypotenuse (BL to TR)
                Edge(r, t, r, b), // right edge
                Edge(r, b, l, b)  // bottom edge
            )
        }
    }

    /** Get the corner points of a brick shape within the given cell rect. */
    fun getCorners(shape: BrickShape, rect: RectF): List<Pair<Float, Float>> {
        val l = rect.left
        val t = rect.top
        val r = rect.right
        val b = rect.bottom

        return when (shape) {
            BrickShape.SQUARE -> listOf(l to t, r to t, r to b, l to b)
            BrickShape.TRIANGLE_TL -> listOf(l to t, r to t, l to b)
            BrickShape.TRIANGLE_TR -> listOf(l to t, r to t, r to b)
            BrickShape.TRIANGLE_BL -> listOf(l to t, l to b, r to b)
            BrickShape.TRIANGLE_BR -> listOf(r to t, l to b, r to b)
        }
    }

    /** Check if a point is inside the brick shape */
    fun containsPoint(shape: BrickShape, rect: RectF, px: Float, py: Float): Boolean {
        if (!rect.contains(px, py)) return false

        return when (shape) {
            BrickShape.SQUARE -> true // rect.contains already handles this
            BrickShape.TRIANGLE_TL -> {
                // TL corner is right angle. Hypotenuse from TR to BL.
                // Point must be on left side of hypotenuse line from (right,top) to (left,bottom)
                val dx = rect.left - rect.right
                val dy = rect.bottom - rect.top
                val cross = dx * (py - rect.top) - dy * (px - rect.right)
                cross >= 0
            }
            BrickShape.TRIANGLE_TR -> {
                // TR corner right angle. Hypotenuse from TL to BR.
                val dx = rect.right - rect.left
                val dy = rect.bottom - rect.top
                val cross = dx * (py - rect.top) - dy * (px - rect.left)
                cross >= 0
            }
            BrickShape.TRIANGLE_BL -> {
                // BL corner right angle. Hypotenuse from TL to BR.
                val dx = rect.right - rect.left
                val dy = rect.bottom - rect.top
                val cross = dx * (py - rect.top) - dy * (px - rect.left)
                cross <= 0
            }
            BrickShape.TRIANGLE_BR -> {
                // BR corner right angle. Hypotenuse from BL to TR.
                val dx = rect.right - rect.left
                val dy = rect.top - rect.bottom
                val cross = dx * (py - rect.bottom) - dy * (px - rect.left)
                cross >= 0
            }
        }
    }
}
