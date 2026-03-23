package com.rapidfire.game.physics

import kotlin.math.sqrt

object ReflectionCalculator {

    /**
     * Reflect a velocity vector (vx, vy) off a surface with the given outward normal (nx, ny).
     * Uses the formula: v' = v - 2(v·n)n
     * Returns the reflected velocity as a Pair(vx, vy).
     */
    fun reflect(vx: Float, vy: Float, nx: Float, ny: Float): Pair<Float, Float> {
        val dot = vx * nx + vy * ny
        // Only reflect if moving into the surface
        if (dot >= 0f) return vx to vy
        val rvx = vx - 2f * dot * nx
        val rvy = vy - 2f * dot * ny
        return rvx to rvy
    }

    /**
     * Reflect off a corner point. Normal is from the corner center to the ball center.
     */
    fun reflectOffCorner(
        vx: Float, vy: Float,
        ballX: Float, ballY: Float,
        cornerX: Float, cornerY: Float
    ): Pair<Float, Float> {
        val dx = ballX - cornerX
        val dy = ballY - cornerY
        val dist = sqrt(dx * dx + dy * dy)
        if (dist < 0.001f) return vx to vy
        val nx = dx / dist
        val ny = dy / dist
        return reflect(vx, vy, nx, ny)
    }
}
