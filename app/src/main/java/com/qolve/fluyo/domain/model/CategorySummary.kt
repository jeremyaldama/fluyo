package com.qolve.fluyo.domain.model

/** Aggregated spending for one category over a period. */
data class CategorySummary(
    val categoryId: String?,
    val name: String,
    val color: String,
    val icon: String,
    val total: MoneyAmount,
    val count: Int,
) {
    fun share(allTotal: MoneyAmount): Float = total.ratioOf(allTotal).coerceIn(0f, 1f)
}
