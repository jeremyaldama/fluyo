package com.qolve.fluyo.presentation.util

import androidx.lifecycle.SavedStateHandle
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.UUID

/**
 * Persists an idempotency key for one logical mutation across retries and process recreation.
 * Payload values are stored only as a SHA-256 fingerprint, never as financial/user input.
 */
internal class StableMutationRequestStore(
    private val state: SavedStateHandle,
    namespace: String,
    private val newRequestId: () -> String = { UUID.randomUUID().toString() },
) {
    private val requestIdKey = "${namespace}_request_id"
    private val fingerprintKey = "${namespace}_payload_fingerprint"

    fun getOrCreate(vararg payload: String?): String {
        val fingerprint = payloadFingerprint(payload.asList())
        existingForFingerprint(fingerprint)?.let { return it }

        check(pendingRequestId() == null) {
            "A different pending mutation must be reconciled before creating another request id"
        }

        return newRequestId().also { requestId ->
            require(requestId.isNotBlank()) { "Request id must not be blank" }
            state[fingerprintKey] = fingerprint
            state[requestIdKey] = requestId
        }
    }

    /** Returns a pending key only when it belongs to this exact logical payload. */
    fun existing(vararg payload: String?): String? =
        existingForFingerprint(payloadFingerprint(payload.asList()))

    /** Opaque key for a prior uncertain attempt; its plaintext payload is never persisted. */
    fun pendingRequestId(): String? = state.get<String>(requestIdKey)?.takeIf(String::isNotBlank)

    fun complete(requestId: String) {
        if (state.get<String>(requestIdKey) == requestId) clear()
    }

    /** Clears an uncertain key only after the server proved that it never committed. */
    private fun replaceAfterConfirmedAbsent(
        pendingRequestId: String,
        payload: Array<out String?>,
    ): String {
        check(state.get<String>(requestIdKey) == pendingRequestId) {
            "Pending mutation changed during reconciliation"
        }
        clear()
        return getOrCreate(*payload)
    }

    /**
     * Resolves a changed/restored form without storing its financial or personal fields.
     * An exact retry goes directly to the idempotent RPC. If the payload changed, the old
     * request id is looked up first: a committed row wins; a new key is minted only after
     * the server confirms that no row exists. Lookup failures fail closed.
     */
    suspend fun <T : Any> resolve(
        vararg payload: String?,
        findCommitted: suspend (requestId: String) -> Result<T?>,
    ): Result<PendingMutationResolution<T>> {
        existing(*payload)?.let { requestId ->
            return Result.success(PendingMutationResolution.Ready(requestId))
        }
        val pendingId = pendingRequestId()
            ?: return Result.success(PendingMutationResolution.Ready(getOrCreate(*payload)))

        return findCommitted(pendingId).map { committed ->
            if (committed != null) {
                complete(pendingId)
                PendingMutationResolution.Committed(committed)
            } else {
                PendingMutationResolution.Ready(
                    replaceAfterConfirmedAbsent(pendingId, payload),
                )
            }
        }
    }

    private fun clear() {
        state.remove<String>(fingerprintKey)
        state.remove<String>(requestIdKey)
    }

    private fun existingForFingerprint(fingerprint: String): String? {
        val existingId = state.get<String>(requestIdKey)
        return existingId?.takeIf { requestId ->
            requestId.isNotBlank() && state.get<String>(fingerprintKey) == fingerprint
        }
    }
}

internal sealed interface PendingMutationResolution<out T : Any> {
    data class Ready(val requestId: String) : PendingMutationResolution<Nothing>
    data class Committed<T : Any>(val value: T) : PendingMutationResolution<T>
}

/** Length-prefixing makes distinct component boundaries and null values unambiguous. */
internal fun payloadFingerprint(payload: List<String?>): String {
    val digest = MessageDigest.getInstance("SHA-256")
    payload.forEach { component ->
        if (component == null) {
            digest.update(0.toByte())
        } else {
            val bytes = component.toByteArray(Charsets.UTF_8)
            digest.update(1.toByte())
            digest.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(bytes.size).array())
            digest.update(bytes)
        }
    }
    return digest.digest().joinToString(separator = "") { byte ->
        (byte.toInt() and 0xff).toString(16).padStart(2, '0')
    }
}
