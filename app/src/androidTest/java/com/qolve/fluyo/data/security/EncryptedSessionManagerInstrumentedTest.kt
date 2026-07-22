package com.qolve.fluyo.data.security

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.auth.SessionManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore

@RunWith(AndroidJUnit4::class)
class EncryptedSessionManagerInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val file = File(context.noBackupFilesDir, "auth-session-v1.bin")
    private val tempFile = File(context.noBackupFilesDir, "auth-session-v1.bin.tmp")
    private val legacyDisabledFile =
        File(context.noBackupFilesDir, "auth-session-v1.legacy-disabled")
    private val legacyDisabledTempFile =
        File(context.noBackupFilesDir, "auth-session-v1.legacy-disabled.tmp")
    private lateinit var manager: EncryptedSessionManager

    @Before
    fun setUp() = runBlocking {
        cleanPersistentState()
        manager = EncryptedSessionManager(context)
    }

    @After
    fun tearDown() = runBlocking {
        cleanPersistentState()
    }

    @Test
    fun sessionRoundTripsWithoutPlaintextTokensOnDisk() = runBlocking {
        val session = UserSession(
            accessToken = "access-token-that-must-not-appear",
            refreshToken = "refresh-token-that-must-not-appear",
            expiresIn = 3_600,
            tokenType = "bearer",
        )

        manager.saveSession(session)

        assertEquals(session, manager.loadSession())
        val persisted = file.readBytes().decodeToString()
        assertFalse(persisted.contains(session.accessToken))
        assertFalse(persisted.contains(session.refreshToken))
    }

    @Test
    fun corruptedCiphertextFailsClosedAndIsRemoved() = runBlocking {
        file.writeBytes(byteArrayOf(1, 12, 1, 2, 3))

        assertNull(manager.loadSession())
        assertFalse(file.exists())
    }

    @Test
    fun deletionFailureStillInvalidatesTheKeystoreKeyAndIsReported() = runBlocking {
        manager.saveSession(
            UserSession(
                accessToken = "access",
                refreshToken = "refresh",
                expiresIn = 3_600,
                tokenType = "bearer",
            ),
        )
        assertTrue(file.delete())
        assertTrue(file.mkdir())
        val blocker = File(file, "blocker")
        blocker.writeText("forces directory deletion to fail")

        val failure = runCatching { manager.deleteSession() }.exceptionOrNull()

        assertNotNull(failure)
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        assertFalse(keyStore.containsAlias("fluyo_supabase_session_v1"))

        assertTrue(blocker.delete())
        assertTrue(file.delete())
    }

    @Test
    fun failedLegacyDeletionCannotResurrectSessionAfterSignOut() = runBlocking {
        val legacySession = UserSession(
            accessToken = "legacy-access",
            refreshToken = "legacy-refresh",
            expiresIn = 3_600,
            tokenType = "bearer",
        )
        val legacy = FailingLegacyManager(legacySession)
        val migrating = EncryptedSessionManager(context, legacy)

        assertEquals(legacySession, migrating.loadSession())
        assertTrue(legacyDisabledFile.isFile)
        assertNotNull(runCatching { migrating.deleteSession() }.exceptionOrNull())
        assertFalse(file.exists())

        // A fresh process still sees the stale legacy value, but the durable marker
        // makes fallback impossible after encrypted-session invalidation.
        assertNull(EncryptedSessionManager(context, legacy).loadSession())
    }

    private fun cleanPersistentState() {
        listOf(file, tempFile, legacyDisabledFile, legacyDisabledTempFile).forEach { path ->
            if (path.isDirectory) path.listFiles()?.forEach { it.delete() }
            path.delete()
        }
        KeyStore.getInstance("AndroidKeyStore").apply {
            load(null)
            if (containsAlias("fluyo_supabase_session_v1")) {
                deleteEntry("fluyo_supabase_session_v1")
            }
        }
    }

    private class FailingLegacyManager(
        private val session: UserSession,
    ) : SessionManager {
        override suspend fun saveSession(session: UserSession) = Unit
        override suspend fun loadSession(): UserSession = session
        override suspend fun deleteSession(): Unit = error("legacy storage is unavailable")
    }
}
