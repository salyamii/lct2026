package ru.nksk.lctapp.feature.goal.ui

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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.nksk.lctapp.core.ui.game.playerMessage
import ru.nksk.lctapp.core.ui.game.adventurePetArtwork
import ru.nksk.lctapp.domain.analytics.DecisionContext
import ru.nksk.lctapp.domain.analytics.FinancialPosition
import ru.nksk.lctapp.domain.engine.*
import ru.nksk.lctapp.domain.game.GameState

@HiltViewModel
internal class GoalViewModel @Inject constructor(private val session: GameSession, private val handle: SavedStateHandle) : ViewModel() {
    private val mutableState = MutableStateFlow(GoalUiState())
    val uiState = mutableState.asStateFlow()
    private var saved: GameState? = null
    private var observation: Job? = null
    private var toast: Job? = null
    private var busy = false
    private var message: String? = null
    private var celebration: String? = null
    private var pending: EngineRequest? = null
    private var confirmation: PurchaseConfirmation? = null
    private var presentedContext: String? = null

    init { load() }

    private fun load() {
        if (observation?.isActive == true) return
        observation = viewModelScope.launch {
            mutableState.value = GoalUiState()
            try {
                session.prepare()
                session.observe().collect { value ->
                    val game = checkNotNull(value)
                    if (saved != game) {
                        message = null
                        if (pending?.expectedRevision != game.engine?.revision || saved?.economy != game.economy) {
                            pending = null; confirmation = null
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
            if (mutableState.value.contextId == action.id) presentedContext = action.id
            return
        }
        if (busy) return
        if (action == GoalAction.Retry) { load(); return }
        val game = saved ?: return
        when (action) {
            is GoalAction.View -> {
                if (session.catalog.goals.none { it.goalId == action.goalId }) return
                handle["return_to_list"] = mutableState.value.showList || mutableState.value.returnToList
                handle["viewed_goal"] = action.goalId; handle["show_list"] = false
                handle.remove<String>("purchase_result_item")
                pending = null; confirmation = null; message = null; render()
            }
            GoalAction.ShowList -> {
                handle["show_list"] = true; handle.remove<String>("purchase_result_item")
                handle["return_to_list"] = false
                pending = null; confirmation = null; message = null; render()
            }
            GoalAction.DismissPurchaseResult -> { handle.remove<String>("purchase_result_item"); render() }
            is GoalAction.Select -> execute(request(game, session.selectGoalCommand(game, action.goalId)))
            is GoalAction.SelectSavingGoal -> execute(request(game, session.selectSavingGoalCommand(game, action.goalId, action.itemId)))
            is GoalAction.Buy -> execute(request(game, EngineCommand.BuyGoalItem(action.goalId, action.itemId)))
            GoalAction.ConfirmPurchase -> pending?.let { old ->
                val command = old.command as? EngineCommand.BuyGoalItem ?: return@let
                execute(old.copy(id = UUID.randomUUID().toString(), command = command.copy(acceptFoodRisk = true)))
            }
            GoalAction.CancelPurchase -> { pending = null; confirmation = null; render() }
            GoalAction.Retry -> Unit
            is GoalAction.ContextPresented -> Unit
        }
    }

    private fun request(game: GameState, command: EngineCommand): EngineRequest {
        val shown = mutableState.value
        val context = if (command is EngineCommand.BuyGoalItem) {
            val position = FinancialPosition(shown.availableBalance, shown.balance, shown.knownNeeds)
            DecisionContext(presentationId = shown.contextId,
                informationPresented = presentedContext == shown.contextId,
                complete = presentedContext == shown.contextId && position == FinancialPosition(game.economy.availableBalance, game.economy.savingsBalance, knownNeeds(game)),
                before = position, alternativeAvailable = true)
        } else null
        return EngineRequest(UUID.randomUUID().toString(), game.engine?.revision, command, context)
    }

    private fun knownNeeds(game: GameState): Long = foodCostUntilWeekEnd(game,
        session.catalog.meals.filter { it.price > 0 }.minOf { it.price })

    private fun execute(request: EngineRequest) {
        busy = true; message = null; pending = null; confirmation = null; render()
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
                            handle["purchase_result_item"] = command.itemId
                        }
                        toast?.cancel()
                        celebration = if (command is EngineCommand.SelectGoal)
                            "Цель выбрана! Продолжи день на главном экране." else null
                        if (celebration != null) toast = viewModelScope.launch { delay(3_500); celebration = null; render() }
                    }
                    is EngineResult.Blocked -> {
                        saved = checkNotNull(session.read())
                        val reason = result.reason
                        val command = request.command
                        if (reason is BlockReason.FoodBudgetWarning && command is EngineCommand.BuyGoalItem &&
                            saved?.engine?.revision == request.expectedRevision) {
                            val item = session.catalog.content.items.first { it.id == command.itemId }
                            pending = request
                            confirmation = PurchaseConfirmation(item.name, checkNotNull(item.priceCoins),
                                reason.remainingBalance, reason.neededForFood)
                        } else message = reason.playerMessage(checkNotNull(saved).pet.name)
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { message = "Не удалось сохранить действие. Попробуй ещё раз."
            } finally { busy = false; render() }
        }
    }

    private fun render() {
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
                    GoalProjectStatus.COMPLETED -> "Проект завершён в истории"
                    GoalProjectStatus.ACTIVE -> if (progress.isCollected) "Всё собрано, продолжай историю" else "Для текущей цели куплено ${progress.boughtCount} из ${progress.items.size}"
                    GoalProjectStatus.AVAILABLE -> if (activeGoal == null) "Выбери, на что будем копить" else "Текущая глава"
                    GoalProjectStatus.LOCKED -> "Откроется после предыдущей главы"
                }, requirements = progress.items.map { item ->
                    GoalRequirementUiState(item.id, renderPetText(item.name, game.pet.name),
                        checkNotNull(item.priceCoins), item.id in progress.ownedItemIds)
                })
        }
        val goal = catalog.goals.firstOrNull { it.goalId == handle.get<String>("viewed_goal") } ?: activeGoal ?: story.requiredGoal
        val showList = handle.get<Boolean>("show_list") ?: false
        val common = GoalUiState(loading = false, busy = busy, petName = game.pet.name,
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
                    ?.let { item -> GoalPurchaseResult(item.id, renderPetText(item.name, game.pet.name), checkNotNull(item.priceCoins)) }
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
            selected = selected, balance = game.economy.savingsBalance, totalPrice = progress.totalPrice,
            remainingPrice = progress.remainingPrice, collected = progress.boughtCount,
            parts = progress.items.map { item ->
                val owned = item.id in progress.ownedItemIds
                val target = game.selectedSavingItemId == item.id
                val block = if (selected && target && !owned) session.engine.blockReason(game,
                    EngineCommand.BuyGoalItem(goal.goalId, item.id)) else null
                GoalPartUiState(item.id, renderPetText(item.name, game.pet.name), renderPetText(item.description, game.pet.name), checkNotNull(item.priceCoins), owned,
                    selected && target && !owned && (block == null || block is BlockReason.FoodBudgetWarning),
                    block?.takeUnless { it is BlockReason.FoodBudgetWarning || it is BlockReason.InsufficientMoney }?.playerMessage(game.pet.name),
                    (block as? BlockReason.InsufficientMoney)?.missing,
                    savingTarget = target, canSelect = selected && !owned && game.economy.planning == null && game.economy.unallocated == 0L,
                    savedCoins = minOf(game.economy.savingsBalance, checkNotNull(item.priceCoins)),
                    remainingCoins = maxOf(0L, checkNotNull(item.priceCoins) - game.economy.savingsBalance))
            },
            storyHint = when {
                completed -> "Эта глава пройдена. Купленные вещи остаются у тебя."
                !story.goalAvailable(goal) -> projects.first { it.id == goal.goalId }.hint
                !selected && activeGoal != null -> "Сначала заверши текущую главу. Следующая большая цель откроется по сюжету."
                !selected -> "Выбор не пропускает историю. После него нажми «Продолжить день» на главном экране."
                progress.isCollected -> "Комплект собран! Финал главы откроется после нужных сюжетных событий. Продолжай день на главном экране."
                else -> "Собери снаряжение для задания Смотрителей. Можно начать с любой части; остальные соберём позже."
            },
        )
    }
}
