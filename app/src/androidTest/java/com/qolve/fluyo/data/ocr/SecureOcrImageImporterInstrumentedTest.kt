package com.qolve.fluyo.data.ocr

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qolve.fluyo.data.local.SensitiveCacheCleaner
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecureOcrImageImporterInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var importer: SecureOcrImageImporter
    private lateinit var cleaner: SensitiveCacheCleaner

    @Before
    fun setUp() = runBlocking {
        importer = SecureOcrImageImporter(context)
        cleaner = SensitiveCacheCleaner(context)
        cleaner.clearForSignOut()
    }

    @After
    fun tearDown() = runBlocking {
        cleaner.clearForSignOut()
    }

    @Test
    fun validContentImageIsCopiedToOwnedBoundedUri() = runBlocking {
        val source = createBitmapContentUri("valid.png", Bitmap.CompressFormat.PNG)

        val imported = importer.import(source).getOrThrow()

        assertNotEquals(source, imported.uri)
        assertTrue(importer.isOwnedImportUri(imported.uri))
        assertEquals("image/png", imported.mimeType)
        assertEquals(64, imported.width)
        assertEquals(32, imported.height)
        assertTrue(imported.byteCount > 0L)
        assertTrue(context.contentResolver.openInputStream(imported.uri)?.use { it.read() >= 0 } == true)
    }

    @Test
    fun nonContentUriIsRejectedWithoutCreatingAnImport(): Unit = runBlocking {
        val source = File(context.cacheDir, "not-content.jpg").apply { writeBytes(byteArrayOf(1)) }

        val result = importer.import(Uri.fromFile(source))

        assertTrue(result.isFailure)
        assertFalse(File(context.cacheDir, SecureOcrImageImporter.IMPORTS_PATH).listFiles().orEmpty().isNotEmpty())
        source.delete()
    }

    @Test
    fun forgedJpegMimeWithNonImageBytesIsRejectedAndCleaned() = runBlocking {
        val source = createCaptureFile("forged.jpg").apply {
            writeText("this is not an image")
        }
        val sourceUri = fileProviderUri(source)

        val result = importer.import(sourceUri)

        assertTrue(result.isFailure)
        assertTrue(File(context.cacheDir, SecureOcrImageImporter.IMPORTS_PATH).listFiles().orEmpty().isEmpty())
    }

    private fun createBitmapContentUri(
        name: String,
        format: Bitmap.CompressFormat,
    ): Uri {
        val file = createCaptureFile(name)
        val bitmap = Bitmap.createBitmap(64, 32, Bitmap.Config.ARGB_8888)
        try {
            file.outputStream().use { output ->
                check(bitmap.compress(format, 100, output))
            }
        } finally {
            bitmap.recycle()
        }
        return fileProviderUri(file)
    }

    private fun createCaptureFile(name: String): File {
        val directory = File(context.cacheDir, "captures").apply { mkdirs() }
        return File(directory, name)
    }

    private fun fileProviderUri(file: File): Uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )
}
