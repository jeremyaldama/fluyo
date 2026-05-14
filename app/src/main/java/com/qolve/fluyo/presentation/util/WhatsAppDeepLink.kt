package com.qolve.fluyo.presentation.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * One-tap deep link into the Fluyo WhatsApp bot.
 *
 * **Why `wa.me`.** WhatsApp's documented click-to-chat URL works whether or not the
 * receiving number already exists in the user's contacts — it just opens the chat with
 * an optional pre-filled draft. Both the Android app and WhatsApp Web honor the same URL,
 * which means the same link works from desktop too if a tester is on the web client.
 *
 * **Why this matters for cost.** WhatsApp Cloud API distinguishes:
 *   • Service conversations — user sends first, 24h response window opens automatically.
 *     These are FREE in Peru (and most regions) since Meta dropped service-conversation
 *     pricing in April 2025.
 *   • Marketing / utility / authentication conversations — business sends first, require
 *     pre-approved templates, billed per conversation.
 *
 * Routing every new user through `wa.me` keeps every Fluyo session on the free path.
 *
 * The bot number is intentionally hardcoded in [FluyoBot.NUMBER_E164] — it's a single
 * production-line and tying it to BuildConfig would create per-build drift without
 * meaningful flexibility. When the number changes (e.g. you switch WhatsApp Business
 * accounts), update it here and ship a new build.
 */
object FluyoBot {
    /**
     * Fluyo WhatsApp Business number, E.164 without the leading "+".
     * Used both as a UI display value (after formatting) and as the wa.me path segment.
     */
    const val NUMBER_E164: String = "51901047888"

    /** Pretty-printed for chrome that wants to show the actual number. */
    const val NUMBER_HUMAN: String = "+51 901 047 888"
}

/**
 * Builds a `wa.me` URL targeting the Fluyo bot with the given pre-filled message.
 * Returns null only if the prefill encoder somehow fails — which it doesn't in practice
 * because [Uri.encode] handles every codepoint.
 */
fun fluyoWhatsAppUri(prefillText: String): Uri {
    val encoded = Uri.encode(prefillText)
    return Uri.parse("https://wa.me/${FluyoBot.NUMBER_E164}?text=$encoded")
}

/**
 * Opens WhatsApp at the Fluyo bot. Silently falls back to a toast if no app on the device
 * can handle the URL (no WhatsApp + no browser — extremely unlikely on a Peruvian phone,
 * but graceful handling beats a crash).
 */
fun Context.openFluyoOnWhatsApp(prefillText: String) {
    val intent = Intent(Intent.ACTION_VIEW, fluyoWhatsAppUri(prefillText)).apply {
        // FLAG_ACTIVITY_NEW_TASK lets us launch from non-Activity contexts (e.g. when this
        // helper is wired through a ViewModel that holds an applicationContext).
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    runCatching { startActivity(intent) }.onFailure {
        Toast.makeText(
            this,
            "Instala WhatsApp para conectar tu cuenta",
            Toast.LENGTH_LONG,
        ).show()
    }
}
