package com.rapidfire.game.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ScoreDao {
    @Insert
    suspend fun insertScore(score: ScoreEntity)

    @Query("SELECT * FROM scores ORDER BY score DESC LIMIT 20")
    suspend fun getTopScores(): List<ScoreEntity>

    @Query("SELECT MAX(score) FROM scores")
    suspend fun getHighScore(): Int?
}
