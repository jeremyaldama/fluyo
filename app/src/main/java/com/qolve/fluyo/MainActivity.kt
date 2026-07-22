package com.qolve.fluyo

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.qolve.fluyo.data.security.AuthCallbackPolicy
import com.qolve.fluyo.presentation.events.SharedImageEvents
import com.qolve.fluyo.presentation.navigation.FluyoNavHost
import com.qolve.fluyo.presentation.theme.FluyoTheme
import dagger.hilt.android.AndroidEntryPoint
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.handleDeeplinks
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var sharedImageEvents: SharedImageEvents
    @Inject lateinit var supabaseClient: SupabaseClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Financial balances, OCR receipts and authentication fields must not be copied
        // into screenshots, screen recordings or the Recents preview.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        consumeAuthCallbackIfAny(intent)
        consumeSharedImageIfAny(intent)
        setContent {
            FluyoTheme {
                FluyoNavHost()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        consumeAuthCallbackIfAny(intent)
        consumeSharedImageIfAny(intent)
        // Keep the latest non-sensitive intent for normal Activity semantics. An
        // authentication callback has already had its token-bearing URI scrubbed.
        setIntent(intent)
    }

    private fun consumeAuthCallbackIfAny(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return

        val callback = intent.data ?: return
        val targetsCallback =
            callback.scheme.equals(AuthCallbackPolicy.EXPECTED_SCHEME, ignoreCase = true) &&
                callback.host.equals(AuthCallbackPolicy.EXPECTED_HOST, ignoreCase = true)
        if (!targetsCallback) return

        try {
            val isSafePkceCallback = AuthCallbackPolicy.accepts(
                scheme = callback.scheme,
                host = callback.host,
                userInfo = callback.userInfo,
                port = callback.port,
                path = callback.path,
                fragment = callback.fragment,
                queryParameterNames = callback.queryParameterNames,
                authorizationCodes = callback.getQueryParameters("code"),
                error = callback.getQueryParameter("error"),
            )
            if (!isSafePkceCallback) {
                Log.w(TAG, "Rejected an invalid authentication callback")
                return
            }
            supabaseClient.handleDeeplinks(intent = intent)
        } catch (_: Exception) {
            // Never log the exception or URI: Supabase callbacks may contain an
            // access token, refresh token, OTP or PKCE authorization code.
            Log.w(TAG, "Rejected an invalid authentication callback")
        } finally {
            // Activity intents survive configuration changes and can appear in
            // diagnostics. Remove the sensitive URI as soon as it is consumed.
            intent.data = null
        }
    }

    private fun consumeSharedImageIfAny(intent: Intent?) {
        if (intent == null) return
        if (intent.action != Intent.ACTION_SEND) return
        if (intent.type?.startsWith("image/") != true) return

        val streamUri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
        }
        val uri = streamUri ?: intent.clipData?.getItemAt(0)?.uri ?: intent.data
        uri?.let { sharedImageEvents.emit(it) }

        // Do not retain a receipt URI in the Activity's base intent across recreation or
        // expose it through diagnostics after handing the one-shot event to navigation.
        intent.removeExtra(Intent.EXTRA_STREAM)
        intent.clipData = null
        intent.data = null
        intent.type = null
        intent.action = null
    }

    private companion object {
        const val TAG = "MainActivity"
    }
}
