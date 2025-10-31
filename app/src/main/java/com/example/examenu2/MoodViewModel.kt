import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.examenu2.AppDatabase
import com.example.examenu2.MoodEntry
import com.example.examenu2.MoodCount
import com.example.examenu2.PeriodAverage
import com.example.examenu2.MoodRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MoodViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = MoodRepository(AppDatabase.getDatabase(app).moodDao())

    private val _moods = MutableStateFlow<List<MoodEntry>>(emptyList())
    val moods = _moods.asStateFlow()

    private val _averages = MutableStateFlow<List<PeriodAverage>>(emptyList())
    val averages = _averages.asStateFlow()

    private val _counts = MutableStateFlow<List<MoodCount>>(emptyList())
    val counts = _counts.asStateFlow()

    fun insertMood(moodLevel: Int, activities: String, notes: String?) {
        viewModelScope.launch {
            repo.insert(MoodEntry(moodLevel = moodLevel, activities = activities, notes = notes))
            refreshAll()
        }
    }

    fun refreshAll() {
        viewModelScope.launch {
            _moods.value = repo.getAll()
            _averages.value = repo.getAveragePerDay()
            _counts.value = repo.getMoodCounts()
        }
    }

    suspend fun getLastEntryTimestamp(): Long? {
        return repo.getLastMood()?.date
    }
}
