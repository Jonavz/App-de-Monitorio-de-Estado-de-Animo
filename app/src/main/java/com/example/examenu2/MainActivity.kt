package com.example.examenu2

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.room.*
import androidx.work.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.IsoFields
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton

// --- CLASE APPLICATION ---
class MoodTrackerApp : Application() {
    val database: MoodDatabase by lazy { MoodDatabase.getDatabase(this) }
    val repository: MoodRepository by lazy { MoodRepository(database.moodDao()) }

    override fun onCreate() {
        super.onCreate()
        scheduleReminder(this)
    }
}

// --- BASE DE DATOS (ROOM) ---


class Converters {
    @TypeConverter
    fun fromStringList(value: List<String>): String {
        return Gson().toJson(value)
    }

    @TypeConverter
    fun toStringList(value: String): List<String> {
        val listType = object : TypeToken<List<String>>() {}.type
        return Gson().fromJson(value, listType)
    }
}

// 2. Entity
@Entity(tableName = "mood_entries")
data class MoodEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long,
    val moodRating: Int, // 1 (Muy Malo) a 5 (Muy Bueno)
    val tags: List<String>,
    val notes: String?
)

// 3. DAO
@Dao
interface MoodDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: MoodEntry)

    @Query("SELECT * FROM mood_entries ORDER BY timestamp DESC")
    fun getAllEntries(): Flow<List<MoodEntry>>

    @Query("SELECT * FROM mood_entries ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestEntry(): MoodEntry?
}

// 4. Database
@Database(entities = [MoodEntry::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class MoodDatabase : RoomDatabase() {
    abstract fun moodDao(): MoodDao

    companion object {
        @Volatile
        private var INSTANCE: MoodDatabase? = null

        fun getDatabase(context: Context): MoodDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MoodDatabase::class.java,
                    "mood_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// --- REPOSITORIO ---
class MoodRepository(private val moodDao: MoodDao) {
    val allEntries: Flow<List<MoodEntry>> = moodDao.getAllEntries()

    suspend fun insert(entry: MoodEntry) {
        moodDao.insertEntry(entry)
    }

    suspend fun getLatestEntry(): MoodEntry? {
        return moodDao.getLatestEntry()
    }
}

// --- VIEWMODEL ---
class MoodViewModel(private val repository: MoodRepository) : ViewModel() {

    val allEntries = repository.allEntries.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList()
    )

    fun addMoodEntry(rating: Int, tags: List<String>, notes: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            val entry = MoodEntry(
                timestamp = System.currentTimeMillis(),
                moodRating = rating,
                tags = tags,
                notes = notes
            )
            repository.insert(entry)
        }
    }
}

// Factory para el ViewModel
class MoodViewModelFactory(private val repository: MoodRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MoodViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MoodViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}


const val NOTIFICATION_CHANNEL_ID = "mood_reminder_channel"
const val WORK_TAG = "moodReminderWork"
const val REMINDER_INTERVAL_HOURS = 18L


class MoodReminderWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val repository = (context.applicationContext as MoodTrackerApp).repository

        return withContext(Dispatchers.IO) {
            val latestEntry = repository.getLatestEntry()
            val currentTime = System.currentTimeMillis()

            val demoTimeLimit = TimeUnit.SECONDS.toMillis(15) // 15 SEGUNDOS para demo
            val timeLimit = demoTimeLimit

            val needsNotification = if (latestEntry == null) {
                true
            } else {
                (currentTime - latestEntry.timestamp) > timeLimit
            }

            if (needsNotification) {
                sendNotification(context)
            }

            Result.success()
        }
    }
}

fun sendNotification(context: Context) {
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Recordatorio de Ánimo",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "¿Cómo te sientes hoy?"
        }
        notificationManager.createNotificationChannel(channel)
    }

    val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle("Registro de Ánimo")
        .setContentText("No has registrado tu estado de ánimo hoy. ¡Tómate un minuto!")
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setAutoCancel(true)
        .build()

    notificationManager.notify(1, notification)
}

fun scheduleReminder(context: Context) {
    val workRequest = PeriodicWorkRequestBuilder<MoodReminderWorker>(
        15, TimeUnit.MINUTES // El mínimo permitido por Android
    )
        .build()

    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        WORK_TAG,
        ExistingPeriodicWorkPolicy.KEEP,
        workRequest
    )
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            scheduleReminder(context)
        }
    }
}


