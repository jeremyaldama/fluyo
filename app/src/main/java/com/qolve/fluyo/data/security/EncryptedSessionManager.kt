package com.qolve.fluyo.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Persists Supabase sessions encrypted with a non-exportable Android Keystore key.
 *
 * The ciphertext lives under [Context.getNoBackupFilesDir], is bound to this package via
 * AES-GCM associated data and is written through a temporary file. A legacy manager can
 * be supplied once to migrate the plaintext session used by supabase-kt's default
 * SettingsSessionManager; the legacy value is deleted immediately after migration.
 */
class EncryptedSessionManager(
    private val context: Context,
    private val legacyManager: SessionManager? = null,
) : SessionManager {

    private val mutex = Mutex()
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }
    private val sessionFile = File(context.noBackupFilesDir, SESSION_FILE_NAME)
    private val tempFile = File(context.noBackupFilesDir, "$SESSION_FILE_NAME.tmp")
    private val legacyDisabledFile = File(context.noBackupFilesDir, LEGACY_DISABLED_FILE_NAME)
    private val legacyDisabledTempFile =
        File(context.noBackupFilesDir, "$LEGACY_DISABLED_FILE_NAME.tmp")
    private val associatedData = "${context.packageName}:supabase-session:v1".encodeToByteArray()

    override suspend fun saveSession(session: UserSession) = mutex.withLock {
        withContext(Dispatchers.IO) {
            saveEncrypted(json.encodeToString(session).encodeToByteArray())
            disableLegacyFallback()
        }
    }

    override suspend fun loadSession(): UserSession? = mutex.withLock {
        // Any encrypted artifact proves that this installation crossed the migration
        // boundary. Persist that fact before parsing so corrupt ciphertext cannot cause
        // a downgrade to a stale plaintext refresh token.
        val encryptedArtifactExists = withContext(Dispatchers.IO) {
            sessionFile.exists() || tempFile.exists()
        }
        if (encryptedArtifactExists) {
            withContext(Dispatchers.IO) { disableLegacyFallback() }
        }
        val encrypted = withContext(Dispatchers.IO) { loadEncryptedOrNull() }
        if (encrypted != null) {
            val decoded = try {
                json.decodeFromString<UserSession>(encrypted.decodeToString())
            } catch (_: Exception) {
                withContext(Dispatchers.IO) { invalidateEncryptedState(strict = false) }
                null
            }
            if (decoded != null) deleteLegacyBestEffort()
            return@withLock decoded
        }

        if (withContext(Dispatchers.IO) { legacyDisabledFile.exists() }) {
            return@withLock null
        }

        // One-time migration from supabase-kt's plaintext SettingsSessionManager.
        val legacy = legacyManager?.loadSession() ?: return@withLock null
        withContext(Dispatchers.IO) {
            saveEncrypted(json.encodeToString(legacy).encodeToByteArray())
            disableLegacyFallback()
        }
        deleteLegacyBestEffort()
        legacy
    }

    override suspend fun deleteSession() = mutex.withLock {
        var firstFailure: Exception? = null
        try {
            // Tombstone first: even if legacy Settings deletion fails, a later process
            // must never resurrect the refresh token that sign-out tried to invalidate.
            withContext(Dispatchers.IO) { disableLegacyFallback() }
        } catch (failure: Exception) {
            firstFailure = failure
        }
        try {
            // Delete the non-exportable key as well as the ciphertext. Even if the
            // filesystem refuses deletion, the persisted refresh token becomes
            // cryptographically unrecoverable by this installation.
            withContext(Dispatchers.IO) { invalidateEncryptedState(strict = true) }
        } catch (failure: Exception) {
            firstFailure = failure
        }
        try {
            legacyManager?.deleteSession()
        } catch (failure: Exception) {
            if (firstFailure == null) firstFailure = failure else firstFailure.addSuppressed(failure)
        }
        firstFailure?.let { throw it }
        Unit
    }

    private suspend fun deleteLegacyBestEffort() {
        try {
            legacyManager?.deleteSession()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // A later successful encrypted load retries removal, closing a crash/I/O
            // window without making the valid encrypted session unavailable.
        }
    }

    private fun saveEncrypted(plaintext: ByteArray) {
        require(plaintext.size <= MAX_SESSION_BYTES) { "Session payload is unexpectedly large" }
        val encrypted = encrypt(plaintext, retryAfterKeyReset = true)
        tempFile.outputStream().use { stream ->
            stream.write(FORMAT_VERSION)
            stream.write(encrypted.iv.size)
            stream.write(encrypted.iv)
            stream.write(encrypted.ciphertext)
            stream.flush()
            stream.fd.sync()
        }

        if (sessionFile.exists() && !sessionFile.delete()) {
            tempFile.delete()
            error("Unable to replace encrypted session")
        }
        if (!tempFile.renameTo(sessionFile)) {
            tempFile.delete()
            error("Unable to persist encrypted session")
        }
    }

    private fun disableLegacyFallback() {
        if (legacyDisabledFile.isFile) return
        if (legacyDisabledFile.exists()) {
            error("Legacy-session tombstone path is not a file")
        }
        try {
            legacyDisabledTempFile.outputStream().use { stream ->
                stream.write(LEGACY_DISABLED_FORMAT)
                stream.flush()
                stream.fd.sync()
            }
            if (!legacyDisabledTempFile.renameTo(legacyDisabledFile)) {
                error("Unable to persist legacy-session tombstone")
            }
        } catch (failure: Exception) {
            legacyDisabledTempFile.delete()
            throw failure
        }
    }

    private fun loadEncryptedOrNull(): ByteArray? {
        if (!sessionFile.isFile) return null
        val bytes = runCatching { sessionFile.readBytes() }.getOrElse {
            invalidateEncryptedState(strict = false)
            return null
        }
        if (bytes.size !in MIN_FILE_BYTES..MAX_FILE_BYTES || bytes[0].toInt() != FORMAT_VERSION) {
            invalidateEncryptedState(strict = false)
            return null
        }

        val ivSize = bytes[1].toInt() and 0xff
        if (ivSize !in 12..32 || bytes.size <= 2 + ivSize) {
            invalidateEncryptedState(strict = false)
            return null
        }

        val iv = bytes.copyOfRange(2, 2 + ivSize)
        val ciphertext = bytes.copyOfRange(2 + ivSize, bytes.size)
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_BITS, iv))
            cipher.updateAAD(associatedData)
            cipher.doFinal(ciphertext)
        } catch (_: GeneralSecurityException) {
            // A restored/corrupted ciphertext or invalidated key is not a usable session.
            invalidateEncryptedState(strict = false)
            null
        }
    }

    private fun encrypt(plaintext: ByteArray, retryAfterKeyReset: Boolean): EncryptedPayload {
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            cipher.updateAAD(associatedData)
            EncryptedPayload(cipher.iv, cipher.doFinal(plaintext))
        } catch (failure: GeneralSecurityException) {
            if (!retryAfterKeyReset) throw failure
            keyStore().deleteEntry(KEY_ALIAS)
            encrypt(plaintext, retryAfterKeyReset = false)
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val store = keyStore()
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun invalidateEncryptedState(strict: Boolean) {
        var firstFailure: Exception? = null
        fun attempt(description: String, block: () -> Boolean) {
            try {
                if (!block() && firstFailure == null) {
                    firstFailure = IllegalStateException(description)
                }
            } catch (failure: Exception) {
                if (firstFailure == null) firstFailure = failure else firstFailure.addSuppressed(failure)
            }
        }

        // Invalidate the key first. A later file deletion failure can leave only
        // unusable ciphertext, never a refresh token that can be restored.
        attempt("Unable to invalidate the encrypted session key") {
            val store = keyStore()
            if (store.containsAlias(KEY_ALIAS)) store.deleteEntry(KEY_ALIAS)
            !store.containsAlias(KEY_ALIAS)
        }
        attempt("Unable to delete encrypted session") {
            !sessionFile.exists() || sessionFile.delete()
        }
        attempt("Unable to delete temporary encrypted session") {
            !tempFile.exists() || tempFile.delete()
        }

        if (strict) firstFailure?.let { throw it }
    }

    private data class EncryptedPayload(val iv: ByteArray, val ciphertext: ByteArray)

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "fluyo_supabase_session_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
        const val FORMAT_VERSION = 1
        const val SESSION_FILE_NAME = "auth-session-v1.bin"
        const val LEGACY_DISABLED_FILE_NAME = "auth-session-v1.legacy-disabled"
        const val LEGACY_DISABLED_FORMAT = 1
        const val MAX_SESSION_BYTES = 512 * 1024
        const val MIN_FILE_BYTES = 2 + 12 + 16
        const val MAX_FILE_BYTES = MAX_SESSION_BYTES + 2 + 32 + 16
    }
}
