package com.rapidfire.game.physics

import android.graphics.RectF
import com.rapidfire.game.model.Ball
import com.rapidfire.game.model.GameBoard

/**
 * Common interface that any physics implementation must provide.
 * Two implementations exist:
 *   - [CollisionDetector]   : v1, Continuous Collision Detection (original engine).
 *   - [CollisionDetectorV2] : v2, Substep + MTV (clean rewrite, currently active).
 *
 * The active implementation is selected by [PhysicsEngineFactory.create]. The selection
 * is an internal compile-time choice and is not exposed to the user.
 */
interface PhysicsEngine {
    fun updateDimensions(
        leftBound: Float, rightBound: Float,
        topBound: Float, bottomBound: Float,
        cellWidth: Float, cellHeight: Float,
        offsetX: Float, offsetY: Float
    )

    fun getBrickRect(row: Int, col: Int): RectF

    fun advanceBall(ball: Ball, dt: Float, board: GameBoard): CcdResult
}
