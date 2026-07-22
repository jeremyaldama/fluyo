package com.qolve.fluyo.presentation.util

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import java.time.LocalDate

/** Material DatePicker policy backed by calendar dates rather than device time-zone instants. */
@OptIn(ExperimentalMaterial3Api::class)
class LocalDateSelectableDates(
    private val minimum: LocalDate,
    private val maximum: LocalDate,
) : SelectableDates {
    init {
        require(!maximum.isBefore(minimum)) { "Invalid selectable date range" }
    }

    override fun isSelectableDate(utcTimeMillis: Long): Boolean =
        datePickerUtcMillisToLocalDate(utcTimeMillis) in minimum..maximum

    override fun isSelectableYear(year: Int): Boolean = year in minimum.year..maximum.year
}
