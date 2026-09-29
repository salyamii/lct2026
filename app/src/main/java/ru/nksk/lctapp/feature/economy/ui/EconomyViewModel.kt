package ru.nksk.lctapp.feature.economy.ui

import ru.nksk.lctapp.core.ui.game.asGameUiText

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.nksk.lctapp.core.ui.game.BudgetHistoryUi
import ru.nksk.lctapp.core.ui.game.budgetHistoryUi
import ru.nksk.lctapp.core.ui.game.playerMessage
import ru.nksk.lctapp.domain.analytics.DecisionContext
import ru.nksk.lctapp.domain.analytics.FinancialPosition
import ru.nksk.lctapp.domain.economy.*
import ru.nksk.lctapp.domain.engine.*
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.pet.PetState
import ru.nksk.lctapp.domain.pet.renderPetText

internal data class EconomyUiState(
    val loading: Boolean = true,
    val economy: EconomyState? = null,
    val saving: Boolean = false,
    val error: String? = null,
    val revisionReason: BudgetRevisionReason = BudgetRevisionReason.UNSPECIFIED,
    val depositWarning: BlockReason.FoodBudgetWarning? = null,
    val knownNeeds: Long = 0,
    val unexpectedExpenses: List<UnexpectedExpenseUi> = emptyList(),
    val expenseHistoryLoading: Boolean = true,
    val expenseHistoryUnavailable: Boolean = false,
    val selectedExpenseOperationId: String? = null,
    val pet: PetState? = null,
    val revision: Long? = null,
    val savingsTarget: SavingsTargetUi? = null,
    val savingsStep: SavingsStep = SavingsStep.DEPOSIT,
    val transferInput: String = "",
    val withdrawal: WithdrawalPreview? = null,
    val transferReceipt: SavingsReceipt? = null,
    val exclusiveSaving: Boolean = false,
    val budgetConfirmation: BudgetScreenState? = null,
    val budgetHistory: BudgetHistoryUi? = null,
    val budgetHistoryVisible: Boolean = false,
    val budgetHistoryLoading: Boolean = false,
    val budgetHistoryError: String? = null,
) {
    /** Relative edits can queue while a previous edit is being persisted. Final actions cannot. */
    val planEditingEnabled: Boolean get() = !loading && economy != null && error == null &&
        !exclusiveSaving && budgetConfirmation == null
    val confirmationContextId: String? get() = economy?.planning
        ?.takeIf { it.stage == BudgetPlanningStage.ALLOCATION }
        ?.let { "budget:${it.id}:${it.revision}" }
    val transferContextId: String? get() = economy?.let {
        "savings:$revision:$savingsStep:${it.availableBalance}:${it.savingsBalance}:$knownNeeds:${it.plan}" +
            ":$savingsTarget:${transferInput.toLongOrNull()}:confirm=${withdrawal?.amount}"
    }
}

internal sealed interface EconomyAction {
    data object StartAllocation : EconomyAction
    data object OpenBudgetHistory : EconomyAction
    data object CloseBudgetHistory : EconomyAction
    data class Adjust(val article: BudgetArticle, val increase: Boolean) : EconomyAction
    data class SetAmount(val article: BudgetArticle, val amount: Long) : EconomyAction
    data class SetReason(val reason: BudgetRevisionReason) : EconomyAction
    data class ContextPresented(val id: String) : EconomyAction
    data class TransferContextPresented(val id: String) : EconomyAction
    data class SelectUnexpectedExpense(val operationId: String?) : EconomyAction
    data object RetryExpenseHistory : EconomyAction
    data class Deposit(val amount: Long) : EconomyAction
    data class Withdraw(val amount: Long) : EconomyAction
    data class OpenTransfer(val withdrawing: Boolean) : EconomyAction
    data class UpdateTransferInput(val input: String) : EconomyAction
    data object ClearTransferReceipt : EconomyAction
    data object OpenSavingsHub : EconomyAction
    data object ConfirmWithdrawal : EconomyAction
    data object CancelWithdrawal : EconomyAction
    data object ConfirmDepositRisk : EconomyAction
    data object CancelDepositRisk : EconomyAction
    data object Confirm : EconomyAction
    data object Retry : EconomyAction
    data object DismissError : EconomyAction
}

