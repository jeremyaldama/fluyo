package com.qolve.fluyo.presentation.events

import com.qolve.fluyo.data.SessionScopedCache
import com.qolve.fluyo.domain.model.MoneyAmount
import com.qolve.fluyo.domain.model.BadgeType
import com.qolve.fluyo.domain.repository.BadgeEventPublisher
import com.qolve.fluyo.domain.repository.SessionBoundary

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed interface AppEvent {
    data class ExpenseSaved(val amount: MoneyAmount, val source: Source) : AppEvent {
        enum class Source { MANUAL, OCR }
    }

    data class BadgeUnlocked(val typeWire: String) : AppEvent
}

/**
 * App-wide event queue for cross-screen toasts/snackbars.
 *
 * A Channel retains events emitted while MainShell is not composed (for example while the
 * manual/OCR route is on top) and delivers each event to exactly one collector. The former
 * replay-less SharedFlow silently discarded those events when no collector was active.
 */
@Singleton
class AppEvents @Inject constructor(
    private val sessionBoundary: SessionBoundary,
) : SessionScopedCache, BadgeEventPublisher {
    private val channel = Channel<AppEvent>(capacity = Channel.UNLIMITED)
    val events: Flow<AppEvent> = channel.receiveAsFlow()

    fun emit(event: AppEvent, expectedSessionEpoch: Long): Boolean =
        sessionBoundary.runIfCurrent(expectedSessionEpoch) {
            check(channel.trySend(event).isSuccess) { "App event queue is closed" }
        }

    override fun publishBadgeUnlocked(type: BadgeType, expectedSessionEpoch: Long) {
        emit(AppEvent.BadgeUnlocked(type.wire), expectedSessionEpoch)
    }

    override suspend fun clearForSignOut() {
        while (channel.tryReceive().isSuccess) {
            // Drain queued amounts/badges before another identity can collect them.
        }
    }
}
