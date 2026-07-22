package com.qolve.fluyo.domain.repository

import java.time.LocalDate

/** Persistence port for the per-user, once-per-calendar-day nudge limit. */
interface NudgeHistoryRepository {
    suspend fun lastFiredOn(expectedSessionEpoch: Long): LocalDate?

    /** Atomically claims today's notification slot for the active user. */
    suspend fun claimToday(expectedSessionEpoch: Long): Boolean
}
