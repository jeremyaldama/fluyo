package com.qolve.fluyo.presentation.util

import androidx.core.net.toUri

/** Runtime counterpart of the release-time public URL validator in app/build.gradle.kts. */
fun isSafeExternalHttpsUrl(raw: String): Boolean = runCatching {
    val uri = raw.toUri()
    raw.isNotBlank() &&
        raw == raw.trim() &&
        '<' !in raw &&
        '>' !in raw &&
        uri.isAbsolute &&
        uri.scheme.equals("https", ignoreCase = true) &&
        !uri.host.isNullOrBlank() &&
        uri.userInfo == null
}.getOrDefault(false)
