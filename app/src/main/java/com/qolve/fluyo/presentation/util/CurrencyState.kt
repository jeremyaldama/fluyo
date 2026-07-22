package com.qolve.fluyo.presentation.util

import com.qolve.fluyo.data.SessionScopedCache
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-wide holder for the active currency code (HU-11). Seeded from the signed-in user
 * on launch ([com.qolve.fluyo.presentation.navigation.RootViewModel]) and updated the
 * moment an empty account chooses it in Profile, so amounts re-render everywhere without an
 * app restart. This is a base denomination, not a display-only preference or FX conversion.
 * Defaults to "PEN".
 */
@Singleton
class CurrencyState @Inject constructor() : SessionScopedCache {
    private val _code = MutableStateFlow("PEN")
    val code: StateFlow<String> = _code.asStateFlow()

    fun set(code: String?) {
        _code.value = code?.takeIf { it.isNotBlank() } ?: "PEN"
    }

    override suspend fun clearForSignOut() {
        _code.value = "PEN"
    }
}
