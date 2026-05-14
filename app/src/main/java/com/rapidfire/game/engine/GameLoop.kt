package com.rapidfire.game.engine

import com.rapidfire.game.audio.SoundManager
import com.rapidfire.game.physics.PhysicsEngine
import com.rapidfire.game.util.Constants

class GameLoop(
    private val state: GameState,
    private val collisionDetector: PhysicsEngine,
    private val soundManager: SoundManager?,
    val effects: VisualEffects = VisualEffects()
) {
    private var lastUpdateTime = System.currentTimeMillis()

    fun reset() {
        lastUpdateTime = System.currentTimeMillis()
    }

    fun update() {
        val currentTime = System.currentTimeMillis()
        val deltaMs = (currentTime - lastUpdateTime).coerceAtMost(Constants.FRAME_TIME_MS * Constants.MAX_FRAME_SKIP)
        lastUpdateTime = currentTime

        if (state.isGameOver) return

        val dt = deltaMs / 1000f // delta time in seconds

        // Always tick animations (cannon slide, brick shift) regardless of phase
        state.updateAnimations(dt)
        effects.update(dt)

        when (state.roundPhase) {
            GameState.RoundPhase.AIMING -> {
                // Nothing to update, waiting for player input
            }

            GameState.RoundPhase.FIRING -> {
                // Fire balls at intervals
                val newBall = state.updateFiring(currentTime)
                if (newBall != null) {
                    soundManager?.playFire()
                    effects.spawnMuzzleFlash(newBall.x, newBall.y)
                }
                updateBalls(dt)
            }

            GameState.RoundPhase.ANIMATING -> {
                updateBalls(dt)
            }

            GameState.RoundPhase.ROUND_END -> {
                state.advanceRound()
                if (state.isGameOver) {
                    soundManager?.playGameOver()
                }
            }
        }
    }

    private fun updateBalls(dt: Float) {
        val effectiveDt = if (state.isTurbo) dt * Constants.TURBO_SPEED_MULTIPLIER else dt
        val activeBalls = state.balls.filter { it.active }

        for (ball in activeBalls) {
            if (!ball.active) continue

            val result = collisionDetector.advanceBall(ball, effectiveDt, state.board)
            ball.ageSecs += effectiveDt

            // Process brick hits: scoring, sound, effects
            var playedSound = false
            for (hit in result.hitBricks) {
                if (hit.destroyed) {
                    state.onBrickDestroyed(hit.points)
                    if (!playedSound) { soundManager?.playExplode(); playedSound = true }
                    effects.spawnBrickParticles(hit.centerX, hit.centerY, hit.color)
                    effects.spawnScorePopup(hit.centerX, hit.centerY - 20f, hit.points)
                } else {
                    if (!playedSound) { soundManager?.playBounce(); playedSound = true }
                }
            }
            if (result.hadWallBounce && !playedSound) {
                soundManager?.playBounce()
            }

            // Despawn (or force-despawn balls that have lived too long — failsafe
            // against pathological orbits in high-HP boss rounds)
            if (result.despawned || ball.ageSecs > Constants.MAX_BALL_LIFETIME_SECS) {
                state.onBallDespawn(ball)
            }
        }
    }
}
