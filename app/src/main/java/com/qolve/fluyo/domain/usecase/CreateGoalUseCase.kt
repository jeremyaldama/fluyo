package com.qolve.fluyo.domain.usecase

import com.qolve.fluyo.domain.model.Goal
import com.qolve.fluyo.domain.repository.GoalRepository
import java.time.LocalDate
import javax.inject.Inject

class CreateGoalUseCase @Inject constructor(
    private val goalRepository: GoalRepository,
) {
    suspend operator fun invoke(
        name: String,
        target: Double,
        deadline: LocalDate?,
    ): Result<Goal> = goalRepository.createGoal(name, target, deadline)
}
