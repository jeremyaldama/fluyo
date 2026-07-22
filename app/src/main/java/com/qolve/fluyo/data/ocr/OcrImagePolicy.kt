package com.qolve.fluyo.data.ocr

import java.io.InputStream
import java.io.OutputStream

/** Pure validation rules shared by the secure OCR importer and its JVM tests. */
internal object OcrImagePolicy {
    const val MAX_BYTES: Long = 10L * 1024L * 1024L
    const val MAX_DIMENSION: Int = 12_000
    const val MAX_PIXELS: Long = 40_000_000L

    private val allowedMimeTypes = setOf(
        "image/jpeg",
        "image/png",
        "image/webp",
    )

    fun canonicalMimeType(value: String?): String? {
        val normalized = value
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase()
            ?.let { if (it == "image/jpg") "image/jpeg" else it }
        return normalized?.takeIf(allowedMimeTypes::contains)
    }

    fun extensionFor(mimeType: String): String = when (canonicalMimeType(mimeType)) {
        "image/jpeg" -> "jpg"
        "image/png" -> "png"
        "image/webp" -> "webp"
        else -> throw InvalidOcrImageException("Unsupported image MIME type")
    }

    fun validateDimensions(width: Int, height: Int) {
        val pixels = width.toLong() * height.toLong()
        if (
            width <= 0 ||
            height <= 0 ||
            width > MAX_DIMENSION ||
            height > MAX_DIMENSION ||
            pixels > MAX_PIXELS
        ) {
            throw InvalidOcrImageException("Image dimensions are outside the accepted limits")
        }
    }

    fun copyBounded(
        input: InputStream,
        output: OutputStream,
        maxBytes: Long = MAX_BYTES,
        checkCancellation: () -> Unit = {},
    ): Long {
        require(maxBytes >= 0L)
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            checkCancellation()
            val read = input.read(buffer)
            if (read < 0) break
            if (read == 0) continue
            total += read
            if (total > maxBytes) {
                throw InvalidOcrImageException("Image exceeds the accepted byte limit")
            }
            output.write(buffer, 0, read)
        }
        return total
    }
}

internal class InvalidOcrImageException(message: String) : IllegalArgumentException(message)
