package com.rapidfire.game.generation

import com.rapidfire.game.model.Brick
import com.rapidfire.game.model.BrickShape
import com.rapidfire.game.util.Constants
import kotlin.random.Random

class RowGenerator(private val params: GenerationParams = GenerationParams()) {

    fun generateRow(roundNumber: Int): List<Brick> {
        val brickCount = Random.nextInt(params.minBricksPerRow, params.maxBricksPerRow + 1)
        val columns = (0 until Constants.GRID_COLUMNS).shuffled().take(brickCount)

        return columns.map { col ->
            // Boss rounds (every 10th): each brick has 60% chance of double value
            val brickValue = if (roundNumber % 10 == 0 && Random.nextFloat() < 0.60f) {
                roundNumber * 2
            } else {
                roundNumber
            }
            Brick(
                row = Constants.GRID_ROWS - 2, // placed in Row 8 (index 7)
                col = col,
                shape = randomShape(),
                value = brickValue
            )
        }
    }

    private fun randomShape(): BrickShape {
        val roll = Random.nextFloat()
        var cumulative = 0f

        cumulative += params.squareProbability
        if (roll < cumulative) return BrickShape.SQUARE

        cumulative += params.triangleTLProbability
        if (roll < cumulative) return BrickShape.TRIANGLE_TL

        cumulative += params.triangleTRProbability
        if (roll < cumulative) return BrickShape.TRIANGLE_TR

        cumulative += params.triangleBLProbability
        if (roll < cumulative) return BrickShape.TRIANGLE_BL

        return BrickShape.TRIANGLE_BR
    }
}
