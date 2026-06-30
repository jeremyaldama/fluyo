package com.qolve.fluyo.presentation.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-wide holder for the active currency code (HU-11). Seeded from the signed-in user
 * on launch ([com.qolve.fluyo.presentation.navigation.RootViewModel]) and updated the
 * moment the user changes it in Profile, so amounts re-render everywhere without an app
 * restart. Defaults to "PEN".
 */
@Singleton
class CurrencyState @Inject constructor() {
    private val _code = MutableStateFlow("PEN")
    val code: StateFlow<String> = _code.asStateFlow()

    fun set(code: String?) {
        _code.value = code?.takeIf { it.isNotBlank() } ?: "PEN"
    }
}
