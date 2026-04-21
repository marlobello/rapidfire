package com.rapidfire.game.physics

import com.rapidfire.game.model.Ball
import com.rapidfire.game.model.Brick
import com.rapidfire.game.model.BrickShape
import com.rapidfire.game.model.GameBoard
import com.rapidfire.game.util.Constants
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Behavioural tests for CollisionDetector targeting the regressions reported by users:
 *   1. Spurious bounces near corners (especially adjacent-brick boundaries)
 *   2. Tunneling — balls passing through bricks
 *
 * The tests sit on top of getBrickRect / advanceBall and use a synthetic board layout
 * so they are independent of the live GenerationParams and the Android RectF runtime
 * stub limitations seen in the existing suite.
 */
class CollisionDetectorBehaviourTest {

    private val cellW = 100f
    private val cellH = 60f
    private val offsetX = 0f
    private val offsetY = 0f
    private val leftBound = 0f
    private val rightBound = cellW * Constants.GRID_COLUMNS
    private val topBound = 0f
    private val bottomBound = cellH * Constants.GRID_ROWS + 200f
    private val tol = 0.5f

    private lateinit var detector: CollisionDetector

    @Before
    fun setUp() {
        detector = CollisionDetector()
        detector.updateDimensions(
            leftBound, rightBound, topBound, bottomBound,
            cellW, cellH, offsetX, offsetY
        )
    }

    private fun place(board: GameBoard, row: Int, col: Int, shape: BrickShape = BrickShape.SQUARE) {
        board.setBrick(row, col, Brick(row = row, col = col, shape = shape, value = 5))
    }

    @Test
    fun `ball gliding just above isolated brick is NOT deflected`() {
        // Regression: previously the corner sweep used radius (r + cornerR), so a ball
        // gliding 0.5 px above the top edge with vy=0 hit a phantom corner-bumper and
        // got deflected sharply downward.
        val board = GameBoard()
        // Game-row -> screen y: y = (GRID_ROWS-1-row)*cellH. Pick row in middle.
        val row = 4; val col = 3
        place(board, row, col)
        val rect = detector.getBrickRect(row, col)

        val ball = Ball(
            x = rect.left - 50f,                // start well left of brick
            y = rect.top - Constants.BALL_RADIUS - 0.5f,  // 0.5 px clearance above top
            vx = 800f,
            vy = 0f,
        )
        val v0x = ball.vx; val v0y = ball.vy

        val result = detector.advanceBall(ball, dt = 0.2f, board = board)

        assertFalse("Ball should not despawn", result.despawned)
        assertTrue("No bricks should be hit", result.hitBricks.isEmpty())
        assertEquals("vx must be unchanged", v0x, ball.vx, 0.01f)
        assertEquals("vy must be unchanged (no phantom corner deflection)", v0y, ball.vy, 0.01f)
    }

    @Test
    fun `ball gliding just above row of adjacent bricks is NOT deflected by interior corners`() {
        val board = GameBoard()
        val row = 4
        place(board, row, 2)
        place(board, row, 3)
        place(board, row, 4)
        val rect = detector.getBrickRect(row, 3)

        val ball = Ball(
            x = rect.left - 80f,
            y = rect.top - Constants.BALL_RADIUS - 0.5f,
            vx = 1200f,
            vy = 0f,
        )
        val v0x = ball.vx; val v0y = ball.vy

        val result = detector.advanceBall(ball, dt = 0.3f, board = board)

        assertFalse(result.despawned)
        assertTrue(
            "No bricks should be hit by a ball gliding parallel above the row " +
                "(any hit indicates a phantom interior-corner collision)",
            result.hitBricks.isEmpty()
        )
        assertEquals(v0x, ball.vx, 0.01f)
        assertEquals(v0y, ball.vy, 0.01f)
    }

    @Test
    fun `ball gliding alongside vertical column is NOT deflected by interior corners`() {
        val board = GameBoard()
        val col = 3
        place(board, 2, col)
        place(board, 3, col)
        place(board, 4, col)
        val rect = detector.getBrickRect(3, col)

        val ball = Ball(
            x = rect.right + Constants.BALL_RADIUS + 0.5f,
            y = rect.top - 80f,
            vx = 0f,
            vy = 1200f,
        )
        val v0x = ball.vx; val v0y = ball.vy

        val result = detector.advanceBall(ball, dt = 0.3f, board = board)

        assertTrue(
            "No bricks should be hit by a ball gliding parallel beside a column",
            result.hitBricks.isEmpty()
        )
        assertEquals(v0x, ball.vx, 0.01f)
        assertEquals(v0y, ball.vy, 0.01f)
    }

