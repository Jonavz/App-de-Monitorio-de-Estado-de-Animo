package com.example.examenu2

import com.example.examenu2.MoodDao
import com.example.examenu2.MoodEntry

class MoodRepository(private val dao: MoodDao) {
    suspend fun insert(mood: MoodEntry) = dao.insertMood(mood)
    suspend fun getAll() = dao.getAllMoods()
    suspend fun getAveragePerDay() = dao.getAveragePerDay()
    suspend fun getMoodCounts() = dao.getMoodCounts()
}
