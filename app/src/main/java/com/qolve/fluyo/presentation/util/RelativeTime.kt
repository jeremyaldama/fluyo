package com.qolve.fluyo.presentation.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.qolve.fluyo.R
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Localized "hace X" formatter for an instant in the past.
 *
 * Buckets:
 *   • < 1 minute → "ahora"
 *   • 1–59 minutes → "hace N min"
 *   • 1–23 hours → "hace N h"
 *   • ≥ 1 day → "hace N d"
 *
 * Intentionally coarse — Movimientos cards don't need second-precision and rounding makes
 * the text more readable. For future dates (clock skew on a freshly-installed device, for
 * example) we fall back to "ahora" rather than show negative spans.
 *
 * **Why @Composable.** This reads from `stringResource` so it follows the locale of the
 * composition. Use it inside the same composition that renders the row.
 */
@Composable
fun relativeTimeFrom(instant: Instant, now: Instant = Instant.now()): String {
    if (instant.isAfter(now)) return stringResource(R.string.rel_time_now)
    val seconds = ChronoUnit.SECONDS.between(instant, now)
    return when {
        seconds < 60 -> stringResource(R.string.rel_time_now)
        seconds < 3600 -> stringResource(R.string.rel_time_minutes, (seconds / 60).toInt())
        seconds < 86_400 -> stringResource(R.string.rel_time_hours, (seconds / 3600).toInt())
        else -> stringResource(R.string.rel_time_days, (seconds / 86_400).toInt())
    }
}
