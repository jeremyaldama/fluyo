package com.qolve.fluyo.presentation.events

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed interface AppEvent {
    data class ExpenseSaved(val amount: Double, val source: Source) : AppEvent {
        enum class Source { MANUAL, OCR }
    }

    data class BadgeUnlocked(val typeWire: String) : AppEvent
}

/**
 * App-wide event bus for cross-screen toasts/snackbars. Backed by a small
 * replay-less SharedFlow so events fire-once even if no collector is active.
 */
@Singleton
class AppEvents @Inject constructor() {
    private val _events = MutableSharedFlow<AppEvent>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<AppEvent> = _events.asSharedFlow()

    fun emit(event: AppEvent) {
        _events.tryEmit(event)
    }
}
