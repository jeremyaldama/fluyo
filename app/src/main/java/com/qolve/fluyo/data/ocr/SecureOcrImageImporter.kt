package com.qolve.fluyo.data.ocr

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import com.qolve.fluyo.data.local.SensitiveArtifactCoordinator
import com.qolve.fluyo.domain.suspendRunCatching
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class ImportedOcrImage(
    val uri: Uri,
    val mimeType: String,
    val byteCount: Long,
    val width: Int,
    val height: Int,
)

/**
 * Moves an untrusted content URI across the app's trust boundary.
 *
 * The source is streamed into a size-limited private cache file before any image decoder sees
 * it. Both the provider-declared type and the decoder-detected type must be supported, and the
 * bounded image is exposed to UI/OCR only through this app's non-exported FileProvider.
 */
@Singleton
class SecureOcrImageImporter @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val artifactCoordinator: SensitiveArtifactCoordinator = SensitiveArtifactCoordinator(),
) {
    suspend fun import(
        source: Uri,
        validateBeforeWrite: () -> Unit = {},
    ): Result<ImportedOcrImage> {
        val completedFile = AtomicReference<File?>()
        return try {
            artifactCoordinator.withLock {
                // Revalidate only after waiting for a possible sign-out purge. If still
                // current, cleanup cannot run until this bounded write has completed.
                validateBeforeWrite()
                withContext(Dispatchers.IO) {
                    suspendRunCatching {
                        withTimeoutOrNull(IMPORT_TIMEOUT_MILLIS) {
                            importOrThrow(source, completedFile::set)
                        } ?: throw InvalidOcrImageException("Image import timed out")
                    }
                }
            }
        } catch (cancelled: kotlin.coroutines.cancellation.CancellationException) {
            // withContext has prompt cancellation: its completed value can be discarded while
            // returning to the caller. Track the finalized file so that case cannot orphan it.
            completedFile.getAndSet(null)?.delete()
            throw cancelled
        }
    }

    fun isOwnedImportUri(uri: Uri): Boolean =
        uri.scheme == CONTENT_SCHEME &&
            uri.authority == fileProviderAuthority &&
            uri.pathSegments.firstOrNull() == IMPORTS_PATH

    private suspend fun importOrThrow(
        source: Uri,
        onFinalized: (File) -> Unit,
    ): ImportedOcrImage {
        if (source.scheme != CONTENT_SCHEME) {
            throw InvalidOcrImageException("Only content URIs are accepted")
        }

        val declaredMime = OcrImagePolicy.canonicalMimeType(
            runInterruptible { context.contentResolver.getType(source) },
        ) ?: throw InvalidOcrImageException("The provider did not declare a supported image type")

        val directory = File(context.cacheDir, IMPORTS_PATH)
        if (!directory.exists() && !directory.mkdirs()) {
            throw IllegalStateException("Unable to create the private OCR cache")
        }

        val token = UUID.randomUUID().toString()
        val stagingFile = File(directory, "receipt-$token.part")
        var finalFile: File? = null
        var completed = false
        try {
            val callerContext = currentCoroutineContext()
            val inputStream = runInterruptible {
                context.contentResolver.openInputStream(source)
            } ?: throw InvalidOcrImageException("The image content could not be opened")
            val byteCount = inputStream.use { input ->
                FileOutputStream(stagingFile).use { output ->
                    runInterruptible {
                        OcrImagePolicy.copyBounded(input, output) {
                            callerContext.ensureActive()
                        }
                    }
                }
            }

            if (byteCount == 0L) {
                throw InvalidOcrImageException("The image content is empty")
            }

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            runInterruptible {
                BitmapFactory.decodeFile(stagingFile.absolutePath, bounds)
            }
            val detectedMime = OcrImagePolicy.canonicalMimeType(bounds.outMimeType)
                ?: throw InvalidOcrImageException("The copied content is not a supported image")
            if (declaredMime != detectedMime) {
                throw InvalidOcrImageException("The declared and detected image types do not match")
            }
            OcrImagePolicy.validateDimensions(bounds.outWidth, bounds.outHeight)

            val destination = File(
                directory,
                "receipt-$token.${OcrImagePolicy.extensionFor(detectedMime)}",
            )
            if (!stagingFile.renameTo(destination)) {
                throw IllegalStateException("Unable to finalize the private OCR image")
            }
            finalFile = destination

            val ownedUri = FileProvider.getUriForFile(
                context,
                fileProviderAuthority,
                destination,
            )
            onFinalized(destination)
            completed = true
            return ImportedOcrImage(
                uri = ownedUri,
                mimeType = detectedMime,
                byteCount = byteCount,
                width = bounds.outWidth,
                height = bounds.outHeight,
            )
        } finally {
            stagingFile.delete()
            if (!completed) finalFile?.delete()
        }
    }

    private val fileProviderAuthority: String
        get() = "${context.packageName}.fileprovider"

    companion object {
        const val IMPORTS_PATH = "ocr-imports"
        private const val CONTENT_SCHEME = "content"
        private const val IMPORT_TIMEOUT_MILLIS = 15_000L
    }
}
