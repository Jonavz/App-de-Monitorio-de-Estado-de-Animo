package com.example.examenu2

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.examenu2.MoodDao
import com.example.examenu2.MoodEntry

@Database(entities = [MoodEntry::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun moodDao(): MoodDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val inst = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mood_database"
                ).build()
                INSTANCE = inst
                inst
            }
        }
    }
}
