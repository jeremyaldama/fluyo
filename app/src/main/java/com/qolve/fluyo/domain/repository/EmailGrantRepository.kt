package com.qolve.fluyo.domain.repository

/**
 * Reads the user's linked Gmail account for receipt auto-import. A null return
 * means no Gmail is linked yet. The write path (storing the OAuth grant) happens
 * server-side in the `gmail-connect` Edge Function; the app only ever reads.
 */
interface EmailGrantRepository {
    /** The linked Gmail address, or null if the user hasn't linked one. */
    suspend fun linkedEmail(): String?
}