// --- INTERFAZ DE USUARIO (COMPOSE) ---

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RequestNotificationPermission()

            val repository = (application as MoodTrackerApp).repository
            val viewModel: MoodViewModel = viewModel(
                factory = MoodViewModelFactory(repository)
            )

            MoodTrackerTheme {
                MainAppScreen(viewModel)
            }
        }
    }
}

@Composable
fun RequestNotificationPermission() {
    val context = LocalContext.current
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { /* isGranted: Boolean -> ... */ }

        LaunchedEffect(key1 = true) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Register : Screen("register", "Registrar", Icons.Default.Create)
    object Analysis : Screen("analysis", "Análisis", Icons.Default.Analytics)
}

val bottomNavItems = listOf(Screen.Register, Screen.Analysis)

@Composable
fun MainAppScreen(viewModel: MoodViewModel) {
    val navController = rememberNavController()
    Scaffold(
        bottomBar = { AppBottomNavigation(navController = navController) }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            AppNavHost(navController = navController, viewModel = viewModel)
        }
    }
}

@Composable
fun AppNavHost(navController: NavHostController, viewModel: MoodViewModel) {
    NavHost(navController = navController, startDestination = Screen.Register.route) {
        composable(Screen.Register.route) {
            RegisterScreen(viewModel = viewModel)
        }
        composable(Screen.Analysis.route) {
            AnalysisScreen(viewModel = viewModel)
        }
    }
}

@Composable
fun AppBottomNavigation(navController: NavController) {
    NavigationBar {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route

        bottomNavItems.forEach { screen ->
            NavigationBarItem(
                icon = { Icon(screen.icon, contentDescription = screen.label) },
                label = { Text(screen.label) },
                selected = currentRoute == screen.route,
                onClick = {
                    if (currentRoute != screen.route) {
                        navController.navigate(screen.route) {
                            popUpTo(navController.graph.startDestinationId)
                            launchSingleTop = true
                        }
                    }
                }
            )
        }
    }
}

// --- Pantalla de Registro ---

