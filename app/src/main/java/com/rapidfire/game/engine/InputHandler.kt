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
    private var playAreaLeft: Float = 0f
    private var playAreaRight: Float = 0f
    private var playAreaTop: Float = 0f

    fun setPlayAreaBounds(left: Float, right: Float, top: Float, cannonY: Float) {
        playAreaLeft = left
        playAreaRight = right
        playAreaTop = top
        cannonScreenY = cannonY
    }

    private fun isInsidePlayArea(x: Float, y: Float): Boolean {
        return x in playAreaLeft..playAreaRight && y in playAreaTop..cannonScreenY
    }

    fun handleTouch(event: MotionEvent, gameState: GameState): Boolean {
        if (gameState.roundPhase != GameState.RoundPhase.AIMING) return false

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (!isInsidePlayArea(event.x, event.y)) return false
                cannon.isAiming = true
                updateAim(event)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!cannon.isAiming) return false
                updateAim(event)
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

    private fun updateAim(event: MotionEvent) {
        aimX = event.x
        aimY = event.y

        val dx = event.x - cannon.x
        val dy = cannonScreenY - event.y

        var angleDeg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        angleDeg = angleDeg.coerceIn(Constants.MIN_AIM_ANGLE_DEG, Constants.MAX_AIM_ANGLE_DEG)

        cannon.aimAngle = angleDeg
    }
}
