package com.qolve.fluyo.presentation.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.qolve.fluyo.domain.model.Expense
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes expenses to a CSV in cacheDir/exports and returns a shareable content:// URI
 * via the app's FileProvider (HU-11 — "Exportar datos"). The outgoing counterpart to the
 * incoming share flow handled by `SharedImageEvents`.
 */
@Singleton
class CsvExporter @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    /**
     * @param expenses rows to export, already ordered as desired.
     * @param categoryNames map of categoryId → display name for the "Categoría" column.
     * @param currencyCode ISO code stamped in the "Moneda" column.
     */
    fun export(
        expenses: List<Expense>,
        categoryNames: Map<String, String>,
        currencyCode: String,
    ): Uri {
        val header = listOf(
            "Fecha", "Monto", "Moneda", "Categoría", "Descripción", "Origen", "Destinatario",
        )
        val rows = expenses.map { e ->
            listOf(
                e.expenseDate.toString(),
                formatAmount(e.amount),
                currencyCode,
                e.categoryId?.let { categoryNames[it] }.orEmpty(),
                e.description.orEmpty(),
                e.source.wire,
                e.recipient.orEmpty(),
            )
        }

        val csv = buildString {
            append(header.joinToString(",") { it.csvEscaped() })
            append('\n')
            rows.forEach { row ->
                append(row.joinToString(",") { it.csvEscaped() })
                append('\n')
            }
        }

        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "fluyo-gastos.csv")
        // Lead with a UTF-8 BOM so Excel renders accents (é, ñ) correctly.
        file.writeText("\uFEFF" + csv)

        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    private fun String.csvEscaped(): String =
        if (any { it == ',' || it == '"' || it == '\n' }) {
            "\"" + replace("\"", "\"\"") + "\""
        } else {
            this
        }
}
