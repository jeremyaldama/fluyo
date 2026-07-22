package com.qolve.fluyo.presentation.util

/** One-shot deep-link payload whose debug output never exposes the challenge token. */
class WhatsAppLaunchRequest(val message: String) {
    override fun toString(): String = "WhatsAppLaunchRequest(message=<redacted>)"
}
