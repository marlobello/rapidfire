package com.rapidfire.game.engine

import com.rapidfire.game.audio.SoundManager
import com.rapidfire.game.model.Brick
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

            // Apply direct hits before blasts so every collision in this frame counts.
            var playedSound = false
            var destroyedCount = 0
            var destroyedPoints = 0
            val bombsToDetonate = ArrayDeque<Brick>()
            for (hit in result.hitBricks) {
                if (hit.destroyed) {
                    destroyedCount++
                    destroyedPoints += hit.points
                    if (!playedSound) { soundManager?.playExplode(); playedSound = true }
                    effects.spawnBrickParticles(hit.centerX, hit.centerY, hit.color)
                    effects.spawnScorePopup(hit.centerX, hit.centerY - 20f, hit.points)
                    if (hit.brick.isBomb) bombsToDetonate.addLast(hit.brick)
                } else {
                    if (!playedSound) { soundManager?.playBounce(); playedSound = true }
                }
            }

            val detonatedBombs = HashSet<Pair<Int, Int>>()
            while (bombsToDetonate.isNotEmpty()) {
                val bomb = bombsToDetonate.removeFirst()
                if (!detonatedBombs.add(bomb.row to bomb.col)) continue

                soundManager?.playBombExplosion()
                val bombRect = collisionDetector.getBrickRect(bomb.row, bomb.col)
                effects.spawnBombParticles(bombRect.centerX(), bombRect.centerY())

                for (adjacent in state.board.detonateAdjacent(bomb.row, bomb.col)) {
                    destroyedCount++
                    destroyedPoints += adjacent.originalValue
                    val adjacentRect = collisionDetector.getBrickRect(adjacent.row, adjacent.col)
                    effects.spawnBrickParticles(
                        adjacentRect.centerX(),
                        adjacentRect.centerY(),
                        adjacent.color
                    )
                    effects.spawnScorePopup(
                        adjacentRect.centerX(),
                        adjacentRect.centerY() - 20f,
                        adjacent.originalValue
                    )
                    if (adjacent.isBomb) bombsToDetonate.addLast(adjacent)
                }
            }

            if (destroyedCount > 0) {
                state.onBricksDestroyed(destroyedCount, destroyedPoints)
            }

            if (result.hadWallBounce && !playedSound) {
                soundManager?.playBounce()
            }

            if (result.despawned) {
                state.onBallDespawn(ball)
            }
        }

    }
}
