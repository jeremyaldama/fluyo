package com.qolve.fluyo.domain

import kotlin.coroutines.cancellation.CancellationException

/**
 * Coroutine-safe counterpart to [runCatching].
 *
 * [runCatching] catches every [Throwable], including [CancellationException]. Turning a
 * cancellation signal into a failed [Result] breaks structured concurrency and lets work
 * continue after its owning scope has been cancelled. This helper preserves the existing
 * Result-based API while always propagating cancellation to the caller.
 */
suspend inline fun <T> suspendRunCatching(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (failure: Throwable) {
    Result.failure(failure)
}
