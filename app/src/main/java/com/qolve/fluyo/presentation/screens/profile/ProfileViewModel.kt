package com.qolve.fluyo.presentation.screens.profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qolve.fluyo.BuildConfig
import com.qolve.fluyo.data.requireCurrent
import com.qolve.fluyo.domain.model.Badge
import com.qolve.fluyo.domain.model.BadgeType
import com.qolve.fluyo.domain.model.BudgetExtra
import com.qolve.fluyo.domain.model.NudgeType
import com.qolve.fluyo.domain.model.User
import com.qolve.fluyo.domain.model.UserLevel
import com.qolve.fluyo.domain.model.UserLevelCatalog
import com.qolve.fluyo.domain.model.MoneyAmount
import com.qolve.fluyo.domain.model.WhatsAppLink
import com.qolve.fluyo.domain.model.sumMoney
import android.net.Uri
import com.qolve.fluyo.domain.repository.AuthRepository
import com.qolve.fluyo.domain.repository.BadgeRepository
import com.qolve.fluyo.domain.repository.BudgetExtraRepository
import com.qolve.fluyo.domain.repository.CategoryRepository
import com.qolve.fluyo.domain.repository.ExpenseRepository
import com.qolve.fluyo.domain.repository.SessionBoundary
import com.qolve.fluyo.domain.repository.WhatsAppLinkRepository
import com.qolve.fluyo.domain.time.FluyoTime
import com.qolve.fluyo.notifications.NudgeOneShot
import com.qolve.fluyo.notifications.NudgeScheduler
import com.qolve.fluyo.presentation.util.CsvExporter
import com.qolve.fluyo.presentation.util.CsvExportArtifact
import com.qolve.fluyo.presentation.util.CurrencyState
import com.qolve.fluyo.presentation.util.SUPPORTED_CURRENCIES
import com.qolve.fluyo.presentation.util.sanitizeDecimalInput
import com.qolve.fluyo.presentation.util.WhatsAppLaunchRequest
import com.qolve.fluyo.presentation.util.PendingMutationResolution
import com.qolve.fluyo.presentation.util.StableMutationRequestStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.Instant
import java.math.RoundingMode
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

