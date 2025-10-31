package com.example.examenu2

import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.example.examenu2.MoodViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: MoodViewModel) {
    var selectedTab by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Registrar") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Análisis") })
            }
        }
    ) {
        when (selectedTab) {
            0 -> RegisterScreen(vm)
            1 -> AnalysisScreen(vm)
        }
    }
}
