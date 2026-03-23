package com.rapidfire.game.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scores")
data class ScoreEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val score: Int = 0,
    val round: Int,
    val bricksDestroyed: Int = 0,
    val boardClears: Int = 0,
    val mulligansUsed: Int = 0,
    val shotsFired: Int = 0,
    val gameMode: String = "CLASSIC",
    val timestamp: Long = System.currentTimeMillis()
)