internal data class BudgetCompletion(val planningId: String?)

/** Every durable outcome goes through the same game command transaction and analytics. */
@HiltViewModel
internal class EconomyViewModel @Inject constructor(private val session: GameSession) : ViewModel() {
    private val state = MutableStateFlow(EconomyUiState())
    val uiState = state.asStateFlow()
    private val completions = Channel<BudgetCompletion>(Channel.BUFFERED)
    val completed = completions.receiveAsFlow()
    private data class Pending(val action: EconomyAction, val id: String = UUID.randomUUID().toString(),
        var request: EngineRequest? = null, val context: DecisionContext? = null,
        val revisionReason: BudgetRevisionReason = BudgetRevisionReason.UNSPECIFIED,
        val causeActionId: String? = null, val shownRevision: Long? = null,
        val transferExpectation: SavingsTransferExpectation? = null,
        val budgetEditBase: Long? = null)
    private val actions = Channel<Pending>(Channel.UNLIMITED)
    private var observation: Job? = null
    private var screenActive = true
    private var expenseHistory: Job? = null
    private var budgetHistoryJob: Job? = null
    private var budgetHistoryRequest = 0L
    private var projectedGame: GameState? = null
    private var failed: Pending? = null
    private var depositRisk: Pending? = null
    private var presentedContextId: String? = null
    private var presentedTransferContextId: String? = null
    private var queued = 0
    private var exclusiveQueued = 0
    private val manualId = "manual:" + UUID.randomUUID().toString()

    init {
        observe()
        viewModelScope.launch {
            for (pending in actions) {
                if (failed != null) {
                    finish(pending)
                    continue
                }
                save(pending)
            }
        }
    }

    fun setActive(active: Boolean) {
        if (screenActive == active) return
        screenActive = active
        if (active) observe() else {
            observation?.cancel(); observation = null
            expenseHistory?.cancel()
            budgetHistoryJob?.cancel()
        }
    }

