package com.qolve.fluyo.domain.usecase

import com.qolve.fluyo.domain.repository.GoalDepositOutcome
import com.qolve.fluyo.domain.repository.GoalRepository
import com.qolve.fluyo.domain.model.MoneyAmount
import javax.inject.Inject

class DepositToGoalUseCase @Inject constructor(
    private val goalRepository: GoalRepository,
) {
    suspend operator fun invoke(
        goalId: String,
        amount: MoneyAmount,
        requestId: String,
    ): Result<GoalDepositOutcome> = goalRepository.deposit(goalId, amount, requestId)
}
