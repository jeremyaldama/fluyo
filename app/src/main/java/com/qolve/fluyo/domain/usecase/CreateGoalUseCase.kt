package com.qolve.fluyo.domain.usecase

import com.qolve.fluyo.domain.model.Goal
import com.qolve.fluyo.domain.model.MoneyAmount
import com.qolve.fluyo.domain.repository.GoalRepository
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

class CreateGoalUseCase @Inject constructor(
    private val goalRepository: GoalRepository,
) {
    suspend operator fun invoke(
        name: String,
        target: MoneyAmount,
        deadline: LocalDate?,
        requestId: String = UUID.randomUUID().toString(),
    ): Result<Goal> = goalRepository.createGoal(name, target, deadline, requestId)

    companion object {
        /** HU-07: a user may keep at most this many goals active at once. */
        const val MAX_ACTIVE_GOALS = 5
    }
}
