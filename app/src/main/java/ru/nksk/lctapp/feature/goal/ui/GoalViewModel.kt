package ru.nksk.lctapp.feature.goal.ui

import ru.nksk.lctapp.core.ui.game.asGameUiText

import ru.nksk.lctapp.domain.pet.renderPetText

import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import ru.nksk.lctapp.core.ui.game.GameActionAttempt
import ru.nksk.lctapp.core.ui.game.playerMessage
import ru.nksk.lctapp.core.ui.game.adventurePetArtwork
import ru.nksk.lctapp.domain.analytics.DecisionContext
import ru.nksk.lctapp.domain.analytics.FinancialPosition
import ru.nksk.lctapp.domain.economy.EconomyOperations
import ru.nksk.lctapp.domain.engine.*
import ru.nksk.lctapp.domain.game.GameState

@HiltViewModel
internal class GoalViewModel @Inject constructor(private val session: GameSession, private val handle: SavedStateHandle) : ViewModel() {
    private val mutableState = MutableStateFlow(GoalUiState())
    val uiState = mutableState.asStateFlow()
    private val continuationNavigation = Channel<GoalContinuationDestination>(Channel.BUFFERED)
    val continueNavigation = continuationNavigation.receiveAsFlow()
    private var saved: GameState? = null
    private var observation: Job? = null
    private var toast: Job? = null
    private var busy = false
    private var message: String? = null
    private var celebration: String? = null
    private var pending: EngineRequest? = null
    private var confirmation: PurchaseConfirmation? = null
    private var presentedContext: String? = null
    private var purchaseSubmitted = false
    // Observation may refresh the purchase quote, but must not discard an uncertain continuation.
    private var pendingContinuation: GameActionAttempt? = null
    private var shownDemoMode = false
    private var screenActive = true

    init { load() }

    fun setActive(active: Boolean) {
        if (screenActive == active) return
        screenActive = active
        if (active) load() else {
            observation?.cancel(); observation = null
        }
    }

