package ru.nksk.lctapp.feature.menu.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.engine.*
import ru.nksk.lctapp.core.ui.game.playerMessage
import ru.nksk.lctapp.core.ui.game.playerDescription
import java.util.UUID
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

internal sealed interface MainMenuLoadState {
    data object Loading : MainMenuLoadState
    data class Ready(val menu: MainMenuUiState) : MainMenuLoadState
    data class Error(val cause: Exception) : MainMenuLoadState
}

/** Repository observation owns runtime data; a fixture is used only for a genuinely absent save. */
@HiltViewModel
internal class MainMenuViewModel @Inject constructor(
    private val session: GameSession,
) : ViewModel() {
    private val mutableState = MutableStateFlow<MainMenuLoadState>(MainMenuLoadState.Loading)
    val uiState: StateFlow<MainMenuLoadState> = mutableState.asStateFlow()
    private var loading: Job? = null
    private var saved: GameState? = null
    private var busy = false
    private var notice: String? = null
    private var freeMealRequested = false
    private val dayNavigation = Channel<Unit>(Channel.BUFFERED)
    val openDay = dayNavigation.receiveAsFlow()

    init { retry() }

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
                        notice = null
                        if (game.engine?.ateToday != false || game.engine?.phase == DayPhase.FINISHED ||
                            game.economy.balance >= session.catalog.meals.first { it.price > 0 }.price) {
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
        val game = saved ?: return
        if (game.economy.planning != null) {
            viewModelScope.launch { dayNavigation.send(Unit) }
            return
        }
        // Reopening the completed day's summary is a read, not the next day's income.
        val command = if (game.engine?.phase == DayPhase.FINISHED) null else session.advanceCommand(game)
        act(game, command, open = true)
    }

    fun feed() {
        val game = saved ?: return
        if (game.economy.planning != null) {
            viewModelScope.launch { dayNavigation.send(Unit) }
            return
        }
        act(game, EngineCommand.Feed(session.catalog.meals.first { it.price > 0 }.id), open = false)
    }

    fun feedFree() {
        val game = saved ?: return
        if (!offersFreeMeal(game)) return
        if (game.economy.planning != null) {
            viewModelScope.launch { dayNavigation.send(Unit) }
            return
        }
        act(game, EngineCommand.Feed(session.catalog.meals.first { it.price == 0L }.id), open = false)
    }

    fun dismissFreeMeal() {
        if (busy) return
        freeMealRequested = false
        notice = null
        render()
    }

    private fun offersFreeMeal(game: GameState): Boolean = freeMealRequested &&
        game.engine?.let { !it.ateToday && it.phase != DayPhase.FINISHED } == true &&
        game.economy.balance < session.catalog.meals.first { it.price > 0 }.price

    private fun act(game: GameState, command: EngineCommand?, open: Boolean) {
        if (busy) return
        busy = true; notice = null; render()
        viewModelScope.launch {
            try {
                val result = command?.let { session.dispatch(EngineRequest(UUID.randomUUID().toString(), game.engine?.revision, it)) }
                when (result) {
                    is EngineResult.Applied -> {
                        saved = result.state; freeMealRequested = false
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
                if (open) dayNavigation.send(Unit)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                notice = "Не удалось сохранить действие. Попробуй ещё раз."
            }
            finally { busy = false; render() }
        }
    }

    private fun render() {
        saved?.let {
            val menu = it.toMainMenuUiState(session.catalog.rules.fullEnergy, session.catalog)
            mutableState.value = MainMenuLoadState.Ready(menu.copy(
                busy = busy, notice = notice, spendingPreview = session.previewAdvanceSpending(it)?.let { preview ->
                    preview.quote.playerDescription(preview.kind)
                }, mealPrice = session.catalog.meals.first { meal -> meal.price > 0 }.price,
                showFreeMeal = offersFreeMeal(it),
                continueLabel = if (it.engine?.phase == DayPhase.FINISHED) menu.continueLabel else when (session.advanceCommand(it)) {
                    EngineCommand.FinishDay -> "Закончить день"
                    EngineCommand.OpenNextEvent -> "Продолжить день"
                    else -> menu.continueLabel
                },
            ))
        }
    }
}
