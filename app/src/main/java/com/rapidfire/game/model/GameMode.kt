package com.rapidfire.game.model

enum class GameMode(
    val baseRound: Int,
    val roundIncrement: Int,
    val displayName: String
) {
    CLASSIC(baseRound = 0, roundIncrement = 1, displayName = "Classic"),
    BOSS_RUSH(baseRound = 0, roundIncrement = 10, displayName = "Boss Rush"),
    QUICK_START_50(baseRound = 49, roundIncrement = 1, displayName = "Quick Start 50"),
    QUICK_START_100(baseRound = 99, roundIncrement = 1, displayName = "Quick Start 100");

    companion object {
        fun fromName(name: String): GameMode =
            entries.find { it.name == name } ?: CLASSIC
    }
}
