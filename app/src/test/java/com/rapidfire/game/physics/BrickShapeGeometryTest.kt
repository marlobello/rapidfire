package com.rapidfire.game.physics

import android.graphics.RectF
import com.rapidfire.game.model.BrickShape
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sqrt

class BrickShapeGeometryTest {

    private val tolerance = 0.001f

    /** Create a RectF with fields set manually (Android stubs don't run constructor bodies). */
    private fun makeRect(l: Float, t: Float, r: Float, b: Float): RectF {
        return RectF().apply { left = l; top = t; right = r; bottom = b }
    }

    private val rect = makeRect(100f, 200f, 200f, 300f)

    // --- Normal direction verification ---

    @Test
    fun `SQUARE edges all have outward-pointing normals`() {
        verifyNormalsPointOutward(BrickShape.SQUARE, rect)
    }

    @Test
    fun `TRIANGLE_TL edges all have outward-pointing normals`() {
        verifyNormalsPointOutward(BrickShape.TRIANGLE_TL, rect)
    }

    @Test
    fun `TRIANGLE_TR edges all have outward-pointing normals`() {
        verifyNormalsPointOutward(BrickShape.TRIANGLE_TR, rect)
    }

    @Test
    fun `TRIANGLE_BL edges all have outward-pointing normals`() {
        verifyNormalsPointOutward(BrickShape.TRIANGLE_BL, rect)
    }

    @Test
    fun `TRIANGLE_BR edges all have outward-pointing normals`() {
        verifyNormalsPointOutward(BrickShape.TRIANGLE_BR, rect)
    }

    @Test
    fun `all normals are unit length`() {
        for (shape in BrickShape.entries) {
            val edges = BrickShapeGeometry.getEdges(shape, rect)
            for (edge in edges) {
                val len = sqrt(edge.normalX * edge.normalX + edge.normalY * edge.normalY)
                assertEquals("Normal for $shape edge should be unit length", 1f, len, tolerance)
            }
        }
    }

    // --- Corner count verification ---

    @Test
    fun `SQUARE has 4 corners`() {
        assertEquals(4, BrickShapeGeometry.getCorners(BrickShape.SQUARE, rect).size)
    }

    @Test
    fun `all triangles have 3 corners`() {
        for (shape in listOf(BrickShape.TRIANGLE_TL, BrickShape.TRIANGLE_TR, BrickShape.TRIANGLE_BL, BrickShape.TRIANGLE_BR)) {
            assertEquals("$shape should have 3 corners", 3, BrickShapeGeometry.getCorners(shape, rect).size)
        }
    }

    // --- Edge geometry verification ---

    @Test
    fun `SQUARE edges form a closed rectangle`() {
        val edges = BrickShapeGeometry.getEdges(BrickShape.SQUARE, rect)
        assertEquals(4, edges.size)
        // Each edge end should connect to the next edge start
        for (i in edges.indices) {
            val next = edges[(i + 1) % edges.size]
            assertEquals("Edge $i end X should match edge ${(i+1)%edges.size} start X",
                edges[i].x2, next.x1, tolerance)
            assertEquals("Edge $i end Y should match edge ${(i+1)%edges.size} start Y",
                edges[i].y2, next.y1, tolerance)
        }
    }

    @Test
    fun `triangle edges form closed shapes`() {
        for (shape in listOf(BrickShape.TRIANGLE_TL, BrickShape.TRIANGLE_TR, BrickShape.TRIANGLE_BL, BrickShape.TRIANGLE_BR)) {
            val edges = BrickShapeGeometry.getEdges(shape, rect)
            assertEquals("$shape should have 3 edges", 3, edges.size)
            for (i in edges.indices) {
                val next = edges[(i + 1) % edges.size]
                assertEquals("$shape: Edge $i→${(i+1)%3} X mismatch",
                    edges[i].x2, next.x1, tolerance)
                assertEquals("$shape: Edge $i→${(i+1)%3} Y mismatch",
                    edges[i].y2, next.y1, tolerance)
            }
        }
    }

    /**
     * Verifies that every edge normal points away from the shape's centroid.
     */
    private fun verifyNormalsPointOutward(shape: BrickShape, rect: RectF) {
        val edges = BrickShapeGeometry.getEdges(shape, rect)
        val corners = BrickShapeGeometry.getCorners(shape, rect)

        val cx = corners.map { it.first }.average().toFloat()
        val cy = corners.map { it.second }.average().toFloat()

        for (edge in edges) {
            val mx = (edge.x1 + edge.x2) / 2f
            val my = (edge.y1 + edge.y2) / 2f
            val toCenterX = mx - cx
            val toCenterY = my - cy
            val dot = edge.normalX * toCenterX + edge.normalY * toCenterY
            assertTrue(
                "$shape: edge (${edge.x1},${edge.y1})→(${edge.x2},${edge.y2}) " +
                        "normal (${edge.normalX},${edge.normalY}) points inward (dot=$dot)",
                dot > 0f
            )
        }
    }
}
