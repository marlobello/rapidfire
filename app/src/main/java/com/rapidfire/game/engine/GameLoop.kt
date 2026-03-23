package com.rapidfire.game.engine

import com.rapidfire.game.audio.SoundManager
import com.rapidfire.game.physics.CollisionDetector
import com.rapidfire.game.util.Constants

class GameLoop(
    private val state: GameState,
    private val collisionDetector: CollisionDetector,
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

        // Sub-step: break large movements into smaller increments so balls
        // can't skip through gaps between bricks. Max step ≈ ball radius.
        val maxStepDist = Constants.BALL_RADIUS
        val maxSpeed = Constants.BALL_SPEED * (if (state.isTurbo) Constants.TURBO_SPEED_MULTIPLIER else 1f)
        val maxStepTime = maxStepDist / maxSpeed
        val subSteps = (effectiveDt / maxStepTime).toInt().coerceIn(1, 16)
        val subDt = effectiveDt / subSteps

        for (ball in activeBalls) {
            for (step in 0 until subSteps) {
                if (!ball.active) break

                // Advance hit cooldown
                if (ball.stepsSinceHit < Int.MAX_VALUE) ball.stepsSinceHit++

                // Move ball
                ball.x += ball.vx * subDt
                ball.y += ball.vy * subDt

                // Check wall collisions
                val wallResult = collisionDetector.checkWallCollision(ball)
                if (wallResult.hit) {
                    ball.vx = wallResult.newVx
                    ball.vy = wallResult.newVy
                    if (step == 0) soundManager?.playBounce()
                }

                // Spatial lookup: only check bricks in nearby grid cells
                val nearbyCells = collisionDetector.getNearbyGridCells(ball)
                for ((row, col) in nearbyCells) {
                    // Skip the brick we just hit (2-step cooldown prevents double-hit)
                    if (row == ball.lastHitRow && col == ball.lastHitCol && ball.stepsSinceHit < 3) {
                        continue
                    }

                    val brick = state.board.getBrick(row, col) ?: continue
                    if (brick.isDestroyed) continue
                    val result = collisionDetector.checkBrickCollision(ball, brick)
                    if (result.hit) {
                        ball.vx = result.newVx
                        ball.vy = result.newVy
                        ball.lastHitRow = row
                        ball.lastHitCol = col
                        ball.stepsSinceHit = 0

                        brick.hit()
                        if (brick.isDestroyed) {
                            val brickRect = collisionDetector.getBrickRect(brick.row, brick.col)
                            val brickCx = brickRect.centerX()
                            val brickCy = brickRect.centerY()
                            val brickPoints = brick.originalValue

                            state.board.removeBrick(brick.row, brick.col)
                            state.onBrickDestroyed(brickPoints)
                            if (step == 0) soundManager?.playExplode()

                            effects.spawnBrickParticles(brickCx, brickCy, brick.color)
                            effects.spawnScorePopup(brickCx, brickCy - 20f, brickPoints)
                        } else {
                            if (step == 0) soundManager?.playBounce()
                        }
                        break // one brick per sub-step
                    }
                }

                // Check despawn
                if (collisionDetector.isBelowBaseline(ball)) {
                    state.onBallDespawn(ball)
                }
            }
        }
    }
}
