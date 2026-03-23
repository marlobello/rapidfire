package com.rapidfire.game.engine

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.rapidfire.game.audio.SoundManager
import com.rapidfire.game.physics.CollisionDetector
import com.rapidfire.game.util.Constants

class GameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SurfaceView(context, attrs), SurfaceHolder.Callback {

    private var gameThread: GameThread? = null
    val gameState = GameState()
    private val renderer = Renderer()
    private val collisionDetector = CollisionDetector()
    private lateinit var inputHandler: InputHandler
    private var gameLoop: GameLoop? = null
    var soundManager: SoundManager? = null

    /** Callback receives a Bundle of game stats on game over. */
    var onGameOver: ((android.os.Bundle) -> Unit)? = null
    /** Called (on UI thread) when the player taps the pause icon. */
    var onPauseRequested: (() -> Unit)? = null

    private var isPaused = false
    private var gameOverHandled = false

    // Thread-safe action queue — UI thread enqueues, game thread dequeues
    private val pendingActions = mutableListOf<() -> Unit>()

    init {
        holder.addCallback(this)
        isFocusable = true

        inputHandler = InputHandler(gameState.cannon) {
            gameState.startFiring()
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        startGame()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        renderer.calculateDimensions(width, height)
        collisionDetector.updateDimensions(
            leftBound = renderer.playAreaLeft,
            rightBound = renderer.playAreaRight,
            topBound = renderer.playAreaTop,
            bottomBound = renderer.cannonScreenY,
            cellWidth = renderer.cellWidth,
            cellHeight = renderer.cellHeight,
            offsetX = renderer.offsetX,
            offsetY = renderer.offsetY
        )
        inputHandler.setCannonScreenY(renderer.cannonScreenY)

        // Pass screen-space bounds to GameState for ball spawning & cannon clamping
        gameState.cannonScreenY = renderer.cannonScreenY
        gameState.playAreaLeft = renderer.playAreaLeft
        gameState.playAreaRight = renderer.playAreaRight

        if (gameState.round == 0) {
            gameState.initNewGame()
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        stopThread()
    }

    fun startGame() {
        if (gameThread?.isRunning == true) return
        isPaused = false
        gameThread = GameThread(holder, this).also { it.start() }
    }

    fun pauseGame() {
        isPaused = true
    }

    fun resumeGame() {
        isPaused = false
        gameLoop?.reset()
    }

    fun restartGame() {
        gameState.initNewGame()
        gameLoop?.effects?.clear()
        gameLoop?.reset()
        gameOverHandled = false
        isPaused = false
    }

    // --- Power-up actions (called from UI thread, executed on game thread) ---

    fun recall() {
        synchronized(pendingActions) { pendingActions.add { gameState.recall() } }
    }

    fun activateTurbo() {
        synchronized(pendingActions) { pendingActions.add { gameState.activateTurbo() } }
    }

    fun useMulligan() {
        synchronized(pendingActions) { pendingActions.add { gameState.useMulligan() } }
    }

    private fun stopThread() {
        gameThread?.isRunning = false
        var retry = true
        while (retry) {
            try {
                gameThread?.join()
                retry = false
            } catch (_: InterruptedException) {
                // retry
            }
        }
        gameThread = null
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (gameState.isGameOver) return false

        if (event.action == MotionEvent.ACTION_DOWN) {
            val x = event.x
            val y = event.y

            // Pause button (works even when paused)
            if (renderer.pauseButtonRect.contains(x, y)) {
                post { onPauseRequested?.invoke() }
                return true
            }

            if (isPaused) return false

            // Power-up buttons
            val ballsInPlay = gameState.roundPhase == GameState.RoundPhase.FIRING ||
                    gameState.roundPhase == GameState.RoundPhase.ANIMATING

            if (renderer.mulliganButtonRect.contains(x, y) &&
                gameState.mulligans > 0 && ballsInPlay
            ) {
                useMulligan()
                return true
            }
            if (renderer.recallButtonRect.contains(x, y) && ballsInPlay) {
                recall()
                return true
            }
            if (renderer.turboButtonRect.contains(x, y) && ballsInPlay && !gameState.isTurbo) {
                activateTurbo()
                return true
            }
        }

        if (isPaused) return false
        if (gameState.brickShiftOffset > 0f || gameState.isCannonSliding) return false
        return inputHandler.handleTouch(event, gameState)
    }

    fun updateAndRender(canvas: Canvas) {
        if (gameLoop == null) {
            gameLoop = GameLoop(gameState, collisionDetector, soundManager)
        }

        // Process pending actions from UI thread
        synchronized(pendingActions) {
            for (action in pendingActions) action()
            pendingActions.clear()
        }

        if (!isPaused) {
            gameLoop?.update()

            if (gameState.isGameOver && !gameOverHandled) {
                gameOverHandled = true
                isPaused = true // Stop the game loop from running further
                val stats = android.os.Bundle().apply {
                    putInt("round", gameState.round)
                    putInt("score", gameState.score)
                    putInt("bricksDestroyed", gameState.bricksDestroyed)
                    putInt("boardClears", gameState.boardClears)
                    putInt("mulligansUsed", gameState.mulligansUsed)
                    putInt("shotsFired", gameState.shotsFired)
                }
                onGameOver?.invoke(stats)
            }
        }

        renderer.render(canvas, gameState, collisionDetector, inputHandler, gameLoop?.effects)
    }

    inner class GameThread(
        private val surfaceHolder: SurfaceHolder,
        private val gameView: GameView
    ) : Thread("GameThread") {
        var isRunning = false

        override fun run() {
            isRunning = true
            while (isRunning) {
                val startTime = System.currentTimeMillis()
                var canvas: Canvas? = null
                try {
                    canvas = surfaceHolder.lockCanvas()
                    if (canvas != null) {
                        gameView.updateAndRender(canvas)
                    }
                } catch (_: Exception) {
                    // Surface may have been destroyed
                } finally {
                    canvas?.let {
                        try {
                            surfaceHolder.unlockCanvasAndPost(it)
                        } catch (_: Exception) {
                            // ignore
                        }
                    }
                }

                val elapsed = System.currentTimeMillis() - startTime
                val sleepTime = Constants.FRAME_TIME_MS - elapsed
                if (sleepTime > 0) {
                    try {
                        sleep(sleepTime)
                    } catch (_: InterruptedException) {
                        // ignore
                    }
                }
            }
        }
    }
}
