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

    @Query("SELECT * FROM scores WHERE gameMode != 'BOSS_RUSH' ORDER BY score DESC LIMIT 20")
    suspend fun getStandardScores(): List<ScoreEntity>

    @Query("SELECT * FROM scores WHERE gameMode = 'BOSS_RUSH' ORDER BY score DESC LIMIT 20")
    suspend fun getBossRushScores(): List<ScoreEntity>

    @Query("SELECT MAX(score) FROM scores")
    suspend fun getHighScore(): Int?

    @Query("SELECT MAX(score) FROM scores WHERE gameMode != 'BOSS_RUSH'")
    suspend fun getStandardHighScore(): Int?

    @Query("SELECT MAX(score) FROM scores WHERE gameMode = 'BOSS_RUSH'")
    suspend fun getBossRushHighScore(): Int?
}
