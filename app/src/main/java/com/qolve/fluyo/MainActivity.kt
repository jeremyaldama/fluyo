package com.qolve.fluyo

import android.os.Bundle
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
        enableEdgeToEdge()
        setContent {
            FluyoTheme {
                FluyoNavHost()
            }
        }
    }
}
