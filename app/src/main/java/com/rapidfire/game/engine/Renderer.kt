package com.rapidfire.game.engine

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.rapidfire.game.model.Ball
import com.rapidfire.game.model.Brick
import com.rapidfire.game.model.BrickShape
import com.rapidfire.game.model.Cannon
import com.rapidfire.game.physics.CollisionDetector
import com.rapidfire.game.util.ColorPalette
import com.rapidfire.game.util.Constants
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class Renderer {
    private val brickPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val brickTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val ballPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ColorPalette.ballColor
    }
    private val cannonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ColorPalette.cannonColor
    }
    private val aimLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ColorPalette.aimLineColor
        style = Paint.Style.STROKE
        strokeWidth = 3f
        pathEffect = DashPathEffect(floatArrayOf(15f, 10f), 0f)
    }
    private val hudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ColorPalette.hudTextColor
        textSize = 56f
        textAlign = Paint.Align.LEFT
        isFakeBoldText = true
    }
    private val backgroundPaint = Paint().apply {
        color = ColorPalette.backgroundColor
    }
    private val boundaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(80, 255, 255, 255)
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }
    private val pauseIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 255, 255, 255)
        style = Paint.Style.FILL
    }
    private val buttonOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }
    private val buttonTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val fanfarePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val fanfareRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val fanfareBonusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 215, 0)  // gold
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val popupPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        textSize = 28f
    }
    private val flashPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bgGradientPaint = Paint()
    private var bgGradient: RadialGradient? = null
    private var bgLinearGradient: LinearGradient? = null
    private var lastScreenW = 0f
    private var lastScreenH = 0f

    /** Hit-test rects for canvas-drawn buttons */
    var pauseButtonRect = RectF()
        private set
    var mulliganButtonRect = RectF()
        private set
    var recallButtonRect = RectF()
        private set
    var turboButtonRect = RectF()
        private set

    var cellWidth = 0f
        private set
    var cellHeight = 0f
        private set
    var offsetX = 0f
        private set
    var offsetY = 0f
        private set
    var cannonScreenY = 0f
        private set
    var playAreaLeft = 0f
        private set
    var playAreaRight = 0f
        private set
    var playAreaTop = 0f
        private set

    fun calculateDimensions(width: Int, height: Int) {
        val screenW = width.toFloat()
        val screenH = height.toFloat()

        val sidePadding = 24f
        val hudHeight = 240f     // bigger title + padding + round/score/pause row
        val ammoAreaBelow = 50f  // space below baseline for ammo text
        val buttonAreaBelow = 200f // power-up buttons below ammo (taller buttons + padding)
        val minPadding = 20f     // minimum vertical padding

        // Available space for grid
        val availW = screenW - sidePadding * 2
        val maxGridH = screenH - hudHeight - ammoAreaBelow - buttonAreaBelow - minPadding * 2

        // Square cells — constrained by the smaller dimension
        val cellByW = availW / Constants.GRID_COLUMNS
        val cellByH = maxGridH / Constants.GRID_ROWS
        val cellSize = min(cellByW, cellByH)

        cellWidth = cellSize
        cellHeight = cellSize

        val gridW = cellSize * Constants.GRID_COLUMNS
        val gridH = cellSize * Constants.GRID_ROWS

        // Center everything vertically within the SurfaceView
        val totalContentH = hudHeight + gridH + ammoAreaBelow + buttonAreaBelow
        val topMargin = (screenH - totalContentH) / 2f

        // Center grid horizontally
        offsetX = (screenW - gridW) / 2f
        offsetY = topMargin + hudHeight

        // Cannon sits on the bottom boundary line
        cannonScreenY = offsetY + gridH

        playAreaLeft = offsetX
        playAreaRight = offsetX + gridW
        playAreaTop = offsetY

        // Build background gradient (only when dimensions change)
        if (screenW != lastScreenW || screenH != lastScreenH) {
            lastScreenW = screenW
            lastScreenH = screenH
            // Top-to-bottom linear gradient for a sleek, non-bullseye look
            bgGradient = null // clear old radial
            bgLinearGradient = LinearGradient(
                0f, 0f, 0f, screenH,
                intArrayOf(
                    Color.rgb(18, 22, 36),   // dark blue-grey at top
                    Color.rgb(12, 14, 22),   // deep navy middle
                    Color.rgb(6, 6, 10)      // near-black at bottom
                ),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
            bgGradientPaint.shader = bgLinearGradient
        }
    }

    fun render(canvas: Canvas, state: GameState, collisionDetector: CollisionDetector,
               inputHandler: InputHandler, effects: VisualEffects? = null) {
        // Gradient background
        if (bgLinearGradient != null) {
            canvas.drawRect(0f, 0f, lastScreenW, lastScreenH, bgGradientPaint)
        } else {
            canvas.drawColor(ColorPalette.backgroundColor)
        }

        // Subtle grid lines inside play area
        drawBackgroundGrid(canvas)

        // Draw HUD and boundary lines
        drawHUD(canvas, state)
        drawBoundaryLines(canvas)

        // Draw bricks — clipped to play area so shift animation doesn't overflow
        canvas.save()
        canvas.clipRect(playAreaLeft, playAreaTop, playAreaRight, cannonScreenY)
        val shiftPx = state.brickShiftOffset * cellHeight
        for (brick in state.board.getAllBricks()) {
            drawBrick(canvas, brick, collisionDetector, shiftPx)
        }
        canvas.restore()

        // Draw aim line (if aiming)
        if (state.cannon.isAiming) {
            drawAimLine(canvas, state.cannon)
        }

        // Draw balls
        for (ball in state.balls) {
            if (ball.active) {
                drawBall(canvas, ball)
            }
        }

        // Draw cannon
        drawCannon(canvas, state.cannon)

        // Draw ammo indicator
        drawAmmoIndicator(canvas, state)

        // Draw power-up buttons below ammo area
        drawPowerUpButtons(canvas, state)

        // Spawn "+1" popup over mulligan button when earned
        if (state.mulliganJustEarned && effects != null) {
            state.mulliganJustEarned = false
            effects.spawnButtonPopup(
                mulliganButtonRect.centerX(),
                mulliganButtonRect.top - 10f,
                "+1"
            )
        }

        // Draw visual effects (particles, score popups, muzzle flash)
        effects?.let { drawEffects(canvas, it) }

        // Draw board-clear fanfare overlay
        if (state.boardClearTimer > 0f) {
            drawBoardClearFanfare(canvas, state.boardClearTimer)
        }
    }

    private fun drawHUD(canvas: Canvas, state: GameState) {
        // Game title at the top of the HUD band — bold gradient-style
        val centerX = (playAreaLeft + playAreaRight) / 2f
        val titleY = playAreaTop - 160f   // generous padding below title

        // "RAPID" in orange-red, "FIRE" in gold-yellow — matches title screen style
        val rapidPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 90f
            textAlign = Paint.Align.RIGHT
            isFakeBoldText = true
            letterSpacing = 0.05f
            color = Color.rgb(255, 80, 40)
        }
        val firePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 90f
            textAlign = Paint.Align.LEFT
            isFakeBoldText = true
            letterSpacing = 0.05f
            color = Color.rgb(255, 200, 40)
        }
        val gap = 10f
        canvas.drawText("Rapid", centerX - gap, titleY, rapidPaint)
        canvas.drawText("Fire", centerX + gap, titleY, firePaint)

        // Round and Score on the lower HUD row
        val hudY = playAreaTop - 44f     // extra padding below title
        canvas.drawText("Round ${state.round}", playAreaLeft, hudY, hudPaint)

        val scoreText = "Score: ${state.score}"
        val scoreWidth = hudPaint.measureText(scoreText)
        canvas.drawText(scoreText, playAreaRight - scoreWidth, hudY, hudPaint)

        // Pause button — square outlined button with ⏸ bars inside
        val btnSize = 64f
        val btnTop = hudY - btnSize + 10f
        val btnRect = RectF(centerX - btnSize / 2f, btnTop, centerX + btnSize / 2f, btnTop + btnSize)
        pauseButtonRect.set(btnRect)

        buttonOutlinePaint.color = Color.argb(200, 255, 255, 255)
        canvas.drawRoundRect(btnRect, 12f, 12f, buttonOutlinePaint)

        // Draw pause bars inside the button (scaled to square)
        val barW = 10f
        val barH = 30f
        val barGap = 8f
        val barTop = btnRect.centerY() - barH / 2f
        pauseIconPaint.color = Color.argb(220, 255, 255, 255)
        canvas.drawRoundRect(
            centerX - barGap - barW, barTop, centerX - barGap, barTop + barH,
            2f, 2f, pauseIconPaint
        )
        canvas.drawRoundRect(
            centerX + barGap, barTop, centerX + barGap + barW, barTop + barH,
            2f, 2f, pauseIconPaint
        )
    }

    private fun drawBoundaryLines(canvas: Canvas) {
        // Top boundary (ball bounce ceiling)
        canvas.drawLine(playAreaLeft, playAreaTop, playAreaRight, playAreaTop, boundaryPaint)
        // Bottom boundary (cannon track)
        canvas.drawLine(playAreaLeft, cannonScreenY, playAreaRight, cannonScreenY, boundaryPaint)
        // Side boundaries
        canvas.drawLine(playAreaLeft, playAreaTop, playAreaLeft, cannonScreenY, boundaryPaint)
        canvas.drawLine(playAreaRight, playAreaTop, playAreaRight, cannonScreenY, boundaryPaint)
    }

    private fun drawBrick(canvas: Canvas, brick: Brick, collisionDetector: CollisionDetector, shiftOffsetPx: Float) {
        val rect = collisionDetector.getBrickRect(brick.row, brick.col)
        // Shift bricks up during animation (they slide from old position to new)
        if (shiftOffsetPx > 0f) {
            rect.offset(0f, -shiftOffsetPx)
        }
        brickPaint.color = brick.color

        when (brick.shape) {
            BrickShape.SQUARE -> {
                canvas.drawRoundRect(rect, 4f, 4f, brickPaint)
            }
            else -> {
                val path = getTrianglePath(brick.shape, rect)
                canvas.drawPath(path, brickPaint)
            }
        }

        // Draw value text
        val textSize = min(cellWidth, cellHeight) * 0.35f
        brickTextPaint.textSize = textSize
        val textX = rect.centerX()
        val textY = rect.centerY() + textSize / 3f

        // For triangles, offset text toward the right-angle corner
        val (tx, ty) = getTextOffset(brick.shape, rect, textX, textY)
        canvas.drawText(brick.value.toString(), tx, ty, brickTextPaint)
    }

    private fun getTrianglePath(shape: BrickShape, rect: RectF): Path {
        val path = Path()
        when (shape) {
            BrickShape.TRIANGLE_TL -> {
                path.moveTo(rect.left, rect.top)
                path.lineTo(rect.right, rect.top)
                path.lineTo(rect.left, rect.bottom)
                path.close()
            }
            BrickShape.TRIANGLE_TR -> {
                path.moveTo(rect.left, rect.top)
                path.lineTo(rect.right, rect.top)
                path.lineTo(rect.right, rect.bottom)
                path.close()
            }
            BrickShape.TRIANGLE_BL -> {
                path.moveTo(rect.left, rect.top)
                path.lineTo(rect.left, rect.bottom)
                path.lineTo(rect.right, rect.bottom)
                path.close()
            }
            BrickShape.TRIANGLE_BR -> {
                path.moveTo(rect.right, rect.top)
                path.lineTo(rect.left, rect.bottom)
                path.lineTo(rect.right, rect.bottom)
                path.close()
            }
            else -> {} // SQUARE handled separately
        }
        return path
    }

    private fun getTextOffset(shape: BrickShape, rect: RectF, defaultX: Float, defaultY: Float): Pair<Float, Float> {
        val offsetFactor = 0.15f
        val w = rect.width() * offsetFactor
        val h = rect.height() * offsetFactor
        return when (shape) {
            BrickShape.TRIANGLE_TL -> (defaultX - w) to (defaultY - h)
            BrickShape.TRIANGLE_TR -> (defaultX + w) to (defaultY - h)
            BrickShape.TRIANGLE_BL -> (defaultX - w) to (defaultY + h)
            BrickShape.TRIANGLE_BR -> (defaultX + w) to (defaultY + h)
            else -> defaultX to defaultY
        }
    }

    private fun drawBall(canvas: Canvas, ball: Ball) {
        canvas.drawCircle(ball.x, ball.y, Constants.BALL_RADIUS, ballPaint)
    }

    private fun drawCannon(canvas: Canvas, cannon: Cannon) {
        val cx = cannon.x
        val cy = cannonScreenY

        // Draw cannon base as a semicircle sitting ON the baseline (top half only)
        val baseRadius = Constants.CANNON_WIDTH / 2f
        val oval = RectF(cx - baseRadius, cy - baseRadius, cx + baseRadius, cy + baseRadius)
        canvas.drawArc(oval, 180f, 180f, true, cannonPaint)

        // Draw barrel — clipped to above the baseline so nothing bleeds below
        val angleRad = Math.toRadians(cannon.aimAngle.toDouble())
        val barrelEndX = cx + (cos(angleRad) * Constants.BARREL_LENGTH).toFloat()
        val barrelEndY = cy - (sin(angleRad) * Constants.BARREL_LENGTH).toFloat()

        val barrelPaint = Paint(cannonPaint).apply {
            strokeWidth = Constants.BARREL_WIDTH
            strokeCap = Paint.Cap.ROUND
        }
        canvas.save()
        canvas.clipRect(0f, 0f, canvas.width.toFloat(), cy)
        canvas.drawLine(cx, cy, barrelEndX, barrelEndY, barrelPaint)
        canvas.restore()
    }

    private fun drawAimLine(canvas: Canvas, cannon: Cannon) {
        val angleRad = Math.toRadians(cannon.aimAngle.toDouble())
        // Start from the barrel tip, not cannon center
        val startX = cannon.x + (cos(angleRad) * Constants.BARREL_LENGTH).toFloat()
        val startY = cannonScreenY - (sin(angleRad) * Constants.BARREL_LENGTH).toFloat()
        val lineLength = 400f

        val endX = startX + (cos(angleRad) * lineLength).toFloat()
        val endY = startY - (sin(angleRad) * lineLength).toFloat()

        canvas.drawLine(startX, startY, endX, endY, aimLinePaint)
    }

    private fun drawAmmoIndicator(canvas: Canvas, state: GameState) {
        val text = "×${state.cannon.ammo}"
        val smallPaint = Paint(hudPaint).apply {
            textSize = 32f
            textAlign = Paint.Align.LEFT
        }
        val textWidth = smallPaint.measureText(text)
        val ballIconRadius = 10f
        val gap = 6f
        val totalWidth = ballIconRadius * 2 + gap + textWidth

        // Center the icon+text group under the cannon
        val startX = state.cannon.x - totalWidth / 2f
        val indicatorY = cannonScreenY + 45f

        // Draw ball icon
        canvas.drawCircle(startX + ballIconRadius, indicatorY - ballIconRadius + 2f, ballIconRadius, ballPaint)
        // Draw count text
        canvas.drawText(text, startX + ballIconRadius * 2 + gap, indicatorY, smallPaint)
    }

    private fun drawBoardClearFanfare(canvas: Canvas, timer: Float) {
        val totalDuration = Constants.BOARD_CLEAR_DISPLAY_SECS
        val progress = 1f - (timer / totalDuration)  // 0 → 1 over the display time
        val alpha = ((1f - progress) * 255).toInt().coerceIn(0, 255)

        val centerX = (playAreaLeft + playAreaRight) / 2f
        val centerY = (playAreaTop + cannonScreenY) / 2f

        // Expanding rings (3 rings staggered)
        val maxRadius = (playAreaRight - playAreaLeft) * 0.6f
        val ringColors = intArrayOf(
            Color.rgb(255, 215, 0),   // gold
            Color.rgb(255, 140, 0),   // dark orange
            Color.rgb(255, 69, 0)     // red-orange
        )
        for (i in 0 until 3) {
            val ringProgress = (progress - i * 0.12f).coerceIn(0f, 1f)
            val radius = ringProgress * maxRadius
            val ringAlpha = ((1f - ringProgress) * alpha).toInt().coerceIn(0, 255)
            if (ringAlpha > 0 && radius > 0f) {
                fanfareRingPaint.color = ringColors[i]
                fanfareRingPaint.alpha = ringAlpha
                fanfareRingPaint.strokeWidth = 4f + (1f - ringProgress) * 8f
                canvas.drawCircle(centerX, centerY, radius, fanfareRingPaint)
            }
        }

        // "BOARD CLEARED!" text — scales up slightly then holds
        val textScale = if (progress < 0.15f) 0.6f + progress / 0.15f * 0.4f else 1f
        fanfarePaint.textSize = 64f * textScale
        fanfarePaint.alpha = alpha
        canvas.drawText("BOARD CLEARED!", centerX, centerY - 10f, fanfarePaint)

        // Bonus text below
        fanfareBonusPaint.textSize = 40f * textScale
        fanfareBonusPaint.alpha = alpha
        canvas.drawText("+${Constants.BOARD_CLEAR_BONUS}", centerX, centerY + 50f, fanfareBonusPaint)
    }

    private fun drawPowerUpButtons(canvas: Canvas, state: GameState) {
        val ballsInPlay = state.roundPhase == GameState.RoundPhase.FIRING ||
                state.roundPhase == GameState.RoundPhase.ANIMATING

        val btnH = 165f           // 50% taller than the old 110
        val btnSpacing = 18f      // more breathing room between buttons
        val btnTop = cannonScreenY + 85f  // breathing room below ammo
        val totalW = playAreaRight - playAreaLeft
        val btnW = (totalW - btnSpacing * 2) / 3f

        // Compute rects
        mulliganButtonRect.set(playAreaLeft, btnTop, playAreaLeft + btnW, btnTop + btnH)
        recallButtonRect.set(playAreaLeft + btnW + btnSpacing, btnTop,
            playAreaLeft + btnW * 2 + btnSpacing, btnTop + btnH)
        turboButtonRect.set(playAreaRight - btnW, btnTop, playAreaRight, btnTop + btnH)

        // Determine enabled state
        val mulliganEnabled = state.mulligans > 0 && ballsInPlay
        val recallEnabled = ballsInPlay
        val turboEnabled = ballsInPlay && !state.isTurbo

        val mulliganText = if (state.mulligans > 0) "Mulligan (${state.mulligans})" else "Mulligan"
        drawButton(canvas, mulliganButtonRect, mulliganText, mulliganEnabled)
        drawButton(canvas, recallButtonRect, "Recall", recallEnabled)
        drawButton(canvas, turboButtonRect, "Turbo", turboEnabled)
    }

    private fun drawButton(canvas: Canvas, rect: RectF, text: String, enabled: Boolean) {
        val alpha = if (enabled) 220 else 50
        buttonOutlinePaint.color = Color.argb(alpha, 255, 255, 255)
        canvas.drawRoundRect(rect, 14f, 14f, buttonOutlinePaint)

        buttonTextPaint.color = Color.argb(alpha, 255, 255, 255)
        buttonTextPaint.textSize = 44f       // bigger font for taller buttons
        val textY = rect.centerY() - (buttonTextPaint.descent() + buttonTextPaint.ascent()) / 2f
        canvas.drawText(text, rect.centerX(), textY, buttonTextPaint)
    }

    private fun drawBackgroundGrid(canvas: Canvas) {
        val gridPaint = Paint().apply {
            color = Color.argb(18, 255, 255, 255)
            strokeWidth = 1f
        }
        // Vertical lines
        for (col in 0..Constants.GRID_COLUMNS) {
            val x = playAreaLeft + col * cellWidth
            canvas.drawLine(x, playAreaTop, x, cannonScreenY, gridPaint)
        }
        // Horizontal lines
        for (row in 0..Constants.GRID_ROWS) {
            val y = playAreaTop + row * cellHeight
            canvas.drawLine(playAreaLeft, y, playAreaRight, y, gridPaint)
        }
    }

    private fun drawEffects(canvas: Canvas, effects: VisualEffects) {
        // Muzzle flash — bright white+yellow circle at barrel tip
        effects.muzzleFlash?.let { flash ->
            val alpha = (flash.life * 255).toInt().coerceIn(0, 255)
            // Outer glow
            flashPaint.color = Color.argb(alpha / 2, 255, 200, 50)
            canvas.drawCircle(flash.x, flash.y, 30f * flash.life + 10f, flashPaint)
            // Inner core
            flashPaint.color = Color.argb(alpha, 255, 255, 220)
            canvas.drawCircle(flash.x, flash.y, 14f * flash.life + 4f, flashPaint)
        }

        // Particles
        for (p in effects.particles) {
            val alpha = (p.life * 255).toInt().coerceIn(0, 255)
            particlePaint.color = p.color
            particlePaint.alpha = alpha
            canvas.drawCircle(p.x, p.y, p.size * p.life, particlePaint)
        }

        // Score popups
        for (s in effects.scorePopups) {
            val alpha = (s.life * 255).toInt().coerceIn(0, 255)
            popupPaint.color = Color.argb(alpha, 255, 255, 255)
            // Slight scale-up on spawn
            val scale = if (s.life > 0.8f) 1f + (s.life - 0.8f) * 2f else 1f
            popupPaint.textSize = 28f * scale
            canvas.drawText(s.text, s.x, s.y, popupPaint)
        }

        // Button popups ("+1" over mulligan button)
        for (b in effects.buttonPopups) {
            val alpha = (b.life * 255).toInt().coerceIn(0, 255)
            val scale = if (b.life > 0.85f) 1f + (b.life - 0.85f) * 4f else 1f
            popupPaint.color = Color.argb(alpha, 255, 200, 40)  // gold like "Fire" title
            popupPaint.textSize = 40f * scale
            canvas.drawText(b.text, b.x, b.y, popupPaint)
        }
    }
}