    @Test
    fun `ball heading straight down at top of brick reflects cleanly`() {
        val board = GameBoard()
        val row = 4; val col = 3
        place(board, row, col)
        val rect = detector.getBrickRect(row, col)

        val ball = Ball(
            x = (rect.left + rect.right) / 2f,
            y = rect.top - 80f,
            vx = 0f,
            vy = 800f,
        )
        val result = detector.advanceBall(ball, dt = 0.2f, board = board)

        assertEquals("Should have hit exactly one brick", 1, result.hitBricks.size)
        assertEquals("vx should remain 0", 0f, ball.vx, 0.01f)
        assertEquals("vy should reverse to -800", -800f, ball.vy, 0.01f)
    }

    @Test
    fun `ball glancing off exposed corner of isolated brick reflects radially`() {
        // Use an isolated brick so its top-right corner is genuinely exposed.
        val board = GameBoard()
        val row = 4; val col = 3
        place(board, row, col)
        val rect = detector.getBrickRect(row, col)

        val cornerX = rect.right
        val cornerY = rect.top
        // Aim trajectory so the ball clips the TR corner (passes within ball-radius of it)
        // without first contacting the top or right edge segments.
        val ball = Ball(
            x = cornerX + 30f,
            y = cornerY - 80f,
            vx = -250f,
            vy = 800f,
        )
        val speedBefore = sqrt(ball.vx * ball.vx + ball.vy * ball.vy)

        val result = detector.advanceBall(ball, dt = 0.2f, board = board)

        assertTrue("Should have hit the brick at its corner", result.hitBricks.isNotEmpty())
        val speedAfter = sqrt(ball.vx * ball.vx + ball.vy * ball.vy)
        assertEquals("Speed must be preserved by reflection", speedBefore, speedAfter, 0.5f)
    }

    @Test
    fun `high-speed ball does not tunnel through brick (turbo)`() {
        val board = GameBoard()
        val row = 4; val col = 3
        place(board, row, col)
        val rect = detector.getBrickRect(row, col)

        // Use 2 frames worth of motion at turbo speed, starting just past arm's-length.
        // This is a configuration where naive (non-CCD) integration would step the ball
        // straight through the brick.
        val turboVx = Constants.BALL_SPEED * Constants.TURBO_SPEED_MULTIPLIER
        val ball = Ball(
            x = rect.left - 30f,
            y = (rect.top + rect.bottom) / 2f,
            vx = turboVx,
            vy = 0f,
        )

        val result = detector.advanceBall(ball, dt = 1f / 60f, board = board)

        assertEquals("Brick must register a hit (no tunneling)", 1, result.hitBricks.size)
        assertTrue("vx should be reflected leftward", ball.vx < 0f)
        assertTrue(
            "Ball must end up outside the brick (left of left edge), got x=${ball.x}",
            ball.x + Constants.BALL_RADIUS <= rect.left + 1f
        )
    }

    @Test
    fun `ball never ends frame inside a brick`() {
        // Stress: throw many balls at a wall of bricks at varying angles and speeds and
        // verify none end up overlapping any brick after the frame completes.
        val board = GameBoard()
        for (col in 1..5) place(board, 4, col)

        val rng = java.util.Random(0xBEEFL)
        for (i in 0 until 200) {
            val ball = Ball(
                x = leftBound + 50f + rng.nextFloat() * (rightBound - leftBound - 100f),
                y = topBound + 50f + rng.nextFloat() * (detector.getBrickRect(4, 3).top - 100f),
                vx = (rng.nextFloat() - 0.5f) * 4000f,
                vy = (rng.nextFloat() + 0.2f) * 4000f,  // mostly downward
            )
            detector.advanceBall(ball, dt = 1f / 60f, board = board)

            // Verify the ball is not inside any (still-living) brick
            for (b in board.getAllBricks()) {
                if (b.isDestroyed) continue
                val rect = detector.getBrickRect(b.row, b.col)
                val nearestX = ball.x.coerceIn(rect.left, rect.right)
                val nearestY = ball.y.coerceIn(rect.top, rect.bottom)
                val dx = ball.x - nearestX
                val dy = ball.y - nearestY
                val dist = sqrt(dx * dx + dy * dy)
                assertTrue(
                    "Ball $i ended inside brick (${b.row},${b.col}): " +
                        "ball=(${ball.x},${ball.y}) rect=$rect dist=$dist",
                    dist >= Constants.BALL_RADIUS - 1f
                )
            }
        }
    }
}
