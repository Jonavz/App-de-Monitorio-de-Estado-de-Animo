package com.example.examenu2

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.examenu2.MoodViewModel
import kotlinx.coroutines.launch

@Composable
fun AnalysisScreen(vm: MoodViewModel) {
    val scope = rememberCoroutineScope()
    var moods by remember { mutableStateOf(emptyList<com.example.examenu2.MoodEntry>()) }

    LaunchedEffect(Unit) {
        scope.launch {
            vm.loadMoods()
        }
    }

    moods = vm.moods.collectAsState().value

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Registros guardados: ${moods.size}")
        Spacer(Modifier.height(10.dp))
        LazyColumn {
            items(moods.size) { i ->
                val m = moods[i]
                Text("• ${m.activities} — Nivel: ${m.moodLevel} (${m.notes ?: "sin notas"})")
            }
        }
    }
}
