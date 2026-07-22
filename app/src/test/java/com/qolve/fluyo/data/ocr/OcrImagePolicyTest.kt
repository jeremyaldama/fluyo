package com.qolve.fluyo.data.ocr

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class OcrImagePolicyTest {
    @Test
    fun `only canonical JPEG PNG and WebP MIME types are accepted`() {
        assertEquals("image/jpeg", OcrImagePolicy.canonicalMimeType("image/jpg"))
        assertEquals("image/png", OcrImagePolicy.canonicalMimeType(" IMAGE/PNG; charset=binary "))
        assertEquals("image/webp", OcrImagePolicy.canonicalMimeType("image/webp"))
        assertNull(OcrImagePolicy.canonicalMimeType("image/gif"))
        assertNull(OcrImagePolicy.canonicalMimeType("application/octet-stream"))
        assertNull(OcrImagePolicy.canonicalMimeType(null))
    }

    @Test
    fun `bounded copy accepts exactly the byte limit`() {
        val bytes = ByteArray(1_024) { (it % 251).toByte() }
        val output = ByteArrayOutputStream()

        val copied = OcrImagePolicy.copyBounded(
            input = ByteArrayInputStream(bytes),
            output = output,
            maxBytes = bytes.size.toLong(),
        )

        assertEquals(bytes.size.toLong(), copied)
        assertArrayEquals(bytes, output.toByteArray())
    }

    @Test
    fun `bounded copy rejects one byte over the limit`() {
        assertThrows(InvalidOcrImageException::class.java) {
            OcrImagePolicy.copyBounded(
                input = ByteArrayInputStream(ByteArray(1_025)),
                output = ByteArrayOutputStream(),
                maxBytes = 1_024,
            )
        }
    }

    @Test
    fun `dimensions reject oversized edges and decompression bombs`() {
        OcrImagePolicy.validateDimensions(4_000, 10_000)

        assertThrows(InvalidOcrImageException::class.java) {
            OcrImagePolicy.validateDimensions(12_001, 100)
        }
        assertThrows(InvalidOcrImageException::class.java) {
            OcrImagePolicy.validateDimensions(8_000, 8_000)
        }
        assertThrows(InvalidOcrImageException::class.java) {
            OcrImagePolicy.validateDimensions(0, 100)
        }
    }
}
