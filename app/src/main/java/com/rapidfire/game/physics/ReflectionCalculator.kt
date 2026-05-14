package com.rapidfire.game.physics

import com.rapidfire.game.util.Constants
import kotlin.math.abs
import kotlin.math.sign
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
     * If the ball center is too close to the corner, falls back to reversing velocity.
     */
    fun reflectOffCorner(
        vx: Float, vy: Float,
        ballX: Float, ballY: Float,
        cornerX: Float, cornerY: Float
    ): Pair<Float, Float> {
        val dx = ballX - cornerX
        val dy = ballY - cornerY
        val dist = sqrt(dx * dx + dy * dy)
        if (dist < 0.001f) {
            // Degenerate case: ball center is on the corner point.
            // Reverse the velocity to push the ball away reliably.
            return -vx to -vy
        }
        val nx = dx / dist
        val ny = dy / dist
        return reflect(vx, vy, nx, ny)
    }

    /**
     * Enforce a minimum vertical-velocity ratio (|vy|/|v|).
     * Preserves total speed and the SIGNS of vx and vy, but rotates the velocity
     * vector toward vertical when it falls below the threshold. Prevents the ball
     * from converging onto a near-horizontal trajectory that looks like "rolling"
     * and can produce stable left/right wall orbits that never reach the baseline.
     * If vy is exactly 0, nudges it downward (positive screen-y) so balls always
     * trend back toward the despawn line.
     */
    fun enforceMinVerticalVelocity(vx: Float, vy: Float): Pair<Float, Float> {
        val speed = sqrt(vx * vx + vy * vy)
        if (speed < 0.0001f) return vx to vy
        val ratio = abs(vy) / speed
        if (ratio >= Constants.MIN_VERTICAL_VELOCITY_RATIO) return vx to vy
        val ySign = if (vy == 0f) 1f else sign(vy)
        val xSign = if (vx == 0f) 1f else sign(vx)
        val newVy = ySign * Constants.MIN_VERTICAL_VELOCITY_RATIO * speed
        val newVx = xSign * sqrt(speed * speed - newVy * newVy)
        return newVx to newVy
    }
}
