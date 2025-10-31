package com.example.examenu2

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.example.examenu2.MainScreen
import com.example.examenu2.MoodViewModel

class MainActivity : ComponentActivity() {
    private val vm: MoodViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MainScreen(vm)
        }
    }
}