    private fun observe() {
        if (!screenActive || observation?.isActive == true) return
        observation = viewModelScope.launch {
            try {
                session.prepare()
                if (expenseHistory == null || expenseHistory?.isCancelled == true) loadExpenseHistory()
                session.observe().collect { game ->
                    checkNotNull(game) { "Game is not initialized" }
                    project(game)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                state.value = state.value.copy(loading = false, error = "Не удалось прочитать бюджет. Попробуй ещё раз.")
            }
        }
    }

    fun onAction(action: EconomyAction) {
        // Keep the successful outgoing screen locked until its navigation entry is removed.
        if (state.value.budgetConfirmation != null) return
        when (action) {
            EconomyAction.OpenBudgetHistory -> {
                val shown = state.value
                if (!shown.planEditingEnabled || shown.saving || shown.economy?.planning != null) return
                loadBudgetHistory()
                return
            }
            EconomyAction.CloseBudgetHistory -> {
                budgetHistoryRequest++
                budgetHistoryJob?.cancel()
                budgetHistoryJob = null
                state.value = state.value.copy(budgetHistoryVisible = false, budgetHistory = null,
                    budgetHistoryLoading = false, budgetHistoryError = null)
                return
            }
            is EconomyAction.TransferContextPresented -> {
                if (action.id == state.value.transferContextId) presentedTransferContextId = action.id
                return
            }
            EconomyAction.OpenSavingsHub -> {
                if (state.value.saving) return
                depositRisk = null
                presentedTransferContextId = null
                state.value = state.value.copy(savingsStep = SavingsStep.DEPOSIT, transferInput = "", withdrawal = null,
                    transferReceipt = null, depositWarning = null)
                return
            }
            is EconomyAction.OpenTransfer -> {
                val shown = state.value
                val economy = shown.economy ?: return
                if (shown.saving || shown.error != null || economy.planning != null || economy.unallocated != 0L) return
                val direction = if (action.withdrawing) SavingsStep.WITHDRAW else SavingsStep.DEPOSIT
                if (direction == shown.savingsStep && shown.withdrawal == null &&
                    shown.depositWarning == null && shown.transferReceipt == null) return
                depositRisk = null
                presentedTransferContextId = null
                state.value = shown.copy(savingsStep = direction,
                    transferInput = if (direction == shown.savingsStep) shown.transferInput else "",
                    withdrawal = null, transferReceipt = null, depositWarning = null)
                return
            }
            is EconomyAction.UpdateTransferInput -> {
                val shown = state.value
                if (shown.saving || shown.error != null || shown.withdrawal != null || shown.depositWarning != null ||
                    action.input.length > 19 || action.input.any { it !in '0'..'9' }) return
                if (shown.transferInput != action.input) presentedTransferContextId = null
                state.value = shown.copy(transferInput = action.input, transferReceipt = null)
                return
            }
            EconomyAction.ClearTransferReceipt -> {
                if (!state.value.saving) state.value = state.value.copy(transferReceipt = null)
                return
            }
            is EconomyAction.Withdraw -> {
                val shown = state.value
                val economy = shown.economy ?: return
                if (shown.saving || shown.error != null || economy.planning != null || economy.unallocated != 0L ||
                    action.amount <= 0 || action.amount > economy.savingsBalance) return
                depositRisk = null
                presentedTransferContextId = null
                state.value = shown.copy(savingsStep = SavingsStep.WITHDRAW, depositWarning = null, transferReceipt = null,
                    withdrawal = WithdrawalPreview(action.amount, economy.availableBalance, economy.savingsBalance,
                        shown.revision, shown.savingsTarget, shown.knownNeeds))
                return
            }
            EconomyAction.CancelWithdrawal -> {
                if (state.value.saving) return
                presentedTransferContextId = null
                state.value = state.value.copy(savingsStep = SavingsStep.WITHDRAW, withdrawal = null)
                return
            }
            EconomyAction.ConfirmWithdrawal -> {
                val shown = state.value
                val preview = shown.withdrawal ?: return
                val economy = shown.economy ?: return
                if (shown.saving || shown.error != null || shown.savingsStep != SavingsStep.WITHDRAW) return
                if (preview.revision != shown.revision || preview.available != economy.availableBalance ||
                    preview.savings != economy.savingsBalance || preview.target != shown.savingsTarget) {
                    state.value = shown.copy(savingsStep = SavingsStep.WITHDRAW, withdrawal = null)
                    return
                }
                val transfer = EconomyAction.Withdraw(preview.amount)
                enqueue(Pending(transfer, context = shownContext(transfer), shownRevision = preview.revision,
                    transferExpectation = SavingsTransferExpectation(preview.available, preview.savings, preview.target?.id)))
                return
            }
            is EconomyAction.ContextPresented -> {
                if (action.id == state.value.confirmationContextId) presentedContextId = action.id
                return
            }
            EconomyAction.ConfirmDepositRisk -> {
                if (state.value.saving || state.value.error != null) return
                val pending = depositRisk ?: return
                val request = pending.request ?: return
                val command = request.command as? EngineCommand.DepositSavings ?: return
                depositRisk = null
                state.value = state.value.copy(depositWarning = null)
                val id = UUID.randomUUID().toString()
                enqueue(pending.copy(id = id, request = request.copy(
                    id = id, command = command.copy(acceptFoodRisk = true))))
                return
            }
            EconomyAction.CancelDepositRisk -> {
                if (state.value.saving) return
                depositRisk = null
                state.value = state.value.copy(depositWarning = null)
                return
            }
            EconomyAction.DismissError -> {
                failed = null
                state.value = state.value.copy(error = null)
                return
            }
            EconomyAction.Retry -> {
                if (state.value.saving) return
                val retry = failed
                failed = null
                state.value = state.value.copy(error = null)
                if (retry != null) enqueue(retry) else observe()
                return
            }
            is EconomyAction.SetReason -> {
                if (state.value.exclusiveSaving) return
                state.value = state.value.copy(revisionReason = action.reason,
                    selectedExpenseOperationId = state.value.selectedExpenseOperationId
                        .takeIf { action.reason == BudgetRevisionReason.UNEXPECTED_EXPENSE })
                return
            }
            is EconomyAction.SelectUnexpectedExpense -> {
                val shown = state.value
                if (shown.saving || shown.revisionReason != BudgetRevisionReason.UNEXPECTED_EXPENSE) return
                if (action.operationId != null && shown.unexpectedExpenses.none { it.operationId == action.operationId }) return
                state.value = shown.copy(selectedExpenseOperationId = action.operationId)
                return
            }
            EconomyAction.RetryExpenseHistory -> {
                if (expenseHistory?.isActive != true) loadExpenseHistory()
                return
            }
            else -> Unit
        }
        if (state.value.error != null || state.value.economy == null) return
        if (action.isPlanEdit()) {
            if (!state.value.planEditingEnabled) return
        } else if (state.value.saving) return
        enqueue(Pending(action, context = shownContext(action), revisionReason = state.value.revisionReason,
            causeActionId = state.value.selectedExpenseOperationId
                .takeIf { state.value.revisionReason == BudgetRevisionReason.UNEXPECTED_EXPENSE },
            shownRevision = state.value.revision,
            transferExpectation = if (action is EconomyAction.Deposit) state.value.economy?.let {
                SavingsTransferExpectation(it.availableBalance, it.savingsBalance, state.value.savingsTarget?.id, it.plan)
            } else null,
            budgetEditBase = if (action.isPlanEdit()) state.value.economy?.availableBalance else null))
    }

    private fun loadExpenseHistory() {
        if (!screenActive) return
        expenseHistory = viewModelScope.launch {
            state.value = state.value.copy(expenseHistoryLoading = true, expenseHistoryUnavailable = false)
            try {
                val history = session.expenseRecoveryHistory()
                val options = withContext(Dispatchers.Default) { unexpectedExpenseOptions(history, session.catalog) }
                state.value = state.value.copy(unexpectedExpenses = options, expenseHistoryLoading = false,
                    selectedExpenseOperationId = state.value.selectedExpenseOperationId
                        .takeIf { id -> options.any { it.operationId == id } })
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                state.value = state.value.copy(unexpectedExpenses = emptyList(), expenseHistoryLoading = false,
                    expenseHistoryUnavailable = true, selectedExpenseOperationId = null)
            }
        }
    }

    private fun knownNeeds(game: GameState): Long = session.catalog.mealPolicy.foodRequirement(game)

    /** A history response belongs only to the exact saved state that was displayed when requested. */
    private fun loadBudgetHistory() {
        if (!screenActive) return
        val snapshot = projectedGame ?: return
        budgetHistoryJob?.cancel()
        val request = ++budgetHistoryRequest
        state.value = state.value.copy(budgetHistoryVisible = true, budgetHistory = null,
            budgetHistoryLoading = true, budgetHistoryError = null)
        budgetHistoryJob = viewModelScope.launch {
            fun isCurrent() = request == budgetHistoryRequest && state.value.budgetHistoryVisible && projectedGame == snapshot
            try {
                val history = snapshot.financial.plans.lastOrNull()?.id
                    ?.let { session.budgetPlanHistory(it) }.orEmpty()
                val result = withContext(Dispatchers.Default) { budgetHistoryUi(snapshot, history) }
                if (!isCurrent()) return@launch
                state.value = state.value.copy(budgetHistory = result, budgetHistoryLoading = false,
                    budgetHistoryError = if (result == null)
                        "В истории не хватает данных, чтобы точно показать все изменения после этого плана." else null)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (!isCurrent()) return@launch
                state.value = state.value.copy(budgetHistory = null, budgetHistoryLoading = false,
                    budgetHistoryError = "Не удалось загрузить историю. Попробуй ещё раз.")
            }
        }
    }

    private fun project(game: GameState) {
        val changed = projectedGame != game
        projectedGame = game
        val old = state.value.economy
        if (old != null && state.value.depositWarning != null && changed) {
            depositRisk = null
            state.value = state.value.copy(depositWarning = null)
        }
        val target = game.selectedSavingItemId?.let { id -> session.catalog.content.items.firstOrNull { it.id == id } }
            ?.let { item -> item.priceCoins?.let { SavingsTargetUi(item.id, renderPetText(item.name, game.pet.name).asGameUiText(), it) } }
        val confirmationChanged = state.value.withdrawal?.let {
            it.revision != game.engine?.revision || it.available != game.economy.availableBalance ||
                it.savings != game.economy.savingsBalance || it.target != target
        } == true
        state.value = state.value.copy(loading = false, economy = game.economy, knownNeeds = knownNeeds(game),
            pet = game.pet, revision = game.engine?.revision, savingsTarget = target,
            withdrawal = state.value.withdrawal.takeUnless { confirmationChanged },
            savingsStep = if (confirmationChanged) SavingsStep.WITHDRAW else state.value.savingsStep)
        if (screenActive && state.value.budgetHistoryVisible && (changed || budgetHistoryJob?.isCancelled == true)) loadBudgetHistory()
    }

    /** Captured from the rendered model, never invented from a fresh invisible storage read. */
    private fun shownContext(action: EconomyAction): DecisionContext? {
        if (action != EconomyAction.Confirm && action !is EconomyAction.Deposit && action !is EconomyAction.Withdraw) return null
        val shown = state.value
        val economy = shown.economy ?: return null
        val planning = economy.planning
        if (action == EconomyAction.Confirm && planning?.stage != BudgetPlanningStage.ALLOCATION) return null
        val presented = if (action == EconomyAction.Confirm)
            shown.confirmationContextId != null && shown.confirmationContextId == presentedContextId
        else shown.transferContextId != null && shown.transferContextId == presentedTransferContextId && when (action) {
            is EconomyAction.Deposit -> shown.savingsStep == SavingsStep.DEPOSIT && shown.transferInput.toLongOrNull() == action.amount
            is EconomyAction.Withdraw -> shown.withdrawal?.amount == action.amount
            else -> false
        }
        return DecisionContext(
            presentationId = if (action == EconomyAction.Confirm) "budget:${planning?.id}:${planning?.revision}"
                else shown.transferContextId,
            informationPresented = presented, complete = presented,
            before = FinancialPosition(economy.availableBalance, economy.savingsBalance, shown.knownNeeds),
            alternativeAvailable = true,
        )
    }

    private fun enqueue(pending: Pending) {
        queued++
        if (!pending.action.isPlanEdit()) exclusiveQueued++
        val confirmation = if (pending.action == EconomyAction.Confirm) state.value.budgetScreenState() else null
        state.value = state.value.copy(saving = true, exclusiveSaving = exclusiveQueued > 0,
            budgetConfirmation = confirmation)
        actions.trySend(pending)
    }

    private fun finish(pending: Pending) {
        queued--
        if (!pending.action.isPlanEdit()) exclusiveQueued--
        state.value = state.value.copy(saving = queued > 0, exclusiveSaving = exclusiveQueued > 0)
    }

    private suspend fun save(pending: Pending) {
        try {
            val current = checkNotNull(session.read())
            val planning = current.economy.planning
            if (pending.action.isPlanEdit() && pending.request == null && planning == null &&
                pending.budgetEditBase != current.economy.availableBalance) {
                project(current)
                state.value = state.value.copy(error = "Количество монет изменилось. Проверь план ещё раз.")
                return
            }
            if (pending.action == EconomyAction.Confirm && planning == null) {
                completions.send(BudgetCompletion((pending.request?.command as? EngineCommand.ConfirmBudget)?.sessionId))
                return
            }
            val id = planning?.id ?: manualId
            val revision = planning?.revision ?: 0
            val command = when (val action = pending.action) {
                EconomyAction.StartAllocation -> EngineCommand.StartBudgetAllocation(id, revision)
                is EconomyAction.Adjust -> EngineCommand.ChangeBudgetAllocation(id, revision,
                    BudgetSection.valueOf(action.article.name), increase = action.increase, startManual = planning == null)
                is EconomyAction.SetAmount -> EngineCommand.ChangeBudgetAllocation(id, revision,
                    BudgetSection.valueOf(action.article.name), amount = action.amount, startManual = planning == null)
                EconomyAction.Confirm -> EngineCommand.ConfirmBudget(id, revision,
                    if (planning?.reason == BudgetPlanningReason.INITIAL) BudgetRevisionReason.INITIAL else pending.revisionReason,
                    causeActionId = pending.causeActionId.takeUnless { planning?.reason == BudgetPlanningReason.INITIAL })
                is EconomyAction.Deposit -> EngineCommand.DepositSavings(action.amount, expected = pending.transferExpectation)
                is EconomyAction.Withdraw -> EngineCommand.WithdrawSavings(action.amount, confirmed = true, expected = pending.transferExpectation)
                else -> return
            }
            // A retry keeps exactly the same request identity; new relative presses read the latest state.
            val context = pending.context?.let { shown ->
                val matches = shown.before == FinancialPosition(current.economy.availableBalance,
                    current.economy.savingsBalance, knownNeeds(current)) &&
                    (command !is EngineCommand.ConfirmBudget || shown.presentationId == "budget:$id:$revision")
                shown.copy(complete = shown.complete && matches)
            }
            val transfer = command is EngineCommand.DepositSavings || command is EngineCommand.WithdrawSavings
            if (transfer && pending.request == null && pending.context?.before != FinancialPosition(
                    current.economy.availableBalance, current.economy.savingsBalance, knownNeeds(current))) {
                project(current)
                state.value = state.value.copy(error = "Баланс изменился. Проверь сумму перевода ещё раз.")
                return
            }
            val retrying = pending.request != null
            val request = pending.request ?: EngineRequest(pending.id,
                if (transfer) pending.shownRevision else current.engine?.revision, command, context).also { pending.request = it }
            when (val result = session.dispatch(request)) {
                is EngineResult.Applied -> {
                    project(result.state)
                    state.value = state.value.copy(error = null)
                    if (transfer) {
                        val amount = when (command) {
                            is EngineCommand.DepositSavings -> command.amount
                            is EngineCommand.WithdrawSavings -> command.amount
                            else -> error("Not a transfer")
                        }
                        val withdrawing = command is EngineCommand.WithdrawSavings
                        // A lost reply can be retried after other commands changed the balances.
                        // Feedback belongs to the original receipt, while the screen keeps the latest world.
                        val receipt = if (retrying) try {
                            session.commandReceipt(request.id)
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { null } else null
                        val after = if (retrying) receipt?.after?.economy else result.state.economy
                        val before = receipt?.before?.economy
                        state.value = state.value.copy(savingsStep = if (withdrawing) SavingsStep.WITHDRAW else SavingsStep.DEPOSIT,
                            transferInput = "", withdrawal = null, depositWarning = null,
                            transferReceipt = after?.let { SavingsReceipt(amount, withdrawing,
                                before?.availableBalance ?: if (withdrawing) it.availableBalance - amount else it.availableBalance + amount,
                                before?.savingsBalance ?: if (withdrawing) it.savingsBalance + amount else it.savingsBalance - amount,
                                it.availableBalance, it.savingsBalance) })
                    }
                    if (command is EngineCommand.ConfirmBudget) completions.send(BudgetCompletion(command.sessionId))
                }
                is EngineResult.Blocked -> {
                    val latest = session.read()
                    latest?.let(::project)
                    val reason = result.reason
                    if (reason is BlockReason.FoodBudgetWarning && command is EngineCommand.DepositSavings) {
                        depositRisk = pending
                        state.value = state.value.copy(economy = latest?.economy ?: state.value.economy,
                            depositWarning = reason)
                    } else state.value = state.value.copy(economy = latest?.economy ?: state.value.economy,
                        error = result.reason.playerMessage(current.pet.name), budgetConfirmation = null)
                }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            failed = pending
            state.value = state.value.copy(error = "Не удалось сохранить действие. Повтори попытку.",
                budgetConfirmation = null)
        } finally {
            finish(pending)
        }
    }
}

private fun EconomyAction.isPlanEdit(): Boolean =
    this is EconomyAction.Adjust || this is EconomyAction.SetAmount
