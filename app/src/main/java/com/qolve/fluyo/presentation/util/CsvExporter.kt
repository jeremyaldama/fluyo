package com.qolve.fluyo.presentation.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.qolve.fluyo.data.local.SensitiveArtifactCoordinator
import com.qolve.fluyo.domain.model.Expense
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@ConsistentCopyVisibility
data class CsvExportArtifact internal constructor(
    val uri: Uri,
    internal val file: File,
)

/**
 * Writes expenses to a CSV in cacheDir/exports and returns a shareable content:// URI
 * via the app's FileProvider (HU-11 — "Exportar datos"). The outgoing counterpart to the
 * incoming share flow handled by `SharedImageEvents`.
 */
@Singleton
class CsvExporter @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val artifactCoordinator: SensitiveArtifactCoordinator = SensitiveArtifactCoordinator(),
) {
    /**
     * @param expenses rows to export, already ordered as desired.
     * @param categoryNames map of categoryId → display name for the "Categoría" column.
     * @param currencyCode ISO code stamped in the "Moneda" column.
     */
    suspend fun export(
        expenses: List<Expense>,
        categoryNames: Map<String, String>,
        currencyCode: String,
        validateBeforeWrite: () -> Unit = {},
    ): CsvExportArtifact = artifactCoordinator.withLock {
        // This executes after waiting for any sign-out cleanup. A stale caller therefore
        // fails before it can recreate the previous identity's CSV.
        validateBeforeWrite()
        withContext(Dispatchers.IO) {
            exportLocked(expenses, categoryNames, currencyCode)
        }
    }

    private fun exportLocked(
        expenses: List<Expense>,
        categoryNames: Map<String, String>,
        currencyCode: String,
    ): CsvExportArtifact {
        val header = listOf(
            "Fecha", "Monto", "Moneda", "Categoría", "Descripción", "Origen", "Destinatario",
        )

        val dir = File(context.cacheDir, "exports")
        check((dir.isDirectory || dir.mkdirs()) && dir.isDirectory) {
            "Unable to create the export directory"
        }
        // Every grant points to immutable content. Reusing one fixed path lets a later export
        // silently change what an earlier share recipient reads.
        val token = UUID.randomUUID().toString()
        val file = File(dir, "fluyo-gastos-$token.csv")
        val temporary = File(dir, "fluyo-gastos-$token.csv.tmp")
        try {
            temporary.outputStream().bufferedWriter(StandardCharsets.UTF_8).use { writer ->
                // Lead with a UTF-8 BOM so Excel renders accents (é, ñ) correctly.
                writer.append('\uFEFF')
                writer.append(header.joinToString(",") { escapeCsvCell(it) })
                writer.newLine()
                expenses.forEach { expense ->
                    val row = listOf(
                        expense.expenseDate.toString(),
                        formatAmount(expense.amount),
                        currencyCode,
                        expense.categoryId?.let { categoryNames[it] }.orEmpty(),
                        expense.description.orEmpty(),
                        expense.source.wire,
                        expense.recipient.orEmpty(),
                    )
                    writer.append(row.joinToString(",") { escapeCsvCell(it) })
                    writer.newLine()
                }
            }
            if (file.exists() && !file.delete()) error("Unable to replace the previous export")
            if (!temporary.renameTo(file)) error("Unable to publish the CSV export")
        } catch (failure: Exception) {
            temporary.delete()
            throw failure
        }

        return CsvExportArtifact(
            uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file),
            file = file,
        )
    }

    /** Removes an export whose originating session changed before publication. */
    fun discard(artifact: CsvExportArtifact) {
        if (artifact.file.exists() && !artifact.file.delete() && artifact.file.exists()) {
            error("Unable to discard a stale CSV export")
        }
    }
}

/**
 * Escapes a CSV cell and neutralizes spreadsheet formula prefixes. Descriptions,
 * categories and recipients can originate in OCR/WhatsApp, so treating them as trusted
 * spreadsheet input would allow formula injection when the export is opened in Excel.
 */
internal fun escapeCsvCell(value: String): String {
    val firstMeaningful = value
        .dropWhile { it.isWhitespace() || it.isISOControl() }
        .firstOrNull()
    val safeValue = if (firstMeaningful != null && firstMeaningful in setOf('=', '+', '-', '@')) {
        "'$value"
    } else {
        value
    }
    return if (safeValue.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
        "\"" + safeValue.replace("\"", "\"\"") + "\""
    } else {
        safeValue
    }
}