val predefinedTags = mapOf(
    "Actividades" to listOf("Salir de fiesta", "Ir al cine", "Salir a correr", "Trabajar", "Estudiar"),
    "Lugares" to listOf("Parque", "Plaza", "Centro comercial", "Casa", "Oficina")
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RegisterScreen(viewModel: MoodViewModel) {
    var moodRating by remember { mutableStateOf(3f) } // 1 a 5
    var notes by remember { mutableStateOf("") }
    var selectedPredefinedTags by remember { mutableStateOf<Set<String>>(emptySet()) }

    var newCustomTag by remember { mutableStateOf("") }
    var customTagType by remember { mutableStateOf("Actividad") } // "Actividad" o "Lugar"
    val tagTypes = listOf("Actividad", "Lugar")
    var customActivityTags by remember { mutableStateOf<Set<String>>(emptySet()) }
    var customPlaceTags by remember { mutableStateOf<Set<String>>(emptySet()) }

    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        item {
            Text(
                "¿Cómo te sientes hoy?",
                style = MaterialTheme.typography.headlineMedium
            )
        }

        item {
            val moodEmoji = when (moodRating.roundToInt()) {
                1 -> "😞"; 2 -> "😕"; 3 -> "😐"; 4 -> "🙂"; 5 -> "😄"; else -> "😐"
            }
            Text(moodEmoji, fontSize = 64.sp)
            Slider(
                value = moodRating,
                onValueChange = { moodRating = it },
                valueRange = 1f..5f,
                steps = 3
            )
        }

        item {
            Text(
                "¿Qué hiciste o dónde estuviste?",
                style = MaterialTheme.typography.titleMedium
            )
        }

        // Etiquetas predefinidas
        predefinedTags.forEach { (category, tags) ->
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(category, style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        tags.forEach { tag ->
                            FilterChip(
                                selected = tag in selectedPredefinedTags,
                                onClick = {
                                    selectedPredefinedTags = if (tag in selectedPredefinedTags) {
                                        selectedPredefinedTags - tag
                                    } else {
                                        selectedPredefinedTags + tag
                                    }
                                },
                                label = { Text(tag) }
                            )
                        }
                    }
                }
            }
        }

        item {
            Text(
                "Añadir etiquetas personalizadas",
                style = MaterialTheme.typography.titleMedium
            )
        }

        item {
            // --- REEMPLAZO PARA 'SegmentedButton' ---
            // Usamos un Row simple con OutlinedButton para evitar el error 'shape'
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tagTypes.forEach { label ->
                    val isSelected = label == customTagType
                    OutlinedButton(
                        onClick = { customTagType = label },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Text(label)
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = newCustomTag,
                    onValueChange = { newCustomTag = it },
                    label = { Text("Nueva $customTagType") },
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        if (newCustomTag.isNotBlank()) {
                            if (customTagType == "Actividad") {
                                customActivityTags = customActivityTags + newCustomTag.trim()
                            } else {
                                customPlaceTags = customPlaceTags + newCustomTag.trim()
                            }
                            newCustomTag = ""
                        }
                    },
                    enabled = newCustomTag.isNotBlank()
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Añadir etiqueta")
                }
            }
        }

        if (customActivityTags.isNotEmpty()) {
            item {
                TagChipList(
                    title = "Actividades Personalizadas:",
                    tags = customActivityTags,
                    onRemove = { tag -> customActivityTags = customActivityTags - tag }
                )
            }
        }
        if (customPlaceTags.isNotEmpty()) {
            item {
                TagChipList(
                    title = "Lugares Personalizados:",
                    tags = customPlaceTags,
                    onRemove = { tag -> customPlaceTags = customPlaceTags - tag }
                )
            }
        }

        item {
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notas adicionales...") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            Button(
                onClick = {
                    val allTags = selectedPredefinedTags + customActivityTags + customPlaceTags

                    viewModel.addMoodEntry(
                        rating = moodRating.roundToInt(),
                        tags = allTags.toList(),
                        notes = notes.takeIf { it.isNotBlank() }
                    )

                    // Resetear todo
                    moodRating = 3f
                    notes = ""
                    selectedPredefinedTags = emptySet()
                    customActivityTags = emptySet()
                    customPlaceTags = emptySet()
                    newCustomTag = ""

                    android.widget.Toast.makeText(context, "¡Guardado!", android.widget.Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Guardar Día")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TagChipList(title: String, tags: Set<String>, onRemove: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title, style = MaterialTheme.typography.labelMedium)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            tags.forEach { tag ->
                InputChip(
                    selected = true,
                    onClick = { /* Se elimina con el icono */ },
                    label = { Text(tag) },
                    trailingIcon = {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Eliminar etiqueta",
                            modifier = Modifier
                                .size(InputChipDefaults.IconSize)
                                .clickable { onRemove(tag) }
                        )
                    }
                )
            }
        }
    }
}
// --- Pantalla de Análisis ---

