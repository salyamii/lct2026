package ru.nksk.lctapp.feature.menu.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import javax.inject.Qualifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.nksk.lctapp.core.ui.components.MealChoiceUiState
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.engine.*
import ru.nksk.lctapp.core.ui.game.playerMessage
import ru.nksk.lctapp.core.ui.game.playerDescription
import ru.nksk.lctapp.core.ui.game.mealChoices
import java.util.UUID
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class MenuComputationDispatcher

internal sealed interface MainMenuLoadState {
    data object Loading : MainMenuLoadState
    data class Ready(val menu: MainMenuUiState) : MainMenuLoadState
    data class Error(val cause: Exception) : MainMenuLoadState
}

/** Repository observation owns runtime data; a fixture is used only for a genuinely absent save. */
@HiltViewModel
internal class MainMenuViewModel @Inject constructor(
    private val session: GameSession,
    @param:MenuComputationDispatcher private val computationDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val mutableState = MutableStateFlow<MainMenuLoadState>(MainMenuLoadState.Loading)
    val uiState: StateFlow<MainMenuLoadState> = mutableState.asStateFlow()
    private var loading: Job? = null
    private var saved: GameState? = null
    private var busy = false
    private var notice: String? = null
    private var freeMealRequested = false
    private var mealsShown = false
    private var pendingMeal: EngineRequest? = null
    private data class Presentation(
        val game: GameState,
        val demoMode: Boolean,
        val menu: MainMenuUiState,
        val continuation: ContinueDayPlan,
        val meals: List<MealChoiceUiState>? = null,
    )
    private var presentation: Presentation? = null
    private var projection: Job? = null
    private var projectionGame: GameState? = null
    private var projectionDemoMode = false
    private var projectionIncludesMeals = false
    private var menuActive = false
    private val dayNavigation = Channel<Unit>(Channel.BUFFERED)
    val openDay = dayNavigation.receiveAsFlow()
    private val budgetNavigation = Channel<Unit>(Channel.BUFFERED)
    val openBudget = budgetNavigation.receiveAsFlow()
    private val trainingNavigation = Channel<Unit>(Channel.BUFFERED)
    val openTraining = trainingNavigation.receiveAsFlow()

    init { retry() }

    fun setActive(active: Boolean) {
        if (menuActive == active) return
        menuActive = active
        if (active) render() else projection?.cancel()
    }

    fun retry() {
        if (loading?.isActive == true) return
        loading = viewModelScope.launch {
            mutableState.value = MainMenuLoadState.Loading
            try {
                session.prepare()
                session.observe().collect { saved ->
                    val game = checkNotNull(saved) { "Saved game disappeared after initialization" }
                    // A notice describes the previous attempted action, not the updated save.
                    if (this@MainMenuViewModel.saved != game) {
                        if (pendingMeal == null) notice = null
                        if (!session.catalog.mealPolicy.canOfferFreeMeal(game)) {
                            freeMealRequested = false
                        }
                    }
                    this@MainMenuViewModel.saved = game
                    render()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.value = MainMenuLoadState.Error(error)
            }
        }
    }

    fun continueDay() {
        if (busy) return
        if (pendingMeal != null) { feed(); return }
        val game = saved ?: return
        val current = presentation?.takeIf { it.game === game && it.demoMode == session.demoModeEnabled }
            ?: run { render(); return }
        when (val plan = current.continuation) {
            ContinueDayPlan.NeedsBudget -> viewModelScope.launch { budgetNavigation.send(Unit) }
            is ContinueDayPlan.Day -> act(game, plan.command, open = true)
        }
    }

    fun feed() {
        val game = saved ?: return
        if (busy) return
        if (game.economy.planning != null) {
            viewModelScope.launch { budgetNavigation.send(Unit) }
            return
        }
        mealsShown = true
        freeMealRequested = session.catalog.mealPolicy.canOfferFreeMeal(game)
        render()
    }

    fun selectMeal(mealId: String) {
        val game = saved ?: return
        val pendingId = (pendingMeal?.command as? EngineCommand.Feed)?.mealId
        if (busy || (pendingId != null && pendingId != mealId)) return
        if (pendingId == null && session.catalog.mealPolicy.choices(game).none { it.id == mealId }) return
        act(game, EngineCommand.Feed(mealId), open = false)
    }

    fun feedFree() {
        val game = saved ?: return
        if (!offersFreeMeal(game)) return
        if (game.economy.planning != null) {
            viewModelScope.launch { budgetNavigation.send(Unit) }
            return
        }
        selectMeal(checkNotNull(session.catalog.mealPolicy.freeMeal).id)
    }

    fun dismissFreeMeal() {
        if (busy) return
        freeMealRequested = false
        mealsShown = false
        notice = null
        render()
    }

    private fun offersFreeMeal(game: GameState): Boolean = freeMealRequested && session.catalog.mealPolicy.canOfferFreeMeal(game)

    private fun act(game: GameState, command: EngineCommand?, open: Boolean) {
        if (busy) return
        busy = true; notice = null; render()
        viewModelScope.launch {
            try {
                val result = command?.let {
                    val request = if (it is EngineCommand.Feed) pendingMeal ?: EngineRequest(
                        UUID.randomUUID().toString(), game.engine?.revision, it).also { request -> pendingMeal = request }
                    else EngineRequest(UUID.randomUUID().toString(), game.engine?.revision, it)
                    session.dispatch(request)
                }
                if (command is EngineCommand.Feed) pendingMeal = null
                when (result) {
                    is EngineResult.Applied -> {
                        saved = result.state; freeMealRequested = false
                        if (command is EngineCommand.Feed) mealsShown = false
                    }
                    is EngineResult.Blocked -> {
                        notice = result.reason.playerMessage(game.pet.name)
                        if (command is EngineCommand.Feed && result.reason is BlockReason.InsufficientMoney) {
                            freeMealRequested = true
                            notice = null
                        }
                    }
                    null -> Unit
                }
                if (result is EngineResult.Blocked && result.reason is BlockReason.FinancialPracticeRequired)
                    trainingNavigation.send(Unit)
                else if (result is EngineResult.Blocked && result.reason == BlockReason.BudgetPlanningRequired)
                    budgetNavigation.send(Unit)
                else if (open) dayNavigation.send(Unit)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                notice = "Не удалось сохранить действие. Попробуй ещё раз."
            }
            finally { busy = false; render() }
        }
    }

    private fun render() {
        if (!menuActive) return
        val game = saved ?: return
        val demoMode = session.demoModeEnabled
        val current = presentation?.takeIf { it.game === game && it.demoMode == demoMode }
        val needsMeals = mealsShown
        if (current != null && (!needsMeals || current.meals != null)) {
            publish(current, ready = true)
            return
        }
        // Busy/notice updates reuse immutable presentation; no preview transition runs on the tap.
        presentation?.let { publish(it, ready = false) }
        if (projection?.isActive == true && projectionGame === game && projectionDemoMode == demoMode &&
            (!needsMeals || projectionIncludesMeals)) return
        projection?.cancel()
        projectionGame = game
        projectionDemoMode = demoMode
        projectionIncludesMeals = needsMeals
        projection = viewModelScope.launch {
            try {
                val projected = withContext(computationDispatcher) {
                    val base = current ?: buildPresentation(game, demoMode)
                    if (needsMeals && base.meals == null) base.copy(
                        meals = mealChoices(game, session.catalog, session.engine, demoMode)) else base
                }
                // A later Room snapshot or mode change owns the UI, even if old CPU work finishes last.
                if (menuActive && saved === game && session.demoModeEnabled == demoMode) {
                    presentation = projected
                    publish(projected, ready = !mealsShown || projected.meals != null)
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (error: Exception) {
                if (menuActive && saved === game && session.demoModeEnabled == demoMode) {
                    loading?.cancel()
                    mutableState.value = MainMenuLoadState.Error(error)
                }
            }
        }
    }

    private fun buildPresentation(game: GameState, demoMode: Boolean): Presentation {
        val continuation = session.continueDayPlan(game, demoMode)
        val command = (continuation as? ContinueDayPlan.Day)?.command
        val menu = game.toMainMenuUiState(session.catalog.rules.fullEnergy, session.catalog, demoMode)
        return Presentation(game, demoMode, menu.copy(
            spendingPreview = session.engine.advanceSpending(game, command)?.let { it.quote.playerDescription(it.kind) },
            mealPrice = session.catalog.mealPolicy.basicMeal.price,
            continueLabel = if (game.engine?.phase == DayPhase.FINISHED) menu.continueLabel else when (command) {
                EngineCommand.FinishDay -> "Закончить день"
                EngineCommand.OpenNextEvent -> "Продолжить день"
                else -> menu.continueLabel
            },
        ), continuation)
    }

    private fun publish(current: Presentation, ready: Boolean) {
        val retryId = (pendingMeal?.command as? EngineCommand.Feed)?.mealId
        mutableState.value = MainMenuLoadState.Ready(current.menu.copy(
            canFeed = current.menu.canFeed || pendingMeal != null,
            busy = busy || !ready,
            notice = notice,
            showFreeMeal = offersFreeMeal(current.game),
            showMeals = mealsShown && current.meals != null,
            meals = if (!mealsShown) emptyList() else current.meals.orEmpty().map { choice ->
                if (retryId == null) choice else choice.copy(enabled = choice.id == retryId,
                    label = if (choice.id == retryId) "Повторить: ${choice.label}" else choice.label)
            },
        ))
    }
}
