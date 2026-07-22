package com.qolve.fluyo.presentation.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.net.toUri
import com.qolve.fluyo.BuildConfig
import com.qolve.fluyo.R

/**
 * One-tap deep link into the Fluyo WhatsApp bot.
 *
 * **Why `wa.me`.** WhatsApp's documented click-to-chat URL works whether or not the
 * receiving number already exists in the user's contacts — it just opens the chat with
 * an optional pre-filled draft. Both the Android app and WhatsApp Web honor the same URL,
 * which means the same link works from desktop too if a tester is on the web client.
 *
 * The destination is supplied at build time and the whole flow is disabled by default.
 * This prevents a release from advertising an absent or unverified webhook backend.
 */
private val E164_DIGITS = Regex("^[1-9][0-9]{7,14}$")

/**
 * Builds a `wa.me` URL targeting the Fluyo bot with the given pre-filled message.
 * Returns null only if the prefill encoder somehow fails — which it doesn't in practice
 * because [Uri.encode] handles every codepoint.
 */
fun fluyoWhatsAppUri(prefillText: String): Uri? {
    val number = BuildConfig.WHATSAPP_BOT_NUMBER
        .takeIf { BuildConfig.WHATSAPP_LINKING_ENABLED && E164_DIGITS.matches(it) }
        ?: return null
    val encoded = Uri.encode(prefillText)
    return "https://wa.me/$number?text=$encoded".toUri()
}

/**
 * Opens WhatsApp at the Fluyo bot. Silently falls back to a toast if no app on the device
 * can handle the URL (no WhatsApp + no browser — extremely unlikely on a Peruvian phone,
 * but graceful handling beats a crash).
 */
fun Context.openFluyoOnWhatsApp(prefillText: String) {
    val destination = fluyoWhatsAppUri(prefillText)
    if (destination == null) {
        Toast.makeText(this, R.string.whatsapp_unavailable, Toast.LENGTH_LONG).show()
        return
    }
    val intent = Intent(Intent.ACTION_VIEW, destination).apply {
        // FLAG_ACTIVITY_NEW_TASK lets us launch from non-Activity contexts (e.g. when this
        // helper is wired through a ViewModel that holds an applicationContext).
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    runCatching { startActivity(intent) }.onFailure {
        Toast.makeText(
            this,
            R.string.whatsapp_unavailable,
            Toast.LENGTH_LONG,
        ).show()
    }
}