data class ProfileUiState(
    val isLoading: Boolean = true,
    val user: User? = null,
    val badges: List<Badge> = emptyList(),
    /** Current day-streak of registering an expense (ending today). 0 when broken. */
    val streak: Int = 0,
    val showBudgetDialog: Boolean = false,
    val budgetInput: String = "",
    val isSavingBudget: Boolean = false,
    /** "Ingreso extra del mes" dialog state. */
    val showExtraDialog: Boolean = false,
    val extraAmountInput: String = "",
    val extraNoteInput: String = "",
    val isSavingExtra: Boolean = false,
    val monthExtras: List<BudgetExtra> = emptyList(),
    val deletingExtraIds: Set<String> = emptySet(),
    /** Sender-verified WhatsApp identity; never derived from the legacy profile phone. */
    val whatsAppLink: WhatsAppLink? = null,
    val showWhatsAppDialog: Boolean = false,
    val isLoadingWhatsApp: Boolean = false,
    val isCreatingWhatsAppChallenge: Boolean = false,
    val isUnlinkingWhatsApp: Boolean = false,
    val whatsAppChallengeExpiresAt: Instant? = null,
    /** Currency-picker dialog state. */
    val showCurrencyDialog: Boolean = false,
    val currencyInput: String = "PEN",
    val isSavingCurrency: Boolean = false,
    /** False after the first expense/goal/extra: historical amounts have one fixed denomination. */
    val canChangeCurrency: Boolean = true,
    /** Delete-account confirmation state. */
    val showDeleteDialog: Boolean = false,
    val isDeleting: Boolean = false,
    val errorMessage: String? = null,
) {
    val currency: String get() = user?.currency ?: "PEN"
    val monthExtrasTotal: MoneyAmount get() = monthExtras.map { it.amount }.sumMoney()
    val totalPoints: Int get() = badges.sumOf { it.type.points }
    val currentLevel: UserLevel get() = UserLevelCatalog.levelFor(totalPoints)
    val unlockedTypes: Set<BadgeType> get() = badges.map { it.type }.toSet()
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val badgeRepository: BadgeRepository,
    private val expenseRepository: ExpenseRepository,
    private val categoryRepository: CategoryRepository,
    private val budgetExtraRepository: BudgetExtraRepository,
    private val whatsAppLinkRepository: WhatsAppLinkRepository,
    private val nudgeScheduler: NudgeScheduler,
    private val nudgeOneShot: NudgeOneShot,
    private val currencyState: CurrencyState,
    private val csvExporter: CsvExporter,
    private val sessionBoundary: SessionBoundary,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val userState = MutableStateFlow<User?>(null)
    private val streakState = MutableStateFlow(0)
    private val sheet = MutableStateFlow(SheetState())
    private val notificationSettingsMutex = Mutex()
    private var exportJob: Job? = null
    private val pendingExtraDeletes = ConcurrentHashMap.newKeySet<String>()
    private val pendingExtraRequests = StableMutationRequestStore(
        savedStateHandle,
        namespace = "profile_budget_extra",
    )

    /** One-shot CSV export results — the screen collects this and fires the share sheet. */
    private val csvExports = Channel<Uri>(Channel.BUFFERED)
    val csvExportEvents: Flow<Uri> = csvExports.receiveAsFlow()

    private val whatsAppLaunches = Channel<WhatsAppLaunchRequest>(Channel.UNLIMITED)
    val whatsAppLaunchEvents: Flow<WhatsAppLaunchRequest> = whatsAppLaunches.receiveAsFlow()

    val uiState: StateFlow<ProfileUiState> = combine(
        userState,
        badgeRepository.observeBadges(),
        streakState,
        sheet,
    ) { user, badges, streak, s ->
        ProfileUiState(
            isLoading = s.loading,
            user = user,
            badges = badges,
            streak = streak,
            showBudgetDialog = s.showBudget,
            budgetInput = s.budgetInput,
            isSavingBudget = s.savingBudget,
            showExtraDialog = s.showExtra,
            extraAmountInput = s.extraAmountInput,
            extraNoteInput = s.extraNoteInput,
            isSavingExtra = s.savingExtra,
            monthExtras = s.monthExtras,
            deletingExtraIds = s.deletingExtraIds,
            whatsAppLink = s.whatsAppLink,
            showWhatsAppDialog = s.showWhatsApp,
            isLoadingWhatsApp = s.loadingWhatsApp,
            isCreatingWhatsAppChallenge = s.creatingWhatsAppChallenge,
            isUnlinkingWhatsApp = s.unlinkingWhatsApp,
            whatsAppChallengeExpiresAt = s.whatsAppChallengeExpiresAt,
            showCurrencyDialog = s.showCurrency,
            currencyInput = s.currencyInput,
            isSavingCurrency = s.savingCurrency,
            canChangeCurrency = s.canChangeCurrency,
            showDeleteDialog = s.showDelete,
            isDeleting = s.deleting,
            errorMessage = s.error,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProfileUiState(),
    )

    init {
        refresh()
    }

    fun refresh() {
        val expectedEpoch = sessionBoundary.snapshot()
        sheet.update { it.copy(loading = true, error = null) }
        if (BuildConfig.WHATSAPP_LINKING_ENABLED) refreshWhatsAppLink(expectedEpoch)
        viewModelScope.launch {
            val badgesLoaded = badgeRepository.refresh().isSuccess
            val user = authRepository.currentUser().getOrNull()
            val canChangeCurrency = !authRepository.hasMonetaryActivity().getOrDefault(true)
            val streakResult = expenseRepository.currentStreak()
            sessionBoundary.runIfCurrent(expectedEpoch) {
                userState.value = user
                sheet.update {
                    it.copy(
                        loading = false,
                        canChangeCurrency = canChangeCurrency,
                        error = when {
                            user == null -> "No se pudo cargar el perfil"
                            streakResult.isFailure -> "No se pudo cargar la racha"
                            !badgesLoaded -> "No se pudieron actualizar las medallas"
                            else -> it.error
                        },
                    )
                }
                streakResult.getOrNull()?.let { streakState.value = it }
            }
        }
    }

    fun openBudgetDialog() {
        sheet.update {
            it.copy(
                showBudget = true,
                budgetInput = formatBudgetForInput(userState.value?.monthlyBudget ?: MoneyAmount.ZERO),
            )
        }
        reloadMonthExtras()
    }

    fun closeBudgetDialog() {
        sheet.update { it.copy(showBudget = false, budgetInput = "", savingBudget = false) }
    }

    fun onBudgetInputChange(value: String) {
        sheet.update { it.copy(budgetInput = sanitizeDecimalInput(value)) }
    }

    fun saveBudget() {
        val expectedEpoch = sessionBoundary.snapshot()
        val raw = MoneyAmount.parse(sheet.value.budgetInput, RoundingMode.UNNECESSARY)
            ?.takeIf { it >= MoneyAmount.ZERO }
            ?: return
        sheet.update { it.copy(savingBudget = true, error = null) }
        viewModelScope.launch {
            val result = authRepository.updateProfile(monthlyBudget = raw)
            result.fold(
                onSuccess = { updated ->
                    if (sessionBoundary.runIfCurrent(expectedEpoch) {
                            userState.value = updated
                            sheet.update {
                                it.copy(showBudget = false, savingBudget = false, budgetInput = "")
                            }
                        }
                    ) {
                        // Re-query the breakdown so the Home ring reflects the new base at once.
                        expenseRepository.refresh()
                    }
                },
                onFailure = {
                    sessionBoundary.runIfCurrent(expectedEpoch) {
                        sheet.update {
                            it.copy(savingBudget = false, error = "No se pudo actualizar el presupuesto")
                        }
                    }
                },
            )
        }
    }

    // ─── "Ingreso extra del mes" (adds to the current month only) ───────────

    private fun reloadMonthExtras(expectedEpoch: Long = sessionBoundary.snapshot()) {
        viewModelScope.launch {
            budgetExtraRepository.extrasForMonth(FluyoTime.currentMonth()).fold(
                onSuccess = { extras ->
                    sessionBoundary.runIfCurrent(expectedEpoch) {
                        sheet.update { it.copy(monthExtras = extras) }
                    }
                },
                onFailure = {
                    sessionBoundary.runIfCurrent(expectedEpoch) {
                        sheet.update {
                            it.copy(error = "No se pudieron cargar los ingresos del mes")
                        }
                    }
                },
            )
        }
    }

    fun openExtraDialog() {
        sheet.update { it.copy(showExtra = true, extraAmountInput = "", extraNoteInput = "") }
    }

    fun closeExtraDialog() {
        sheet.update { it.copy(showExtra = false, extraAmountInput = "", extraNoteInput = "", savingExtra = false) }
    }

    fun onExtraAmountChange(value: String) {
        sheet.update { it.copy(extraAmountInput = sanitizeDecimalInput(value)) }
    }

    fun onExtraNoteChange(value: String) {
        sheet.update { it.copy(extraNoteInput = value.take(60)) }
    }

    fun saveExtra() {
        if (sheet.value.savingExtra) return
        val expectedEpoch = sessionBoundary.snapshot()
        val amount = MoneyAmount.parse(sheet.value.extraAmountInput, RoundingMode.UNNECESSARY)
            ?.takeIf { it > MoneyAmount.ZERO }
            ?: return
        val note = sheet.value.extraNoteInput.trim().takeIf { it.isNotEmpty() }
        val month = FluyoTime.currentMonth()
        val payload = arrayOf(
            amount.cents.toString(),
            note,
            month.toString(),
        )
        sheet.update { it.copy(savingExtra = true, error = null) }
        viewModelScope.launch {
            var submittedRequestId: String? = null
            val resolution = pendingExtraRequests.resolve(
                *payload,
                findCommitted = budgetExtraRepository::findCreatedByRequestId,
            ).getOrElse {
                sessionBoundary.runIfCurrent(expectedEpoch) {
                    sheet.update { state ->
                        state.copy(
                            savingExtra = false,
                            error = "No se pudo verificar el intento anterior",
                        )
                    }
                }
                return@launch
            }
            val result = when (resolution) {
                is PendingMutationResolution.Committed -> Result.success(resolution.value)
                is PendingMutationResolution.Ready -> {
                    submittedRequestId = resolution.requestId
                    budgetExtraRepository.addExtra(
                        amount,
                        note,
                        month,
                        resolution.requestId,
                    )
                }
            }
            result.fold(
                onSuccess = {
                    submittedRequestId?.let(pendingExtraRequests::complete)
                    if (sessionBoundary.runIfCurrent(expectedEpoch) {
                            sheet.update {
                                it.copy(
                                    showExtra = false,
                                    extraAmountInput = "",
                                    extraNoteInput = "",
                                    savingExtra = false,
                                )
                            }
                        }
                    ) {
                        reloadMonthExtras(expectedEpoch)
                        expenseRepository.refresh()
                    }
                },
                onFailure = {
                    sessionBoundary.runIfCurrent(expectedEpoch) {
                        sheet.update { it.copy(savingExtra = false, error = "No se pudo agregar el ingreso") }
                    }
                },
            )
        }
    }

    fun deleteExtra(extra: BudgetExtra) {
        if (!pendingExtraDeletes.add(extra.id)) return
        val expectedEpoch = sessionBoundary.snapshot()
        if (!sessionBoundary.runIfCurrent(expectedEpoch) {
                sheet.update {
                    it.copy(
                        deletingExtraIds = it.deletingExtraIds + extra.id,
                        error = null,
                    )
                }
            }
        ) {
            pendingExtraDeletes.remove(extra.id)
            return
        }

        viewModelScope.launch {
            try {
                budgetExtraRepository.deleteExtra(extra.id).fold(
                    onSuccess = {
                        if (sessionBoundary.runIfCurrent(expectedEpoch) {
                                sheet.update { state ->
                                    state.copy(
                                        monthExtras = state.monthExtras.filterNot { it.id == extra.id },
                                        error = null,
                                    )
                                }
                            }
                        ) {
                            reloadMonthExtras(expectedEpoch)
                            expenseRepository.refresh()
                        }
                    },
                    onFailure = {
                        sessionBoundary.runIfCurrent(expectedEpoch) {
                            sheet.update { state ->
                                state.copy(error = "No se pudo eliminar el ingreso")
                            }
                        }
                    },
                )
            } finally {
                pendingExtraDeletes.remove(extra.id)
                sessionBoundary.runIfCurrent(expectedEpoch) {
                    sheet.update { state ->
                        state.copy(deletingExtraIds = state.deletingExtraIds - extra.id)
                    }
                }
            }
        }
    }

    fun consumeError() {
        sheet.update { it.copy(error = null) }
    }

    // ─── Verified WhatsApp sender linkage ──────────────────────────────────

    fun openWhatsAppDialog() {
        if (!BuildConfig.WHATSAPP_LINKING_ENABLED) return
        sheet.update { it.copy(showWhatsApp = true, error = null) }
        refreshWhatsAppLink()
    }

    fun closeWhatsAppDialog() {
        // Network work is intentionally not cancelled by closing the dialog. Its result keeps
        // the settings row current and the in-flight flag prevents duplicate RPC requests.
        sheet.update { it.copy(showWhatsApp = false, error = null) }
    }

    fun refreshWhatsAppLink() = refreshWhatsAppLink(sessionBoundary.snapshot())

    private fun refreshWhatsAppLink(expectedEpoch: Long) {
        if (!BuildConfig.WHATSAPP_LINKING_ENABLED) return
        if (sheet.value.loadingWhatsApp) return
        sheet.update { it.copy(loadingWhatsApp = true, error = null) }
        viewModelScope.launch {
            whatsAppLinkRepository.currentLink().fold(
                onSuccess = { link ->
                    sessionBoundary.runIfCurrent(expectedEpoch) {
                        sheet.update {
                            it.copy(
                                whatsAppLink = link,
                                loadingWhatsApp = false,
                                whatsAppChallengeExpiresAt = if (link == null) {
                                    it.whatsAppChallengeExpiresAt
                                } else {
                                    null
                                },
                            )
                        }
                    }
                },
                onFailure = {
                    sessionBoundary.runIfCurrent(expectedEpoch) {
                        sheet.update {
                            it.copy(
                                loadingWhatsApp = false,
                                error = "No se pudo consultar WhatsApp",
                            )
                        }
                    }
                },
            )
        }
    }

    fun createWhatsAppChallenge() {
        if (!BuildConfig.WHATSAPP_LINKING_ENABLED) return
        if (sheet.value.loadingWhatsApp ||
            sheet.value.creatingWhatsAppChallenge ||
            sheet.value.unlinkingWhatsApp
        ) return
        val expectedEpoch = sessionBoundary.snapshot()
        sheet.update { it.copy(creatingWhatsAppChallenge = true, error = null) }
        viewModelScope.launch {
            whatsAppLinkRepository.createChallenge().fold(
                onSuccess = { challenge ->
                    sessionBoundary.runIfCurrent(expectedEpoch) {
                        sheet.update {
                            it.copy(
                                creatingWhatsAppChallenge = false,
                                whatsAppChallengeExpiresAt = challenge.expiresAt,
                            )
                        }
                        whatsAppLaunches.trySend(WhatsAppLaunchRequest(challenge.message))
                    }
                },
                onFailure = {
                    sessionBoundary.runIfCurrent(expectedEpoch) {
                        sheet.update {
                            it.copy(
                                creatingWhatsAppChallenge = false,
                                error = "No se pudo iniciar la verificación",
                            )
                        }
                    }
                },
            )
        }
    }

    fun unlinkWhatsApp() {
        if (!BuildConfig.WHATSAPP_LINKING_ENABLED) return
        if (sheet.value.loadingWhatsApp ||
            sheet.value.creatingWhatsAppChallenge ||
            sheet.value.unlinkingWhatsApp
        ) return
        val expectedEpoch = sessionBoundary.snapshot()
        sheet.update { it.copy(unlinkingWhatsApp = true, error = null) }
        viewModelScope.launch {
            whatsAppLinkRepository.unlink().fold(
                onSuccess = {
                    sessionBoundary.runIfCurrent(expectedEpoch) {
                        sheet.update {
                            it.copy(
                                whatsAppLink = null,
                                unlinkingWhatsApp = false,
                                whatsAppChallengeExpiresAt = null,
                            )
                        }
                    }
                },
                onFailure = {
                    sessionBoundary.runIfCurrent(expectedEpoch) {
                        sheet.update {
                            it.copy(
                                unlinkingWhatsApp = false,
                                error = "No se pudo desvincular WhatsApp",
                            )
                        }
                    }
                },
            )
        }
    }

    // ─── Currency picker (HU-11) ─────────────────────────────────────────────
    fun openCurrencyDialog() {
        val expectedEpoch = sessionBoundary.snapshot()
        viewModelScope.launch {
            // Re-check on every attempt: another device or the WhatsApp backend may have
            // created activity since this ViewModel last refreshed.
            val canChange = authRepository.hasMonetaryActivity()
                .fold(onSuccess = { hasActivity -> !hasActivity }, onFailure = { false })
            sessionBoundary.runIfCurrent(expectedEpoch) {
                sheet.update {
                    if (canChange) {
                        it.copy(
                            showCurrency = true,
                            currencyInput = userState.value?.currency ?: "PEN",
                            canChangeCurrency = true,
                            error = null,
                        )
                    } else {
                        it.copy(
                            showCurrency = true,
                            canChangeCurrency = false,
                            error = "La moneda no puede cambiar después de registrar actividad financiera.",
                        )
                    }
                }
            }
        }
    }

    fun closeCurrencyDialog() {
        sheet.update { it.copy(showCurrency = false, savingCurrency = false, error = null) }
    }

    fun onCurrencySelect(code: String) {
        sheet.update { it.copy(currencyInput = code) }
    }

    fun saveCurrency() {
        if (!sheet.value.canChangeCurrency) {
            sheet.update {
                it.copy(
                    showCurrency = true,
                    savingCurrency = false,
                    error = "La moneda no puede cambiar después de registrar actividad financiera.",
                )
            }
            return
        }
        val expectedEpoch = sessionBoundary.snapshot()
        val code = sheet.value.currencyInput.takeIf { it in SUPPORTED_CURRENCIES } ?: return
        sheet.update { it.copy(savingCurrency = true, error = null) }
        viewModelScope.launch {
            authRepository.updateProfile(currency = code).fold(
                onSuccess = { updated ->
                    sessionBoundary.runIfCurrent(expectedEpoch) {
                        userState.value = updated
                        currencyState.set(updated.currency)
                        sheet.update { it.copy(showCurrency = false, savingCurrency = false) }
                    }
                },
                onFailure = { e ->
                    // The database trigger is authoritative and closes races with activity
                    // created on another device while this dialog was open.
                    sessionBoundary.runIfCurrent(expectedEpoch) {
                        sheet.update {
                            val denominationLocked = e.message
                                ?.contains("Currency cannot change", ignoreCase = true) == true
                            it.copy(
                                showCurrency = true,
                                savingCurrency = false,
                                canChangeCurrency = !denominationLocked,
                                error = if (denominationLocked) {
                                    "La moneda no puede cambiar después de registrar actividad financiera."
                                } else {
                                    "No se pudo cambiar la moneda"
                                },
                            )
                        }
                    }
                },
            )
        }
    }

    // ─── CSV export (HU-11) ──────────────────────────────────────────────────
    fun exportCsv() {
        if (exportJob?.isActive == true) return
        val expectedEpoch = sessionBoundary.snapshot()
        exportJob = viewModelScope.launch {
            var artifact: CsvExportArtifact? = null
            var published = false
            try {
                val user = authRepository.currentUser().getOrThrow()
                    ?: error("Authenticated profile is unavailable")
                sessionBoundary.requireCurrent(expectedEpoch)
                categoryRepository.refresh().getOrThrow()
                sessionBoundary.requireCurrent(expectedEpoch)
                val expenses = expenseRepository
                    .loadByDateRange(LocalDate.of(2000, 1, 1), FluyoTime.today())
                    .getOrThrow()
                val names = categoryRepository.observeCategories().first()
                    .associate { it.id to it.name }
                sessionBoundary.requireCurrent(expectedEpoch)
                artifact = csvExporter.export(expenses, names, user.currency) {
                    // Executed while holding the same file lock as sign-out cleanup.
                    sessionBoundary.requireCurrent(expectedEpoch)
                }
                val readyArtifact = checkNotNull(artifact)
                published = sessionBoundary.runIfCurrent(expectedEpoch) {
                    check(csvExports.trySend(readyArtifact.uri).isSuccess) {
                        "CSV export event queue is unavailable"
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                sessionBoundary.runIfCurrent(expectedEpoch) {
                    sheet.update { it.copy(error = "No se pudo exportar") }
                }
            } finally {
                if (!published) {
                    artifact?.let { staleArtifact ->
                        withContext(NonCancellable + Dispatchers.IO) {
                            runCatching { csvExporter.discard(staleArtifact) }
                        }
                    }
                }
            }
        }
    }

    // ─── Delete account (HU-11) ──────────────────────────────────────────────
    fun openDeleteDialog() {
        sheet.update { it.copy(showDelete = true, error = null) }
    }

    fun closeDeleteDialog() {
        if (sheet.value.deleting) return
        sheet.update { it.copy(showDelete = false, error = null) }
    }

    fun deleteAccount() {
        if (sheet.value.deleting) return
        val expectedEpoch = sessionBoundary.snapshot()
        sheet.update { it.copy(deleting = true, error = null) }
        viewModelScope.launch {
            // On success the auth state flips to SignedOut and RootViewModel routes to login.
            authRepository.deleteAccount().onFailure {
                sessionBoundary.runIfCurrent(expectedEpoch) {
                    sheet.update { it.copy(deleting = false, error = "No se pudo eliminar la cuenta") }
                }
            }
        }
    }

    fun signOut() {
        val expectedEpoch = sessionBoundary.snapshot()
        viewModelScope.launch {
            authRepository.signOut().onFailure {
                sessionBoundary.runIfCurrent(expectedEpoch) {
                    sheet.update { it.copy(error = "No se pudo cerrar la sesión completamente") }
                }
            }
        }
    }

    /** Manual smoke-test trigger: enqueues a one-time nudge worker. */
    fun fireTestNudge() {
        val expectedEpoch = sessionBoundary.snapshot()
        sessionBoundary.runIfCurrent(expectedEpoch) { nudgeOneShot.fireNow() }
    }

    fun toggleNotificationsEnabled(enabled: Boolean) {
        val expectedEpoch = sessionBoundary.snapshot()
        viewModelScope.launch {
            notificationSettingsMutex.withLock {
                authRepository.updateNotificationSettings(enabled = enabled).fold(
                    onSuccess = { updated ->
                        sessionBoundary.runIfCurrent(expectedEpoch) {
                            userState.value = updated
                            if (updated.notificationEnabled) {
                                nudgeScheduler.schedule(updated.notificationHour)
                            } else {
                                nudgeScheduler.cancel()
                            }
                        }
                    },
                    onFailure = {
                        sessionBoundary.runIfCurrent(expectedEpoch) {
                            sheet.update { it.copy(error = "No se pudo actualizar la configuración") }
                        }
                    },
                )
            }
        }
    }

    fun setNotificationHour(hour: Int) {
        val safeHour = hour.coerceIn(0, 23)
        val expectedEpoch = sessionBoundary.snapshot()
        viewModelScope.launch {
            notificationSettingsMutex.withLock {
                authRepository.updateNotificationSettings(hour = safeHour).fold(
                    onSuccess = { updated ->
                        sessionBoundary.runIfCurrent(expectedEpoch) {
                            userState.value = updated
                            if (updated.notificationEnabled) {
                                nudgeScheduler.schedule(updated.notificationHour)
                            }
                        }
                    },
                    onFailure = {
                        sessionBoundary.runIfCurrent(expectedEpoch) {
                            sheet.update { it.copy(error = "No se pudo actualizar la configuración") }
                        }
                    },
                )
            }
        }
    }

    fun toggleNotificationType(type: NudgeType, enabled: Boolean) {
        val expectedEpoch = sessionBoundary.snapshot()
        viewModelScope.launch {
            notificationSettingsMutex.withLock {
                // Compute after preceding mutations have completed; otherwise rapid taps
                // can all derive from the same stale set and silently re-enable a type.
                val current = userState.value?.notificationTypes ?: NudgeType.entries.toSet()
                val next = if (enabled) current + type else current - type
                authRepository.updateNotificationSettings(types = next).fold(
                    onSuccess = { updated ->
                        sessionBoundary.runIfCurrent(expectedEpoch) { userState.value = updated }
                    },
                    onFailure = {
                        sessionBoundary.runIfCurrent(expectedEpoch) {
                            sheet.update { it.copy(error = "No se pudo actualizar la configuración") }
                        }
                    },
                )
            }
        }
    }

    private fun formatBudgetForInput(value: MoneyAmount): String =
        value.toBigDecimal().stripTrailingZeros().toPlainString()

    private data class SheetState(
        val loading: Boolean = true,
        val showBudget: Boolean = false,
        val budgetInput: String = "",
        val savingBudget: Boolean = false,
        val showExtra: Boolean = false,
        val extraAmountInput: String = "",
        val extraNoteInput: String = "",
        val savingExtra: Boolean = false,
        val monthExtras: List<BudgetExtra> = emptyList(),
        val deletingExtraIds: Set<String> = emptySet(),
        val whatsAppLink: WhatsAppLink? = null,
        val showWhatsApp: Boolean = false,
        val loadingWhatsApp: Boolean = false,
        val creatingWhatsAppChallenge: Boolean = false,
        val unlinkingWhatsApp: Boolean = false,
        val whatsAppChallengeExpiresAt: Instant? = null,
        val showCurrency: Boolean = false,
        val currencyInput: String = "PEN",
        val savingCurrency: Boolean = false,
        val canChangeCurrency: Boolean = true,
        val showDelete: Boolean = false,
        val deleting: Boolean = false,
        val error: String? = null,
    )
}
