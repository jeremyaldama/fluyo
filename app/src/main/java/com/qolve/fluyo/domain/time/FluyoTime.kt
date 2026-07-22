package com.qolve.fluyo.domain.time

import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * Product calendar boundary shared by Android and PostgreSQL.
 *
 * Budgets, streaks, statistics and dated expenses are defined in Lima civil time;
 * using the device zone in some screens and UTC on the server caused the month to
 * roll over at different moments. Tests should keep passing explicit dates/clocks.
 */
object FluyoTime {
    val ZONE_ID: ZoneId = ZoneId.of("America/Lima")

    fun today(clock: Clock = Clock.system(ZONE_ID)): LocalDate = LocalDate.now(clock)

    fun currentMonth(clock: Clock = Clock.system(ZONE_ID)): YearMonth =
        YearMonth.from(today(clock))
}

