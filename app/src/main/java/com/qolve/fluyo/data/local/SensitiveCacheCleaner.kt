package com.qolve.fluyo.data.local

import android.content.Context
import android.net.Uri
import com.qolve.fluyo.data.SessionScopedCache
import com.qolve.fluyo.data.ocr.SecureOcrImageImporter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Removes user-generated OCR images and CSV exports from the app cache. */
@Singleton
class SensitiveCacheCleaner @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val artifactCoordinator: SensitiveArtifactCoordinator = SensitiveArtifactCoordinator(),
) : SessionScopedCache {

    fun deleteOwnedCapture(uri: Uri?): Boolean {
        if (uri == null || uri.scheme != "content") return false
        if (uri.authority != "${context.packageName}.fileprovider") return false
        if (uri.pathSegments.firstOrNull() !in OWNED_RECEIPT_PATHS) return false
        return runCatching { context.contentResolver.delete(uri, null, null) > 0 }
            .getOrDefault(false)
    }

    fun deleteStaleFiles(olderThanMillis: Long = DAY_MILLIS) {
        val cutoff = System.currentTimeMillis() - olderThanMillis
        sensitiveDirectories().forEach { directory ->
            directory.listFiles()
                ?.filter { file -> file.lastModified() > 0L && file.lastModified() < cutoff }
                ?.forEach { it.deleteRecursively() }
        }
    }

    override suspend fun clearForSignOut() {
        // Identity transitions must not be able to cancel halfway through privacy cleanup.
        // Holding the same lock as writers also proves that no old-session writer can recreate
        // a file after this method returns.
        withContext(NonCancellable + Dispatchers.IO) {
            artifactCoordinator.withLock {
                val failed = sensitiveDirectories().flatMap(::deleteContentsStrict)
                check(failed.isEmpty()) {
                    "Unable to remove all sensitive cache artifacts"
                }
            }
        }
    }

    private fun sensitiveDirectories() = listOf(
        File(context.cacheDir, CAPTURES_PATH),
        File(context.cacheDir, SecureOcrImageImporter.IMPORTS_PATH),
        File(context.cacheDir, EXPORTS_PATH),
    )

    /** Returns only failures; paths are deliberately not surfaced to logs or UI. */
    private fun deleteContentsStrict(directory: File): List<File> {
        if (!directory.exists()) return emptyList()
        if (!directory.isDirectory) return listOf(directory)
        val artifacts = directory.listFiles() ?: return listOf(directory)
        return artifacts.filterNot { artifact ->
            artifact.deleteRecursively() && !artifact.exists()
        }
    }

    private companion object {
        const val CAPTURES_PATH = "captures"
        const val EXPORTS_PATH = "exports"
        val OWNED_RECEIPT_PATHS = setOf(CAPTURES_PATH, SecureOcrImageImporter.IMPORTS_PATH)
        const val DAY_MILLIS = 24L * 60L * 60L * 1_000L
    }
}
