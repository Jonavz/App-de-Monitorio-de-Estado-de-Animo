package com.example.examenu2

// Promedio y periodo (period será día/semana/mes según la query)
data class PeriodAverage(
    val period: String,
    val average: Double
)

data class MoodCount(
    val moodLevel: Int,
    val count: Int
)