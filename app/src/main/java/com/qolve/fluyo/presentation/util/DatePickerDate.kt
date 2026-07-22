package com.qolve.fluyo.presentation.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Material date pickers encode a calendar date as midnight UTC, not as midnight in the
 * device time zone. Keeping both directions here prevents dates from shifting by one day
 * on devices west or east of UTC.
 */
fun LocalDate.toDatePickerUtcMillis(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

fun datePickerUtcMillisToLocalDate(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

val MIN_EXPENSE_DATE: LocalDate = LocalDate.of(2000, 1, 1)

fun isAllowedExpenseDate(date: LocalDate, today: LocalDate): Boolean =
    date in MIN_EXPENSE_DATE..today

fun isAllowedGoalDeadline(date: LocalDate?, today: LocalDate): Boolean =
    date == null || !date.isBefore(today)
