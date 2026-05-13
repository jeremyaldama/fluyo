package com.qolve.fluyo.domain.usecase

import com.qolve.fluyo.domain.repository.GoalDepositOutcome
import com.qolve.fluyo.domain.repository.GoalRepository
import javax.inject.Inject

class DepositToGoalUseCase @Inject constructor(
    private val goalRepository: GoalRepository,
) {
    suspend operator fun invoke(goalId: String, amount: Double): Result<GoalDepositOutcome> =
        goalRepository.deposit(goalId, amount)
}
