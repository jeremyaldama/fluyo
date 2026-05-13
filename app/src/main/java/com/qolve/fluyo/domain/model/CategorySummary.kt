package com.qolve.fluyo.domain.model

/** Aggregated spending for one category over a period. */
data class CategorySummary(
    val categoryId: String?,
    val name: String,
    val color: String,
    val icon: String,
    val total: Double,
    val count: Int,
) {
    fun share(allTotal: Double): Float =
        if (allTotal <= 0.0) 0f else (total / allTotal).toFloat().coerceIn(0f, 1f)
}
