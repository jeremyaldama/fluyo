package com.qolve.fluyo

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.qolve.fluyo.presentation.navigation.FluyoNavHost
import com.qolve.fluyo.presentation.theme.FluyoTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            enableEdgeToEdge()
            setContent {
                FluyoTheme {
                    FluyoNavHost()
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error initializing app", e)
            throw e // Re-throw to allow Android to handle the crash properly
        }
    }
}
