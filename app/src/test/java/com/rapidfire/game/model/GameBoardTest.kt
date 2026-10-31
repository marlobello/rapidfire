package com.rapidfire.game.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameBoardTest {

    @Test
    fun `bomb blast removes all eight adjacent bricks and leaves distant bricks`() {
        val board = GameBoard()
        val bomb = Brick(4, 3, BrickShape.SQUARE, value = 5, isBomb = true)
        board.setBrick(bomb.row, bomb.col, bomb)
        for (row in 3..5) {
            for (col in 2..4) {
                if (row == bomb.row && col == bomb.col) continue
                board.setBrick(row, col, Brick(row, col, BrickShape.SQUARE, value = 20))
            }
        }
        val distant = Brick(6, 3, BrickShape.SQUARE, value = 30)
        board.setBrick(distant.row, distant.col, distant)

        val destroyed = board.detonateAdjacent(bomb.row, bomb.col)

        assertEquals(8, destroyed.size)
        assertTrue(destroyed.all { board.getBrick(it.row, it.col) == null })
        assertEquals(distant, board.getBrick(distant.row, distant.col))
        assertEquals(bomb, board.getBrick(bomb.row, bomb.col))
    }

    @Test
    fun `bomb blast at board edge ignores out of bounds cells`() {
        val board = GameBoard()
        val bomb = Brick(0, 0, BrickShape.SQUARE, value = 1, isBomb = true)
        board.setBrick(bomb.row, bomb.col, bomb)
        board.setBrick(0, 1, Brick(0, 1, BrickShape.SQUARE, value = 1))
        board.setBrick(1, 0, Brick(1, 0, BrickShape.SQUARE, value = 1))
        board.setBrick(1, 1, Brick(1, 1, BrickShape.SQUARE, value = 1))

        val destroyed = board.detonateAdjacent(bomb.row, bomb.col)

        assertEquals(3, destroyed.size)
        assertTrue(destroyed.all { board.getBrick(it.row, it.col) == null })
        assertFalse(board.getAllBricks().any { it.row < 0 || it.col < 0 })
    }
}
