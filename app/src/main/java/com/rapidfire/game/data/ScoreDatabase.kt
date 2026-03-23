package com.rapidfire.game.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ScoreEntity::class], version = 3, exportSchema = false)
abstract class ScoreDatabase : RoomDatabase() {
    abstract fun scoreDao(): ScoreDao

    companion object {
        @Volatile
        private var INSTANCE: ScoreDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE scores ADD COLUMN score INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE scores ADD COLUMN bricksDestroyed INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE scores ADD COLUMN boardClears INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE scores ADD COLUMN mulligansUsed INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE scores ADD COLUMN shotsFired INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE scores ADD COLUMN gameMode TEXT NOT NULL DEFAULT 'CLASSIC'")
            }
        }

        fun getInstance(context: Context): ScoreDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ScoreDatabase::class.java,
                    "rapidfire_scores.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build().also { INSTANCE = it }
            }
        }
    }
}