val moodColors = listOf(
    Color(0xFFD32F2F), // 1 (Muy Malo)
    Color(0xFFF57C00), // 2 (Malo)
    Color(0xFFFBC02D), // 3 (Neutral)
    Color(0xFF7CB342), // 4 (Bueno)
    Color(0xFF43A047)  // 5 (Muy Bueno)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisScreen(viewModel: MoodViewModel) {
    val entries by viewModel.allEntries.collectAsState()

    var selectedBarChartPeriod by remember { mutableStateOf("Día") }
    val barChartPeriods = listOf("Día", "Semana", "Mes")

    if (entries.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("No hay datos para analizar todavía.")
        }
        return
    }

    // --- LÓGICA DE PROCESAMIENTO DE DATOS ---
    val zoneId = ZoneId.systemDefault()

    val moodDistribution = entries
        .groupBy { it.moodRating }
        .mapValues { it.value.size }
        .toSortedMap()

    val barChartData: List<ChartData> = remember(entries, selectedBarChartPeriod) {
        val groupedData: Map<String, List<MoodEntry>> = when (selectedBarChartPeriod) {

            "Día" -> {
                val dayFormatter = DateTimeFormatter.ofPattern("dd MMM")
                entries
                    .groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zoneId).toLocalDate() }
                    .entries
                    .sortedBy { it.key } // Ordenar por fecha
                    .takeLast(7) // Tomar los últimos 7
                    .associate { it.key.format(dayFormatter) to it.value } // Formatear
            }
            "Semana" -> {
                entries.groupBy {
                    val date = Instant.ofEpochMilli(it.timestamp).atZone(zoneId).toLocalDate()
                    val weekNumber = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
                    val year = date.year
                    "Sem $weekNumber, $year"
                }
                    .entries
                    .sortedBy { (key) -> key.substringAfterLast(" ").toInt() * 100 + key.substringAfter(" ").substringBefore(",").toInt() }
                    .takeLast(6)
                    .associate { it.toPair() }
            }
            "Mes" -> {
                val monthFormatter = DateTimeFormatter.ofPattern("MMM yyyy")
                entries.groupBy {
                    Instant.ofEpochMilli(it.timestamp).atZone(zoneId).toLocalDate()
                        .format(monthFormatter)
                }
                    .entries
                    .sortedBy { (key) -> LocalDate.parse("01 $key", DateTimeFormatter.ofPattern("dd MMM yyyy")) }
                    .takeLast(6)
                    .associate { it.toPair() }
            }
            else -> emptyMap()
        }

        // Calcular promedios
        groupedData.map { (label, entries) ->
            val average = entries.map { it.moodRating }.average().toFloat()
            ChartData(label = label, average = average)
        }
    }
    // --- FIN LÓGICA DE DATOS ---


    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {

        item {
            Text("Distribución de Ánimo", style = MaterialTheme.typography.headlineMedium)
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Resumen General", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(16.dp))
                    MoodPieChart(data = moodDistribution)
                }
            }
        }

        item {
            Text("Promedio de Ánimo", style = MaterialTheme.typography.headlineMedium)
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        barChartPeriods.forEach { label ->
                            val isSelected = label == selectedBarChartPeriod
                            OutlinedButton(
                                onClick = { selectedBarChartPeriod = label },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    // Cambia el color si está seleccionado
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                Text(label)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    MoodBarChart(data = barChartData)
                }
            }
        }
        item {
            Text("Historial de Etiquetas", style = MaterialTheme.typography.headlineMedium)
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Etiquetas más usadas", style = MaterialTheme.typography.titleLarge)
                    val tagCounts = entries.flatMap { it.tags }
                        .groupingBy { it }
                        .eachCount()
                        .entries
                        .sortedByDescending { it.value }
                        .take(5)

                    if (tagCounts.isEmpty()) {
                        Text("Sin etiquetas registradas.")
                    } else {
                        tagCounts.forEach { (tag, count) ->
                            Text("· $tag: $count veces", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

// --- Componentes de Gráficos ---

/**
 * Gráfico de pastel simulado.
 */
@Composable
fun MoodPieChart(data: Map<Int, Int>) {
    val total = data.values.sum().toFloat()
    if (total == 0f) return

    val legend = mapOf(
        1 to "Muy Malo",
        2 to "Malo",
        3 to "Neutral",
        4 to "Bueno",
        5 to "Muy Bueno"
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .background(Color.Gray, shape = RoundedCornerShape(8.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            data.forEach { (rating, count) ->
                val weight = count / total
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(weight)
                        .background(moodColors[rating - 1])
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        legend.forEach { (rating, label) ->
            val percentage = (data.getOrDefault(rating, 0) / total) * 100
            if (percentage > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(moodColors[rating - 1], shape = CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "$label: ${percentage.roundToInt()}%",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

// Data class para el gráfico de barras
data class ChartData(val label: String, val average: Float)

/**
 * Gráfico de barras que acepta datos pre-calculados (ChartData).
 */
@Composable
fun MoodBarChart(data: List<ChartData>) {
    if (data.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("No hay datos para este periodo.")
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp),
        verticalArrangement = Arrangement.Bottom
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.LightGray.copy(alpha = 0.1f))
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val barCount = data.size
                if (barCount == 0) return@Canvas

                val barSpacing = 16.dp.toPx()
                val barWidth = (size.width - (barCount - 1) * barSpacing) / barCount
                val maxVal = 5f // Máximo es 5

                data.forEachIndexed { index, entry ->
                    val barHeight = (entry.average / maxVal) * size.height
                    val colorIndex = (entry.average.roundToInt() - 1).coerceIn(0, 4)
                    val color = moodColors[colorIndex]

                    drawRect(
                        color = color,
                        topLeft = Offset(
                            x = index * (barWidth + barSpacing),
                            y = size.height - barHeight
                        ),
                        size = Size(width = barWidth, height = barHeight)
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            data.forEach {
                Text(
                    text = it.label,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}


// --- Tema (Básico) ---
@Composable
fun MoodTrackerTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = darkColorScheme(), // O lightColorScheme()
        typography = Typography(),
        content = content
    )
}