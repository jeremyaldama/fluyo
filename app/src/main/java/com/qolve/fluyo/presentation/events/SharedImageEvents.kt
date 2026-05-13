package com.qolve.fluyo.presentation.events

import android.net.Uri
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One-shot bus for images shared into Fluyo from other apps (Yape, Plin,
 * Gallery, camera roll) via the system Share sheet.
 *
 * MainActivity emits when it receives `Intent.ACTION_SEND` with `image/*`.
 * FluyoNavHost subscribes once and navigates to the OCR confirm screen — but
 * only when the user is signed in + onboarded. Otherwise the event is dropped
 * (auth-gating before scan).
 */
@Singleton
class SharedImageEvents @Inject constructor() {
    private val _events = MutableSharedFlow<Uri>(
        replay = 1, // hold latest so late subscribers (cold-start case) still see it
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<Uri> = _events.asSharedFlow()

    fun emit(uri: Uri) {
        _events.tryEmit(uri)
    }

    fun consume() {
        _events.resetReplayCache()
    }
}
