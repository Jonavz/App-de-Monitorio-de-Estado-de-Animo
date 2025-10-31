package com.example.examenu2

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "mood_entries")
data class MoodEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: Long = System.currentTimeMillis(),
    val moodLevel: Int,
    val activities: String = "",
    val location: String? = null,
    val notes: String? = null
)