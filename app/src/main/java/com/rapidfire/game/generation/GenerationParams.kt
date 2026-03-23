package com.rapidfire.game.generation

data class GenerationParams(
    val minBricksPerRow: Int = 1,
    val maxBricksPerRow: Int = 6,
    val squareProbability: Float = 0.60f,
    val triangleTLProbability: Float = 0.10f,
    val triangleTRProbability: Float = 0.10f,
    val triangleBLProbability: Float = 0.10f,
    val triangleBRProbability: Float = 0.10f
)
