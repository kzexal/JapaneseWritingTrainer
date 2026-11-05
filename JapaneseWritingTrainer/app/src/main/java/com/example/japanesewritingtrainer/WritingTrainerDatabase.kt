package com.example.japanesewritingtrainer

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [CardProgressEntity::class],
    version = 1,
    exportSchema = false
)
abstract class WritingTrainerDatabase : RoomDatabase() {

    abstract fun cardProgressDao(): CardProgressDao

    companion object {
        @Volatile
        private var INSTANCE: WritingTrainerDatabase? = null

        fun getInstance(context: Context): WritingTrainerDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    WritingTrainerDatabase::class.java,
                    "japanese_writing_trainer.db"
                ).build().also { INSTANCE = it }
            }
    }
}
