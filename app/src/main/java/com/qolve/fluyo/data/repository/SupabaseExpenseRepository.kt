package com.qolve.fluyo.data.repository

import com.qolve.fluyo.data.SessionScopedCache
import com.qolve.fluyo.data.publishIfCurrent
import com.qolve.fluyo.data.requireCurrent
import com.qolve.fluyo.data.dto.CurrentMonthBudgetDto
import com.qolve.fluyo.data.dto.ExpenseCreateRpcParams
import com.qolve.fluyo.data.dto.ExpenseDto
import com.qolve.fluyo.data.dto.ExpensePageDto
import com.qolve.fluyo.data.dto.ExpensePageRpcParams
import com.qolve.fluyo.data.dto.ExpenseStreakRpcResultDto
import com.qolve.fluyo.data.mapper.toDomain
import com.qolve.fluyo.domain.model.Expense
import com.qolve.fluyo.domain.model.ExpenseSource
import com.qolve.fluyo.domain.model.MonthlyBreakdown
import com.qolve.fluyo.domain.model.MoneyAmount
import com.qolve.fluyo.domain.repository.AuthRepository
import com.qolve.fluyo.domain.repository.ExpenseRepository
import com.qolve.fluyo.domain.repository.SessionBoundary
import com.qolve.fluyo.domain.suspendRunCatching
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

