package com.qolve.fluyo

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.qolve.fluyo.presentation.events.SharedImageEvents
import com.qolve.fluyo.presentation.navigation.FluyoNavHost
import com.qolve.fluyo.presentation.theme.FluyoTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var sharedImageEvents: SharedImageEvents

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            enableEdgeToEdge()
            consumeSharedImageIfAny(intent)
            setContent {
                FluyoTheme {
                    FluyoNavHost()
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error initializing app", e)
            throw e
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeSharedImageIfAny(intent)
    }

    private fun consumeSharedImageIfAny(intent: Intent?) {
        if (intent == null) return
        if (intent.action != Intent.ACTION_SEND) return
        if (intent.type?.startsWith("image/") != true) return

        val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
        }
        uri?.let { sharedImageEvents.emit(it) }
    }
}
