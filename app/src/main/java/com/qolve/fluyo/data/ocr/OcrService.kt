package com.qolve.fluyo.data.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.qolve.fluyo.domain.suspendRunCatching
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device OCR using Google ML Kit Text Recognition v2.
 * No data leaves the device.
 */
@Singleton
class OcrService @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val imageImporter: SecureOcrImageImporter,
) {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognize(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        suspendRunCatching {
            require(imageImporter.isOwnedImportUri(uri)) {
                "OCR accepts only images imported into the private app cache"
            }
            val image = InputImage.fromFilePath(context, uri)
            withTimeoutOrNull(OCR_TIMEOUT_MILLIS) {
                recognizer.process(image).await().text
            } ?: throw OcrTimeoutException()
        }
    }

    private companion object {
        const val OCR_TIMEOUT_MILLIS = 30_000L
    }
}

private class OcrTimeoutException : IllegalStateException("On-device OCR timed out")
