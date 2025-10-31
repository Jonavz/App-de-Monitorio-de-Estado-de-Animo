package com.example.examenu2

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.examenu2.MoodViewModel

@Composable
fun RegisterScreen(vm: MoodViewModel) {
    var moodLevel by remember { mutableStateOf(2) }
    var activities by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Califica tu día", style = MaterialTheme.typography.titleLarge)

        Spacer(Modifier.height(16.dp))

        Slider(value = moodLevel.toFloat(), onValueChange = {
            moodLevel = it.toInt()
        }, valueRange = 1f..3f, steps = 1)

        Text("Estado: ${when(moodLevel){1->"Bajo";2->"Neutral";else->"Alto"}}")

        OutlinedTextField(value = activities, onValueChange = { activities = it }, label = { Text("Actividades") })
        OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notas") })

        Spacer(Modifier.height(20.dp))

        Button(onClick = {
            vm.insertMood(moodLevel, activities, notes)
        }) {
            Text("Guardar")
        }
    }
}
