package com.qolve.fluyo.data.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device OCR using Google ML Kit Text Recognition v2.
 * No data leaves the device.
 */
@Singleton
class OcrService @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognize(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val image = InputImage.fromFilePath(context, uri)
            val result = recognizer.process(image).await()
            result.text
        }
    }
}
