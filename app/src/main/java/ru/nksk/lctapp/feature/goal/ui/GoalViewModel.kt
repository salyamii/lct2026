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
                        if (pending?.expectedRevision != game.engine?.revision) {
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
        if (busy) return
        if (action == GoalAction.Retry) { load(); return }
        val game = saved ?: return
        when (action) {
            is GoalAction.View -> {
                if (session.catalog.goals.none { it.goalId == action.goalId }) return
                handle["viewed_goal"] = action.goalId; handle["show_list"] = false
                pending = null; confirmation = null; message = null; render()
            }
            GoalAction.ShowList -> { handle["show_list"] = true; pending = null; confirmation = null; message = null; render() }
            is GoalAction.Select -> execute(request(game, session.selectGoalCommand(game, action.goalId)))
            is GoalAction.Buy -> execute(request(game, EngineCommand.BuyGoalItem(action.goalId, action.itemId)))
            GoalAction.ConfirmPurchase -> pending?.let { old ->
                val command = old.command as EngineCommand.BuyGoalItem
                execute(old.copy(command = command.copy(acceptFoodRisk = true)))
            }
            GoalAction.CancelPurchase -> { pending = null; confirmation = null; render() }
            GoalAction.Retry -> Unit
        }
    }

    private fun request(game: GameState, command: EngineCommand) =
        EngineRequest(UUID.randomUUID().toString(), game.engine?.revision, command)

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
                        val text = if (command is EngineCommand.BuyGoalItem) {
                            session.catalog.content.items.first { it.id == command.itemId }.name + " — куплено!"
                        } else "Цель выбрана! Продолжи день на главном экране."
                        toast?.cancel()
                        celebration = text
                        toast = viewModelScope.launch { delay(3_500); celebration = null; render() }
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
        val activeGoal = catalog.goals.selectedGoal(game)
        val projects = catalog.goals.map { project ->
            val definition = catalog.content.goals.first { it.id == project.goalId }
            val progress = project.progress(game, catalog.content)
            val completed = game.completedGoalProjects.any { it.goalId == project.goalId }
            val status = when {
                completed -> GoalProjectStatus.COMPLETED
                activeGoal?.goalId == project.goalId -> GoalProjectStatus.ACTIVE
                project.isAvailable(game) -> GoalProjectStatus.AVAILABLE
                else -> GoalProjectStatus.LOCKED
            }
            GoalProjectUiState(project.goalId, renderPetText(definition.title, game.pet.name),
                renderPetText(definition.description, game.pet.name), progress.totalPrice, progress.items.size,
                progress.boughtCount, status, when (status) {
                    GoalProjectStatus.COMPLETED -> "Проект завершён в истории"
                    GoalProjectStatus.ACTIVE -> if (progress.isCollected) "Комплект собран · продолжай историю" else "Текущая цель · куплено ${progress.boughtCount} из ${progress.items.size}"
                    GoalProjectStatus.AVAILABLE -> if (activeGoal == null) "Можно выбрать" else "Доступна после завершения текущей главы"
                    GoalProjectStatus.LOCKED -> if (project.availableAfterProjects == 4) "Откроется после остальных четырёх целей" else "Откроется после первого завершённого проекта"
                })
        }
        val goal = catalog.goals.firstOrNull { it.goalId == handle.get<String>("viewed_goal") } ?: activeGoal
        val showList = handle.get<Boolean>("show_list") ?: (activeGoal == null)
        val common = GoalUiState(loading = false, busy = busy, petName = game.pet.name,
            balance = game.economy.plan.savings, projects = projects, showList = showList || goal == null,
            completedProjectCount = game.completedGoalProjects.size,
            campaignComplete = catalog.storyProgress(game).campaignComplete,
            message = message, confirmation = confirmation, celebration = celebration)
        if (goal == null) { mutableState.value = common; return }
        val definition = catalog.content.goals.first { it.id == goal.goalId }
        val progress = goal.progress(game, catalog.content)
        val selected = activeGoal?.goalId == goal.goalId
        val completed = game.completedGoalProjects.any { it.goalId == goal.goalId }
        mutableState.value = common.copy(
            goalId = goal.goalId, canSelect = activeGoal == null && goal.isAvailable(game), completedProject = completed,
            title = renderPetText(definition.title, game.pet.name), description = renderPetText(definition.description, game.pet.name),
            selected = selected, balance = game.economy.plan.savings, totalPrice = progress.totalPrice,
            remainingPrice = progress.remainingPrice, collected = progress.boughtCount,
            parts = progress.items.map { item ->
                val owned = item.id in progress.ownedItemIds
                val block = if (selected && !owned) session.engine.blockReason(game,
                    EngineCommand.BuyGoalItem(goal.goalId, item.id)) else null
                GoalPartUiState(item.id, renderPetText(item.name, game.pet.name), renderPetText(item.description, game.pet.name), checkNotNull(item.priceCoins), owned,
                    selected && !owned && (block == null || block is BlockReason.FoodBudgetWarning),
                    block?.takeUnless { it is BlockReason.FoodBudgetWarning || it is BlockReason.InsufficientMoney }?.playerMessage(game.pet.name),
                    (block as? BlockReason.InsufficientMoney)?.missing)
            },
            storyHint = when {
                completed -> "Этот проект уже помог пройти главу. Вещи остаются у тебя."
                !goal.isAvailable(game) -> projects.first { it.id == goal.goalId }.hint
                !selected && activeGoal != null -> "Сначала заверши текущую цель и её главу. После этого можно выбрать следующий проект."
                !selected -> "Выбор не пропускает историю. После него нажми «Продолжить день» на главном экране."
                progress.isCollected -> "Комплект собран! Финал главы откроется после нужных сюжетных событий. Продолжай день на главном экране."
                else -> "Части можно покупать в любом порядке. Финал текущей главы ждёт и сюжетных открытий, и этого комплекта."
            },
        )
    }
}
