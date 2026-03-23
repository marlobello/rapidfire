package com.rapidfire.game.model

import com.rapidfire.game.util.Constants

class GameBoard {
    // Grid storage: [row][col], row 0 = bottom (Row 1 in game terms), row 7 = top active row (Row 8)
    private val grid = Array(Constants.GRID_ROWS) { arrayOfNulls<Brick>(Constants.GRID_COLUMNS) }

    fun getBrick(row: Int, col: Int): Brick? {
        if (row !in 0 until Constants.GRID_ROWS || col !in 0 until Constants.GRID_COLUMNS) return null
        return grid[row][col]
    }

    fun setBrick(row: Int, col: Int, brick: Brick?) {
        if (row in 0 until Constants.GRID_ROWS && col in 0 until Constants.GRID_COLUMNS) {
            grid[row][col] = brick
        }
    }

    fun removeBrick(row: Int, col: Int) {
        setBrick(row, col, null)
    }

    /** Shift all rows down by one. Returns true if game over (any brick pushed to row 0). */
    fun shiftDown(): Boolean {
        // Shift rows down: row 0 = row 1, row 1 = row 2, etc.
        for (row in 0 until Constants.GRID_ROWS - 1) {
            for (col in 0 until Constants.GRID_COLUMNS) {
                grid[row][col] = grid[row + 1][col]?.copy(row = row)
            }
        }

        // Clear the top row (row 8, index GRID_ROWS - 1) — always empty
        for (col in 0 until Constants.GRID_COLUMNS) {
            grid[Constants.GRID_ROWS - 1][col] = null
        }

        // Check game-over: any brick in row 0
        for (col in 0 until Constants.GRID_COLUMNS) {
            if (grid[0][col] != null) return true
        }
        return false
    }

    /** Place new bricks in row 7 (index 7, the second-to-top row, which is "Row 8" in game terms after row 8/index 8 stays empty). */
    fun placeNewRow(bricks: List<Brick>) {
        for (brick in bricks) {
            grid[Constants.GRID_ROWS - 2][brick.col] = brick.copy(row = Constants.GRID_ROWS - 2)
        }
    }

    fun getAllBricks(): List<Brick> {
        val result = mutableListOf<Brick>()
        for (row in 0 until Constants.GRID_ROWS) {
            for (col in 0 until Constants.GRID_COLUMNS) {
                grid[row][col]?.let { result.add(it) }
            }
        }
        return result
    }

    fun clear() {
        for (row in 0 until Constants.GRID_ROWS) {
            for (col in 0 until Constants.GRID_COLUMNS) {
                grid[row][col] = null
            }
        }
    }
}
