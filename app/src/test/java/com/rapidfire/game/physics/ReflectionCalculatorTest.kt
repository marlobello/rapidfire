package com.rapidfire.game.physics

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

class ReflectionCalculatorTest {

    private val tolerance = 0.001f

    @Test
    fun `reflect off horizontal surface reverses vy`() {
        // Ball moving down-right, hitting a horizontal surface (normal pointing up)
        val (rvx, rvy) = ReflectionCalculator.reflect(1f, 1f, 0f, -1f)
        assertEquals(1f, rvx, tolerance)
        assertEquals(-1f, rvy, tolerance)
    }

    @Test
    fun `reflect off vertical surface reverses vx`() {
        // Ball moving right, hitting a vertical surface (normal pointing left)
        val (rvx, rvy) = ReflectionCalculator.reflect(1f, 0.5f, -1f, 0f)
        assertEquals(-1f, rvx, tolerance)
        assertEquals(0.5f, rvy, tolerance)
    }

    @Test
    fun `reflect preserves speed`() {
        val vx = 3f; val vy = -4f
        val speedBefore = sqrt(vx * vx + vy * vy)
        val (rvx, rvy) = ReflectionCalculator.reflect(vx, vy, 0f, 1f)
        val speedAfter = sqrt(rvx * rvx + rvy * rvy)
        assertEquals(speedBefore, speedAfter, tolerance)
    }

    @Test
    fun `reflect off 45-degree surface`() {
        // Ball moving straight down, hits 45° surface (normal = (√2/2, -√2/2))
        val n = 1f / sqrt(2f)
        val (rvx, rvy) = ReflectionCalculator.reflect(0f, 1f, n, -n)
        // Reflected to the right: (1, 0)
        assertEquals(1f, rvx, 0.01f)
        assertEquals(0f, rvy, 0.01f)
    }

    @Test
    fun `reflect does nothing when moving away from surface`() {
        // Ball moving away from the surface (dot product >= 0)
        val (rvx, rvy) = ReflectionCalculator.reflect(0f, -1f, 0f, -1f)
        assertEquals(0f, rvx, tolerance)
        assertEquals(-1f, rvy, tolerance)
    }

    // --- Corner reflection tests ---

    @Test
    fun `reflectOffCorner with ball directly above corner reflects downward to upward`() {
        // Ball at (5, 3), corner at (5, 5), ball moving down
        val (rvx, rvy) = ReflectionCalculator.reflectOffCorner(0f, 1f, 5f, 3f, 5f, 5f)
        // Normal from corner to ball is (0, -1), so vy should flip
        assertEquals(0f, rvx, tolerance)
        assertEquals(-1f, rvy, tolerance)
    }

    @Test
    fun `reflectOffCorner with ball at 45 degrees from corner`() {
        // Ball at (4, 4), corner at (5, 5), ball moving toward corner
        val (rvx, rvy) = ReflectionCalculator.reflectOffCorner(1f, 1f, 4f, 4f, 5f, 5f)
        // Normal from corner to ball is (-√2/2, -√2/2)
        // Reflection should reverse both components
        assertEquals(-1f, rvx, tolerance)
        assertEquals(-1f, rvy, tolerance)
    }

    @Test
    fun `reflectOffCorner degenerate case reverses velocity`() {
        // Ball center exactly on corner point
        val (rvx, rvy) = ReflectionCalculator.reflectOffCorner(3f, -4f, 5f, 5f, 5f, 5f)
        assertEquals(-3f, rvx, tolerance)
        assertEquals(4f, rvy, tolerance)
    }

    @Test
    fun `reflectOffCorner preserves speed`() {
        val vx = 3f; val vy = -4f
        val speedBefore = sqrt(vx * vx + vy * vy)
        val (rvx, rvy) = ReflectionCalculator.reflectOffCorner(vx, vy, 10f, 8f, 12f, 10f)
        val speedAfter = sqrt(rvx * rvx + rvy * rvy)
        assertEquals(speedBefore, speedAfter, tolerance)
    }
}