@Singleton
class SupabaseExpenseRepository @Inject constructor(
    private val client: SupabaseClient,
    private val authRepository: AuthRepository,
    private val sessionBoundary: SessionBoundary,
) : ExpenseRepository, SessionScopedCache {

    private companion object {
        /** Stay below PostgREST's common 1,000-row response cap. */
        const val PAGE_SIZE = 500
    }

    private val expensesState = MutableStateFlow<List<Expense>>(emptyList())
    private val breakdownState = MutableStateFlow(
        MonthlyBreakdown(MoneyAmount.ZERO, MoneyAmount.ZERO),
    )

    override suspend fun clearForSignOut() {
        expensesState.value = emptyList()
        breakdownState.value = MonthlyBreakdown(MoneyAmount.ZERO, MoneyAmount.ZERO)
    }

    override fun observeRecentExpenses(limit: Int): Flow<List<Expense>> =
        expensesState.asStateFlow()
            .map { expenses -> expenses.take(limit.coerceAtLeast(0)) }

    override fun observeMonthlyBreakdown(): Flow<MonthlyBreakdown> =
        breakdownState.asStateFlow()

    override suspend fun refresh(): Result<Unit> = suspendRunCatching {
        val sessionEpoch = sessionBoundary.snapshot()
        sessionBoundary.requireCurrent(sessionEpoch)
        val userId = authRepository.currentUserId() ?: return@suspendRunCatching
        val expenses = fetchExpenses(userId)
        val breakdown = fetchBreakdown(userId)
        sessionBoundary.publishIfCurrent(sessionEpoch) {
            expensesState.value = expenses
            breakdownState.value = breakdown
        }
    }

    override suspend fun register(
        amount: MoneyAmount,
        categoryId: String?,
        description: String?,
        expenseDate: LocalDate,
        source: ExpenseSource,
        recipient: String?,
        imageUrl: String?,
        requestId: String,
    ): Result<Expense> = suspendRunCatching {
        val sessionEpoch = sessionBoundary.snapshot()
        sessionBoundary.requireCurrent(sessionEpoch)
        val userId = authRepository.currentUserId() ?: error("No authenticated user")

        require(requestId.isNotBlank()) { "Expense request id must not be blank" }
        val parameters = ExpenseCreateRpcParams(
            requestId = requestId,
            amount = amount.toTransportDouble(),
            categoryId = categoryId,
            description = description?.takeIf { it.isNotBlank() },
            expenseDate = expenseDate.toString(),
            source = source.wire,
            recipient = recipient?.takeIf { it.isNotBlank() },
            imageUrl = imageUrl,
        )

        val saved = client.postgrest.rpc(
            function = "create_expense",
            parameters = Json.encodeToJsonElement(parameters).jsonObject,
        )
            .decodeSingle<ExpenseDto>()
            .toDomain()

        // Refresh derived state only if this response still belongs to the active session.
        val expenses = fetchExpenses(userId)
        val breakdown = fetchBreakdown(userId)
        sessionBoundary.publishIfCurrent(sessionEpoch) {
            expensesState.value = expenses
            breakdownState.value = breakdown
        }

        saved
    }

    override suspend fun getById(id: String): Result<Expense?> = suspendRunCatching {
        val sessionEpoch = sessionBoundary.snapshot()
        sessionBoundary.requireCurrent(sessionEpoch)
        val result = client.postgrest.from("expenses")
            .select { filter { eq("id", id) } }
            .decodeSingleOrNull<ExpenseDto>()
            ?.toDomain()
        sessionBoundary.requireCurrent(sessionEpoch)
        result
    }

    override suspend fun update(
        id: String,
        amount: MoneyAmount,
        categoryId: String?,
        description: String?,
        expenseDate: LocalDate,
    ): Result<Expense> = suspendRunCatching {
        val sessionEpoch = sessionBoundary.snapshot()
        sessionBoundary.requireCurrent(sessionEpoch)
        val userId = authRepository.currentUserId() ?: error("No authenticated user")
        // Use PostgREST's update DSL so nullable columns are sent explicitly. A DTO with
        // `null` defaults is encoded with those properties omitted, making it impossible
        // to clear an existing category or description.
        val updated = client.postgrest.from("expenses")
            .update({
                set("amount", amount.toTransportDouble())
                set("category_id", categoryId)
                set("description", description?.trim()?.takeIf { it.isNotEmpty() })
                set("expense_date", expenseDate.toString())
            }) {
                filter { eq("id", id) }
                select()
            }
            .decodeSingle<ExpenseDto>()
            .toDomain()
        val expenses = fetchExpenses(userId)
        val breakdown = fetchBreakdown(userId)
        sessionBoundary.publishIfCurrent(sessionEpoch) {
            expensesState.value = expenses
            breakdownState.value = breakdown
        }
        updated
    }

    override suspend fun delete(id: String): Result<Unit> = suspendRunCatching {
        val sessionEpoch = sessionBoundary.snapshot()
        sessionBoundary.requireCurrent(sessionEpoch)
        val userId = authRepository.currentUserId() ?: error("No authenticated user")
        client.postgrest.from("expenses")
            .delete { filter { eq("id", id) } }
        val expenses = fetchExpenses(userId)
        val breakdown = fetchBreakdown(userId)
        sessionBoundary.publishIfCurrent(sessionEpoch) {
            expensesState.value = expenses
            breakdownState.value = breakdown
        }
    }

    override suspend fun findCreatedByRequestId(requestId: String): Result<Expense?> =
        suspendRunCatching {
            val sessionEpoch = sessionBoundary.snapshot()
            sessionBoundary.requireCurrent(sessionEpoch)
            val userId = authRepository.currentUserId() ?: error("No authenticated user")
            require(requestId.isNotBlank()) { "Expense request id must not be blank" }
            val expense = client.postgrest.from("expenses")
                .select {
                    filter {
                        eq("user_id", userId)
                        eq("client_request_id", requestId)
                    }
                }
                .decodeSingleOrNull<ExpenseDto>()
                ?.toDomain()
            if (expense != null) {
                val expenses = fetchExpenses(userId)
                val breakdown = fetchBreakdown(userId)
                sessionBoundary.publishIfCurrent(sessionEpoch) {
                    expensesState.value = expenses
                    breakdownState.value = breakdown
                }
            }
            sessionBoundary.requireCurrent(sessionEpoch)
            expense
        }

    private suspend fun fetchExpenses(userId: String): List<Expense> =
        client.postgrest.from("expenses")
            .select {
                filter { eq("user_id", userId) }
                order("expense_date", Order.DESCENDING)
                order("created_at", Order.DESCENDING)
                limit(50)
            }
            .decodeList<ExpenseDto>()
            .map { it.toDomain() }

    override suspend fun loadByDateRange(
        from: LocalDate,
        to: LocalDate,
    ): Result<List<Expense>> = suspendRunCatching {
        val sessionEpoch = sessionBoundary.snapshot()
        sessionBoundary.requireCurrent(sessionEpoch)
        authRepository.currentUserId() ?: return@suspendRunCatching emptyList()
        require(!to.isBefore(from)) { "Invalid date range: $from..$to" }

        val result = mutableListOf<Expense>()
        var snapshotAt: String? = null
        var beforeCreatedAt: String? = null
        var beforeId: String? = null
        var previousCursor: Pair<String, String>? = null
        do {
            val parameters = ExpensePageRpcParams(
                from = from.toString(),
                to = to.toString(),
                snapshotAt = snapshotAt,
                beforeCreatedAt = beforeCreatedAt,
                beforeId = beforeId,
                pageSize = PAGE_SIZE,
            )
            val page = client.postgrest.rpc(
                function = "expense_page",
                parameters = Json.encodeToJsonElement(parameters).jsonObject,
            ).decodeList<ExpensePageDto>()
            check(page.size <= PAGE_SIZE) { "Expense page exceeded the requested limit" }
            if (page.isEmpty()) break

            val pageSnapshots = page.map(ExpensePageDto::snapshotAt).toSet()
            check(pageSnapshots.size == 1) { "Expense page mixed multiple snapshots" }
            val returnedSnapshot = pageSnapshots.single()
            if (snapshotAt == null) snapshotAt = returnedSnapshot
            check(snapshotAt == returnedSnapshot) { "Expense snapshot changed between pages" }

            result += page.map { it.asExpenseDto().toDomain() }
            val last = page.last()
            val nextCursor = last.createdAt to last.id
            check(nextCursor != previousCursor) { "Expense cursor did not advance" }
            previousCursor = nextCursor
            beforeCreatedAt = last.createdAt
            beforeId = last.id
            sessionBoundary.requireCurrent(sessionEpoch)
        } while (page.size == PAGE_SIZE)

        sessionBoundary.requireCurrent(sessionEpoch)
        result.sortedWith(
            compareByDescending<Expense> { it.expenseDate }
                .thenByDescending { it.createdAt }
                .thenByDescending { it.id },
        )
    }

    private suspend fun fetchBreakdown(userId: String): MonthlyBreakdown {
        val row = client.postgrest.from("current_month_budget")
            .select { filter { eq("user_id", userId) } }
            .decodeSingleOrNull<CurrentMonthBudgetDto>()
        return row?.toDomain()
            ?: MonthlyBreakdown(MoneyAmount.ZERO, MoneyAmount.ZERO)
    }

    /** Server-derived consecutive-day streak with no client-side history ceiling. */
    override suspend fun currentStreak(): Result<Int> {
        val sessionEpoch = sessionBoundary.snapshot()
        if (!sessionBoundary.isCurrent(sessionEpoch)) {
            return Result.failure(IllegalStateException("Session transition in progress"))
        }
        authRepository.currentUserId()
            ?: return Result.failure(IllegalStateException("No authenticated user"))
        return suspendRunCatching {
            val row = client.postgrest.rpc("current_expense_streak")
                .decodeSingle<ExpenseStreakRpcResultDto>()
            sessionBoundary.requireCurrent(sessionEpoch)
            row.streak.coerceAtLeast(0)
        }
    }
}
