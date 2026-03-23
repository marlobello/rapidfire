package com.rapidfire.game.engine

import com.rapidfire.game.generation.RowGenerator
import com.rapidfire.game.model.Ball
import com.rapidfire.game.model.Brick
import com.rapidfire.game.model.Cannon
import com.rapidfire.game.model.GameBoard
import com.rapidfire.game.util.Constants
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sign
import kotlin.math.sin

class GameState {
    val board = GameBoard()
    val cannon = Cannon(x = 0f) // will be positioned after view size is known
    val balls = mutableListOf<Ball>()
    val rowGenerator = RowGenerator()

    // Screen-space bounds set by GameView after dimensions are calculated
    var cannonScreenY = 0f
    var playAreaLeft = 0f
    var playAreaRight = 0f

    // Power-up state
    var mulligans = 0
        private set
    var isTurbo = false
        private set

    // Round snapshot for mulligan undo
    private var snapshotBricks: List<Brick> = emptyList()
    private var snapshotCannonX = 0f
    private var snapshotScore = 0

    // Animation state
    var brickShiftOffset = 0f   // 0..1 — fraction of cellHeight to shift bricks up (1 = old position)
    var cannonTargetX = 0f
        private set
    var isCannonSliding = false
        private set

    // Board-clear fanfare
    var boardClearTimer = 0f    // counts down from BOARD_CLEAR_DISPLAY_SECS → 0
        private set

    /** Set true when a mulligan is earned; Renderer consumes and resets it. */
    var mulliganJustEarned = false

    var round = 0
        private set
    var score = 0
        private set
    var isGameOver = false
        private set
    var roundPhase = RoundPhase.AIMING
        private set

    // Session stats
    var bricksDestroyed = 0
        private set
    var boardClears = 0
        private set
    var mulligansUsed = 0
        private set
    var shotsFired = 0
        private set

    // Firing state
    private var ballsToFire = 0
    private var lastFireTime = 0L
    private var firstDespawnX: Float? = null

    enum class RoundPhase {
        AIMING,
        FIRING,     // balls are being launched one by one
        ANIMATING,  // all balls launched, waiting for them to finish
        ROUND_END   // all balls despawned, ready for next round
    }

    fun initNewGame() {
        board.clear()
        balls.clear()
        round = 0
        score = 0
        mulligans = 0
        isTurbo = false
        isGameOver = false
        boardClearTimer = 0f
        mulliganJustEarned = false
        bricksDestroyed = 0
        boardClears = 0
        mulligansUsed = 0
        shotsFired = 0
        roundPhase = RoundPhase.AIMING
        cannon.x = (playAreaLeft + playAreaRight) / 2f
        cannon.ammo = 1
        cannon.aimAngle = 90f
        cannon.isAiming = false
        cannon.isFiring = false
        firstDespawnX = null
        startNewRound()
        brickShiftOffset = 0f   // skip animation for the very first round
        isCannonSliding = false
    }

    fun startNewRound() {
        round++

        cannon.ammo = round
        cannon.isAiming = false
        cannon.isFiring = false
        firstDespawnX = null
        isTurbo = false

        // Shift existing bricks down
        if (round > 1) {
            val gameOver = board.shiftDown()
            if (gameOver) {
                isGameOver = true
                return
            }
        }

        // Animate new row sliding in (and existing bricks shifting down)
        brickShiftOffset = 1f

        // Generate and place new row
        val newBricks = rowGenerator.generateRow(round)
        board.placeNewRow(newBricks)

        // Save snapshot for mulligan (after shift + new bricks placed)
        saveRoundSnapshot()

        roundPhase = RoundPhase.AIMING
    }

    fun startFiring() {
        if (roundPhase != RoundPhase.AIMING) return
        roundPhase = RoundPhase.FIRING
        cannon.isFiring = true
        cannon.isAiming = false
        ballsToFire = cannon.ammo
        lastFireTime = 0L
    }

    fun updateFiring(currentTimeMs: Long): Ball? {
        if (roundPhase != RoundPhase.FIRING || ballsToFire <= 0) {
            if (ballsToFire <= 0 && roundPhase == RoundPhase.FIRING) {
                roundPhase = RoundPhase.ANIMATING
            }
            return null
        }

        if (currentTimeMs - lastFireTime >= turboFireDelay()) {
            lastFireTime = currentTimeMs
            ballsToFire--
            shotsFired++
            cannon.ammo = ballsToFire // update visible counter as balls launch

            val angleRad = Math.toRadians(cannon.aimAngle.toDouble())
            val vx = (cos(angleRad) * Constants.BALL_SPEED).toFloat()
            val vy = (-sin(angleRad) * Constants.BALL_SPEED).toFloat() // negative because screen Y is inverted

            // Spawn ball at the barrel tip, not cannon center
            val spawnX = cannon.x + (cos(angleRad) * Constants.BARREL_LENGTH).toFloat()
            val spawnY = cannonScreenY - (sin(angleRad) * Constants.BARREL_LENGTH).toFloat()

            val ball = Ball(
                x = spawnX,
                y = spawnY,
                vx = vx,
                vy = vy
            )
            balls.add(ball)
            return ball
        }
        return null
    }

