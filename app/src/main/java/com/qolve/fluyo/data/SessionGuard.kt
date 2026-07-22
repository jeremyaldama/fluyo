package com.qolve.fluyo.data

import com.qolve.fluyo.domain.repository.SessionBoundary
import kotlinx.coroutines.CancellationException

/** Cancellation used when a response belongs to an identity that is no longer active. */
class StaleSessionException : CancellationException("Session changed while work was in flight")

fun SessionBoundary.requireCurrent(expectedEpoch: Long) {
    if (!isCurrent(expectedEpoch)) throw StaleSessionException()
}

fun SessionBoundary.publishIfCurrent(expectedEpoch: Long, action: () -> Unit) {
    if (!runIfCurrent(expectedEpoch, action)) throw StaleSessionException()
}
