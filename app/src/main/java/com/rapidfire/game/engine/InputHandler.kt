package com.rapidfire.game.engine

import android.view.MotionEvent
import com.rapidfire.game.model.Cannon
import com.rapidfire.game.util.Constants
import kotlin.math.atan2

class InputHandler(
    private val cannon: Cannon,
    private val onFireCallback: () -> Unit
) {
    var aimX: Float = 0f
        private set
    var aimY: Float = 0f
        private set

    private var cannonScreenY: Float = 0f

    fun setCannonScreenY(y: Float) {
        cannonScreenY = y
    }

    fun handleTouch(event: MotionEvent, gameState: GameState): Boolean {
        if (gameState.roundPhase != GameState.RoundPhase.AIMING) return false

        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                cannon.isAiming = true
                aimX = event.x
                aimY = event.y

                // Calculate angle from cannon to touch point
                val dx = event.x - cannon.x
                val dy = cannonScreenY - event.y // inverted because screen Y goes down

                var angleDeg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()

                // Clamp to valid aiming range (10° to 170° from right horizon)
                angleDeg = angleDeg.coerceIn(Constants.MIN_AIM_ANGLE_DEG, Constants.MAX_AIM_ANGLE_DEG)

                cannon.aimAngle = angleDeg
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (cannon.isAiming) {
                    onFireCallback()
                }
                cannon.isAiming = false
                return true
            }
        }
        return false
    }
}
