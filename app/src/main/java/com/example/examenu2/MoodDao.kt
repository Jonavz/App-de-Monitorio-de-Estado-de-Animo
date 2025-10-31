package com.example.examenu2

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.examenu2.MoodEntry
import com.example.examenu2.PeriodAverage
import com.example.examenu2.MoodCount

@Dao
interface MoodDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMood(mood: MoodEntry)

    @Query("SELECT * FROM mood_entries ORDER BY date DESC")
    suspend fun getAllMoods(): List<MoodEntry>

    @Query("SELECT * FROM mood_entries WHERE date BETWEEN :start AND :end ORDER BY date DESC")
    suspend fun getMoodsInRange(start: Long, end: Long): List<MoodEntry>

    // Promedio por día (period -> 'YYYY-MM-DD')
    @Query(
        """
        SELECT 
          date(date/1000, 'unixepoch') as period,
          AVG(moodLevel) as average
        FROM mood_entries
        GROUP BY date(date/1000, 'unixepoch')
        ORDER BY period
        """
    )
    suspend fun getAveragePerDay(): List<PeriodAverage>

    // Promedio por semana (period -> 'YYYY-WW' where WW is ISO week number-ish)
    @Query(
        """
        SELECT 
          strftime('%Y-%W', date/1000, 'unixepoch') as period,
          AVG(moodLevel) as average
        FROM mood_entries
        GROUP BY strftime('%Y-%W', date/1000, 'unixepoch')
        ORDER BY period
        """
    )
    suspend fun getAveragePerWeek(): List<PeriodAverage>

    // Promedio por mes (period -> 'YYYY-MM')
    @Query(
        """
        SELECT 
          strftime('%Y-%m', date/1000, 'unixepoch') as period,
          AVG(moodLevel) as average
        FROM mood_entries
        GROUP BY strftime('%Y-%m', date/1000, 'unixepoch')
        ORDER BY period
        """
    )
    suspend fun getAveragePerMonth(): List<PeriodAverage>

    // Distribución por estado (para gráfico de pastel)
    @Query("""
        SELECT moodLevel as moodLevel, COUNT(*) as count
        FROM mood_entries
        GROUP BY moodLevel
    """)
    suspend fun getMoodCounts(): List<MoodCount>

    // Obtener último registro (útil para la lógica de notificaciones)
    @Query("SELECT * FROM mood_entries ORDER BY date DESC LIMIT 1")
    suspend fun getLastMood(): MoodEntry?
}