    fun onBallDespawn(ball: Ball) {
        ball.active = false
        if (firstDespawnX == null) {
            firstDespawnX = ball.x
        }

        // Start cannon slide as soon as first ball lands AND all balls have been fired
        if (!isCannonSliding && firstDespawnX != null &&
            roundPhase == RoundPhase.ANIMATING) {
            firstDespawnX?.let {
                cannonTargetX = it.coerceIn(
                    playAreaLeft + Constants.CANNON_WIDTH / 2f,
                    playAreaRight - Constants.CANNON_WIDTH / 2f
                )
                isCannonSliding = cannonTargetX != cannon.x
            }
        }

        // Check if all balls are done
        if (balls.none { it.active }) {
            roundPhase = RoundPhase.ROUND_END
            cannon.isFiring = false
            score += round
        }
    }

    fun onBrickDestroyed(points: Int) {
        score += points
        bricksDestroyed++
        // Board clear: bonus points + earn a mulligan
        if (board.getAllBricks().isEmpty()) {
            score += Constants.BOARD_CLEAR_BONUS
            boardClearTimer = Constants.BOARD_CLEAR_DISPLAY_SECS
            boardClears++
            mulligans++
            mulliganJustEarned = true
        }
    }

    fun advanceRound() {
        if (roundPhase == RoundPhase.ROUND_END && !isCannonSliding) {
            balls.clear()
            startNewRound()
        }
    }

    // --- Power-up methods ---

    /** Recall all balls immediately, ending the round. Cannon stays in place. */
    fun recall() {
        if (roundPhase != RoundPhase.FIRING && roundPhase != RoundPhase.ANIMATING) return
        for (ball in balls) { ball.active = false }
        ballsToFire = 0
        roundPhase = RoundPhase.ROUND_END
        cannon.isFiring = false
        isCannonSliding = false  // cannon stays put on recall
        isTurbo = false
        score += round
    }

    /** Activate turbo mode — balls move at 4× speed for the rest of this round. */
    fun activateTurbo() {
        if (roundPhase != RoundPhase.FIRING && roundPhase != RoundPhase.ANIMATING) return
        isTurbo = true
    }

    /** Use a mulligan to reset the round to its starting state. Returns true if successful. */
    fun useMulligan(): Boolean {
        if (mulligans <= 0) return false
        if (roundPhase == RoundPhase.ROUND_END || isGameOver) return false
        mulligans--
        mulligansUsed++

        // Restore board from snapshot
        board.clear()
        for (brick in snapshotBricks) {
            board.setBrick(brick.row, brick.col, brick.copy())
        }

        // Restore state to round start
        cannon.x = snapshotCannonX
        score = snapshotScore
        balls.clear()
        cannon.ammo = round
        cannon.aimAngle = 90f
        cannon.isAiming = false
        cannon.isFiring = false
        firstDespawnX = null
        isTurbo = false
        brickShiftOffset = 0f
        isCannonSliding = false
        roundPhase = RoundPhase.AIMING
        return true
    }

    private fun saveRoundSnapshot() {
        snapshotBricks = board.getAllBricks().map { it.copy() }
        snapshotCannonX = cannon.x
        snapshotScore = score
    }

    private fun turboFireDelay(): Long {
        return if (isTurbo) (Constants.FIRE_DELAY_MS / Constants.TURBO_SPEED_MULTIPLIER).toLong()
        else Constants.FIRE_DELAY_MS
    }

    /** Advance per-frame animations (cannon slide, brick shift). Called every frame. */
    fun updateAnimations(dt: Float) {
        // Cannon slides along baseline to target position
        if (isCannonSliding) {
            val diff = cannonTargetX - cannon.x
            if (abs(diff) < 2f) {
                cannon.x = cannonTargetX
                isCannonSliding = false
            } else {
                val step = sign(diff) * Constants.CANNON_SLIDE_SPEED * dt
                cannon.x += if (abs(step) > abs(diff)) diff else step
            }
        }

        // Bricks ease into position after a row shift
        if (brickShiftOffset > 0f) {
            brickShiftOffset = (brickShiftOffset - Constants.BRICK_SHIFT_SPEED * dt).coerceAtLeast(0f)
        }

        // Board-clear banner countdown
        if (boardClearTimer > 0f) {
            boardClearTimer = (boardClearTimer - dt).coerceAtLeast(0f)
        }
    }
}