    private fun load() {
        if (!screenActive || observation?.isActive == true) return
        observation = viewModelScope.launch {
            if (saved == null) mutableState.value = GoalUiState()
            try {
                session.prepare()
                session.observe().collect { value ->
                    val game = checkNotNull(value)
                    if (shownDemoMode != session.demoModeEnabled && !purchaseSubmitted) clearPurchase()
                    shownDemoMode = session.demoModeEnabled
                    if (saved != game) {
                        message = null
                        if (pending?.expectedRevision != game.engine?.revision || saved?.economy != game.economy) {
                            val interruptedPreview = confirmation != null && !busy
                            clearPurchase()
                            if (interruptedPreview) message = "Монеты или события изменились. Проверь покупку ещё раз."
                        }
                    }
                    saved = game
                    render()
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { mutableState.value = GoalUiState(loading = false, failed = true) }
        }
    }

    fun onAction(action: GoalAction) {
        if (action is GoalAction.ContextPresented) {
            if (confirmation?.contextId == action.id) presentedContext = action.id
            return
        }
        if (busy || !screenActive) return
        if (action == GoalAction.Retry) { load(); return }
        val game = saved ?: return
        when (action) {
            is GoalAction.View -> {
                if (session.catalog.goals.none { it.goalId == action.goalId }) return
                handle["return_to_list"] = mutableState.value.showList || mutableState.value.returnToList
                handle["viewed_goal"] = action.goalId; handle["show_list"] = false
                handle.remove<String>("purchase_result_item")
                clearPurchase(); message = null; render()
            }
            GoalAction.ShowList -> {
                handle["show_list"] = true; handle.remove<String>("purchase_result_item")
                handle["return_to_list"] = false
                clearPurchase(); message = null; render()
            }
            GoalAction.DismissPurchaseResult -> { handle.remove<String>("purchase_result_item"); render() }
            GoalAction.ContinueStory -> continueStory(game)
            is GoalAction.Select -> execute(request(game, session.selectGoalCommand(game, action.goalId)))
            is GoalAction.SelectSavingGoal -> execute(request(game, session.selectSavingGoalCommand(game, action.goalId, action.itemId)))
            is GoalAction.Buy -> previewPurchase(game, action)
            GoalAction.ConfirmPurchase -> confirmPurchase(game)
            GoalAction.CancelPurchase -> { clearPurchase(); message = null; render() }
            GoalAction.Retry -> Unit
            is GoalAction.ContextPresented -> Unit
        }
    }

    private fun request(game: GameState, command: EngineCommand) =
        EngineRequest(UUID.randomUUID().toString(), game.engine?.revision, command, demoMode = session.demoModeEnabled)

    private fun clearPurchase() {
        pending = null
        confirmation = null
        presentedContext = null
        purchaseSubmitted = false
    }

    /** Opening and cancelling a quote never dispatch a gameplay command. */
    private fun previewPurchase(game: GameState, action: GoalAction.Buy) {
        if (confirmation != null) return
        val command = EngineCommand.BuyGoalItem(action.goalId, action.itemId)
        val block = session.engine.blockReason(game, command)
        if (block != null && block !is BlockReason.FoodBudgetWarning) {
            message = block.playerMessage(game.pet.name); render(); return
        }
        val item = session.catalog.content.items.firstOrNull { it.id == action.itemId } ?: return
        val price = if (session.demoModeEnabled) 0L else item.priceCoins ?: return
        val quote = EconomyOperations.goalPurchaseQuote(game.economy, price)
        if (!quote.affordable) return
        val remaining = game.economy.availableBalance - quote.fromAvailableAmount
        val food = knownNeeds(game)
        clearPurchase()
        val prepared = request(game, command.copy(acceptFoodRisk = remaining < food))
        pending = prepared
        confirmation = PurchaseConfirmation(item.id, renderPetText(item.name, game.pet.name).asGameUiText(), price,
            quote.fromSavings, quote.fromAvailable.parts, game.economy.availableBalance, game.economy.savingsBalance,
            remaining, game.economy.savingsBalance - quote.fromSavings, food, "goal-purchase:${prepared.id}",
            demoMode = session.demoModeEnabled)
        message = null
        render()
    }

    private fun confirmPurchase(game: GameState) {
        val shown = confirmation ?: return
        var prepared = pending ?: return
        if (!purchaseSubmitted) {
            val before = FinancialPosition(shown.availableBefore, shown.savingsBefore, shown.foodNeeded)
            val visible = presentedContext == shown.contextId
            prepared = prepared.copy(context = DecisionContext(presentationId = shown.contextId,
                informationPresented = visible,
                complete = visible && before == FinancialPosition(game.economy.availableBalance,
                    game.economy.savingsBalance, knownNeeds(game)),
                before = before, after = FinancialPosition(shown.remainingBalance, shown.remainingSavings, shown.foodNeeded),
                alternativeAvailable = true))
            pending = prepared
            purchaseSubmitted = true
        }
        // An uncertain write retries the exact same request and exposure evidence.
        execute(prepared)
    }

    private fun knownNeeds(game: GameState): Long = session.catalog.mealPolicy.foodRequirement(game)

    private fun continueStory(game: GameState) {
        val shown = mutableState.value
        if (pendingContinuation == null && !shown.canSelect && !shown.completedProject &&
            (shown.parts.isEmpty() || shown.parts.any { !it.owned })) return
        busy = true
        message = null
        clearPurchase()
        if (pendingContinuation == null && shown.canSelect) {
            pendingContinuation = GameActionAttempt.prepare(game,
                session.selectGoalCommand(game, checkNotNull(shown.goalId)))
        }
        render()
        viewModelScope.launch {
            try {
                while (true) {
                    val current = checkNotNull(saved)
                    val attempt = pendingContinuation ?: when (val plan = session.continueDayPlan(current)) {
                        ContinueDayPlan.NeedsBudget -> {
                            openContinuation(GoalContinuationDestination.BUDGET)
                            return@launch
                        }
                        is ContinueDayPlan.Day -> {
                            val command = plan.command
                            if (command == null) {
                                openContinuation(GoalContinuationDestination.DAY)
                                return@launch
                            }
                            GameActionAttempt.prepare(current, command).also { pendingContinuation = it }
                        }
                    }
                    when (val result = attempt.submit(session)) {
                        is EngineResult.Applied -> {
                            pendingContinuation = null
                            if ((saved?.engine?.revision ?: -1) <= (result.state.engine?.revision ?: -1)) saved = result.state
                            val selected = attempt.request.command as? EngineCommand.SelectGoal
                            if (selected != null) {
                                handle["viewed_goal"] = selected.goalId
                                handle["show_list"] = false
                                // Selection and explicit continuation are separate guarded commands.
                                continue
                            }
                            openContinuation(if (checkNotNull(saved).economy.let { it.planning != null || it.unallocated != 0L })
                                GoalContinuationDestination.BUDGET else GoalContinuationDestination.DAY)
                            return@launch
                        }
                        is EngineResult.Blocked -> {
                            pendingContinuation = null
                            saved = checkNotNull(session.read())
                            when (result.reason) {
                                is BlockReason.FinancialPracticeRequired -> openContinuation(GoalContinuationDestination.TRAINING)
                                BlockReason.BudgetPlanningRequired -> openContinuation(GoalContinuationDestination.BUDGET)
                                BlockReason.MustEat, BlockReason.MustSleep, BlockReason.EventInProgress,
                                BlockReason.DayFinished, BlockReason.NoNextEvent -> openContinuation(GoalContinuationDestination.DAY)
                                else -> message = result.reason.playerMessage(checkNotNull(saved).pet.name)
                            }
                            return@launch
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                message = "Не удалось продолжить историю. Нажми «Продолжить историю» ещё раз."
            } finally {
                busy = false
                render()
            }
        }
    }

    private suspend fun openContinuation(destination: GoalContinuationDestination) {
        handle.remove<String>("purchase_result_item")
        continuationNavigation.send(destination)
    }

    private fun execute(request: EngineRequest) {
        val purchasing = request.command is EngineCommand.BuyGoalItem
        busy = true; message = null
        if (!purchasing) clearPurchase()
        render()
        viewModelScope.launch {
            try {
                when (val result = session.dispatch(request)) {
                    is EngineResult.Applied -> {
                        if ((saved?.engine?.revision ?: -1) <= (result.state.engine?.revision ?: -1)) saved = result.state
                        val command = request.command
                        if (command is EngineCommand.SelectGoal) {
                            handle["viewed_goal"] = command.goalId; handle["show_list"] = false
                        }
                        if (command is EngineCommand.SelectSavingGoal) {
                            handle["viewed_goal"] = command.goalId; handle["show_list"] = false
                        }
                        if (command is EngineCommand.BuyGoalItem) {
                            val receipt = result.state.engine?.journal?.firstOrNull {
                                it.id.startsWith("${request.id}:journal:") && it.kind == DayJournalKind.ITEM_PURCHASE &&
                                    it.sourceId == command.itemId
                            }
                            handle["purchase_result_price"] = receipt?.moneyDelta?.let(Math::negateExact)
                                ?: confirmation?.price
                            clearPurchase()
                            handle["purchase_result_item"] = command.itemId
                        }
                        toast?.cancel()
                        celebration = if (command is EngineCommand.SelectGoal)
                            "Цель выбрана! Продолжи день на главном экране." else null
                        if (celebration != null) toast = viewModelScope.launch { delay(3_500); celebration = null; render() }
                    }
                    is EngineResult.Blocked -> {
                        saved = checkNotNull(session.read())
                        clearPurchase()
                        message = result.reason.playerMessage(checkNotNull(saved).pet.name)
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { message = "Не удалось сохранить действие. Попробуй ещё раз."
            } finally { busy = false; render() }
        }
    }

    private fun render() {
        if (!screenActive) return
        val game = saved ?: return
        val catalog = session.catalog
        val story = catalog.storyProgress(game)
        val activeGoal = catalog.goals.selectedGoal(game)
        val projects = catalog.goals.map { project ->
            val definition = catalog.content.goals.first { it.id == project.goalId }
            val progress = project.progress(game, catalog.content)
            val completed = story.goalCompleted(project)
            val status = when {
                completed -> GoalProjectStatus.COMPLETED
                activeGoal?.goalId == project.goalId -> GoalProjectStatus.ACTIVE
                story.goalAvailable(project) -> GoalProjectStatus.AVAILABLE
                else -> GoalProjectStatus.LOCKED
            }
            GoalProjectUiState(project.goalId, renderPetText(definition.title, game.pet.name),
                renderPetText(definition.description, game.pet.name), progress.totalPrice, progress.items.size,
                progress.boughtCount, status, when (status) {
                    GoalProjectStatus.COMPLETED -> "Задание выполнено"
                    GoalProjectStatus.ACTIVE -> if (progress.isCollected) "Всё собрано, продолжай историю" else "Для текущей цели куплено ${progress.boughtCount} из ${progress.items.size}"
                    GoalProjectStatus.AVAILABLE -> if (activeGoal == null) "Выбери, на что будем копить" else "Текущая глава"
                    GoalProjectStatus.LOCKED -> "Откроется после предыдущей главы"
                }, requirements = progress.items.map { item ->
                    GoalRequirementUiState(item.id, renderPetText(item.name, game.pet.name).asGameUiText(),
                        checkNotNull(item.priceCoins), item.id in progress.ownedItemIds)
                })
        }
        val goal = catalog.goals.firstOrNull { it.goalId == handle.get<String>("viewed_goal") } ?: activeGoal ?: story.requiredGoal
        val showList = handle.get<Boolean>("show_list") ?: false
        val common = GoalUiState(loading = false, busy = busy, petName = game.pet.name,
            demoMode = session.demoModeEnabled,
            character = adventurePetArtwork(game.pet),
            balance = game.economy.savingsBalance, availableBalance = game.economy.availableBalance,
            contextId = "goal:${game.engine?.revision}:${game.economy.availableBalance}:${game.economy.savingsBalance}:${knownNeeds(game)}",
            knownNeeds = knownNeeds(game),
            transfersEnabled = game.economy.planning == null && game.economy.unallocated == 0L,
            projects = projects, showList = showList || goal == null,
            returnToList = !showList && goal != null && handle.get<Boolean>("return_to_list") == true,
            completedProjectCount = catalog.goals.count { story.goalCompleted(it) },
            campaignComplete = catalog.storyProgress(game).campaignComplete,
            message = message, confirmation = confirmation, celebration = celebration,
            purchaseResult = handle.get<String>("purchase_result_item")?.let { id ->
                catalog.content.items.firstOrNull { it.id == id && game.ownedItems.any { owned -> owned.itemId == id } }
                    ?.let { item -> GoalPurchaseResult(item.id, renderPetText(item.name, game.pet.name).asGameUiText(),
                        handle.get<Long>("purchase_result_price") ?: checkNotNull(item.priceCoins)) }
            })
        if (goal == null) { mutableState.value = common; return }
        val definition = catalog.content.goals.first { it.id == goal.goalId }
        val progress = goal.progress(game, catalog.content)
        val selected = story.goalAvailable(goal) && (activeGoal == null || activeGoal.goalId == goal.goalId)
        val completed = story.goalCompleted(goal)
        mutableState.value = common.copy(
            goalId = goal.goalId, scene = goal.scene,
            canSelect = activeGoal == null && story.goalAvailable(goal) && progress.isCollected, completedProject = completed,
            title = renderPetText(definition.title, game.pet.name), description = renderPetText(definition.description, game.pet.name),
            selected = selected, balance = game.economy.savingsBalance,
            totalPrice = if (session.demoModeEnabled && selected) 0 else progress.totalPrice,
            remainingPrice = if (session.demoModeEnabled && selected) 0 else progress.remainingPrice, collected = progress.boughtCount,
            parts = progress.items.map { item ->
                val owned = item.id in progress.ownedItemIds
                val target = game.selectedSavingItemId == item.id
                val price = if (session.demoModeEnabled && selected) 0L else checkNotNull(item.priceCoins)
                val quote = EconomyOperations.goalPurchaseQuote(game.economy, price)
                val block = if (selected && target && !owned) session.engine.blockReason(game,
                    EngineCommand.BuyGoalItem(goal.goalId, item.id)) else null
                GoalPartUiState(item.id, renderPetText(item.name, game.pet.name).asGameUiText(), renderPetText(item.description, game.pet.name), price, owned,
                    selected && target && !owned && (block == null || block is BlockReason.FoodBudgetWarning),
                    block?.takeUnless { it is BlockReason.FoodBudgetWarning || it is BlockReason.InsufficientMoney }?.playerMessage(game.pet.name),
                    (block as? BlockReason.InsufficientMoney)?.missing,
                    savingTarget = target, canSelect = selected && !owned && game.economy.planning == null && game.economy.unallocated == 0L,
                    savedCoins = minOf(game.economy.savingsBalance, price),
                    remainingCoins = maxOf(0L, price - game.economy.savingsBalance),
                    availableContribution = if (quote.affordable) quote.fromAvailableAmount else 0L,
                    demoMode = session.demoModeEnabled && selected)
            },
            storyHint = when {
                completed -> "Эта глава пройдена. Купленные вещи остаются у тебя."
                !story.goalAvailable(goal) -> projects.first { it.id == goal.goalId }.hint
                !selected && activeGoal != null -> "Сначала заверши текущую главу. Следующая большая цель откроется по сюжету."
                !selected -> "Можно выбрать новую цель. Прежние события останутся в истории."
                progress.isCollected -> "Комплект собран! Продолжим приключение и узнаем, что будет дальше."
                else -> "Собери снаряжение для задания Смотрителей. Можно начать с любой части; остальные соберём позже."
            },
        )
    }
}
