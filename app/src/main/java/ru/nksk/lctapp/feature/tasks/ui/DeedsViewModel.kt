package ru.nksk.lctapp.feature.tasks.ui

import ru.nksk.lctapp.domain.pet.renderPetText
import ru.nksk.lctapp.core.ui.game.mealChoices
import ru.nksk.lctapp.core.ui.components.MealChoiceUiState

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import ru.nksk.lctapp.core.ui.game.playerMessage
import ru.nksk.lctapp.core.ui.game.asGameUiText
import ru.nksk.lctapp.core.ui.game.asPetEffortText
import ru.nksk.lctapp.core.ui.game.deedDeadline
import ru.nksk.lctapp.core.ui.game.missingCoinAmount
import ru.nksk.lctapp.domain.engine.*
import ru.nksk.lctapp.domain.game.GameState

data class OfferedDeedUiState(val id: String, val title: String, val description: String, val reward: String,
    val effort: String, val deadline: String, val scene: String)
data class DeedsUiState(
    val loading: Boolean = true, val failed: Boolean = false, val busy: Boolean = false,
    val offers: List<OfferedDeedUiState> = emptyList(), val message: String? = null,
    val meals: List<MealChoiceUiState> = emptyList(), val hasCurrentEvent: Boolean = false,
    val petName: String = "",
)

@HiltViewModel
internal class DeedsViewModel @Inject constructor(private val session: GameSession) : ViewModel() {
    private val mutableState = MutableStateFlow(DeedsUiState())
    val uiState = mutableState.asStateFlow()
    private val eventNavigation = Channel<String>(Channel.BUFFERED)
    val openEvent = eventNavigation.receiveAsFlow()
    private var game: GameState? = null
    private var loading: Job? = null
    private var busy = false
    private var navigating = false
    private var navigationHasLeft = false
    private var message: String? = null
    private var needsFood = false
    private var pendingRequest: EngineRequest? = null
    private var pendingMeal: MealChoiceUiState? = null
    private var shownDemoMode = false

    init { retry() }

    fun retry() {
        if (loading?.isActive == true) return
        loading = viewModelScope.launch {
            mutableState.value = DeedsUiState()
            try {
                session.prepare()
                session.observe().collect {
                    if (game != it && pendingRequest == null) { message = null; needsFood = false }
                    game = checkNotNull(it)
                    render()
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { mutableState.value = DeedsUiState(loading = false, failed = true) }
        }
    }

    fun start(offerId: String) = execute(EngineCommand.StartDeed(offerId), navigate = true)
    fun feed(mealId: String) = execute(EngineCommand.Feed(mealId), navigate = false)

    fun dismissMessage() {
        if (busy) return
        message = null
        needsFood = false
        render()
    }

    fun onScreenResumed() {
        if (navigating && navigationHasLeft) {
            navigating = false
            navigationHasLeft = false
            busy = false
            render()
        }
    }

    fun onScreenHidden() {
        if (navigating) navigationHasLeft = true
    }

    private fun execute(command: EngineCommand, navigate: Boolean) {
        val saved = game ?: return
        if (busy) return
        val request = pendingRequest?.also {
            if (it.command != command) {
                message = "Сначала повтори сохранение выбранного действия."
                render()
                return
            }
        } ?: EngineRequest(UUID.randomUUID().toString(), saved.engine?.revision, command,
            demoMode = shownDemoMode).also {
                pendingRequest = it
                pendingMeal = (command as? EngineCommand.Feed)?.let { feed ->
                    mealChoices(saved, session.catalog, session.engine, shownDemoMode).firstOrNull { meal -> meal.id == feed.mealId }
                }
            }
        busy = true; message = null
        mutableState.value = mutableState.value.copy(busy = true)
        viewModelScope.launch {
            try {
                val result = session.dispatch(request)
                pendingRequest = null
                pendingMeal = null
                when (result) {
                    is EngineResult.Applied -> {
                        game = result.state; needsFood = false
                        if (navigate) {
                            val current = result.state.engine?.currentEvent
                            // A retry can return a newer world. Open only the work
                            // chosen by this request, never another current event.
                            if (command is EngineCommand.StartDeed && current?.origin == EventOrigin.DEED &&
                                current.deedOfferId == command.offerId && current.status == EventStatus.ACTIVE) {
                                navigating = true
                                eventNavigation.send(current.id)
                            } else message = "Действие сохранено. Состояние дела уже изменилось."
                        }
                    }
                    is EngineResult.Blocked -> { message = result.reason.playerMessage(saved.pet.name); needsFood = result.reason == BlockReason.MustEat }
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { message = "Не удалось сохранить действие. Попробуй ещё раз." }
            finally { if (!navigating) { busy = false; render() } }
        }
    }

    private fun render() {
        if (busy) return
        val saved = game ?: return
        val catalog = session.catalog
        shownDemoMode = session.demoModeEnabled
        mutableState.value = DeedsUiState(
            loading = false, busy = busy, message = message, petName = saved.pet.name,
            hasCurrentEvent = saved.engine?.currentEvent != null,
            offers = session.engine.availableDeeds(saved).map { offer ->
                val event = catalog.content.events.single { it.id == offer.eventId }
                val card = catalog.cards.getValue(event.id)
                val reward = catalog.content.choices.single { it.eventId == event.id }.moneyDelta
                OfferedDeedUiState(offer.id, renderPetText(event.title, saved.pet.name).asGameUiText(),
                    renderPetText(event.description, saved.pet.name).asGameUiText(), "До ${missingCoinAmount(reward)}",
                    if (session.demoModeEnabled) "Без усталости · режим бога" else card.effort.asPetEffortText(saved.pet.name),
                    deedDeadline(saved.engine!!.day, offer.expiresDay), card.scene)
            },
            meals = pendingMeal?.let { listOf(it.copy(enabled = true)) }
                ?: if (!needsFood) emptyList() else mealChoices(saved, catalog, session.engine, shownDemoMode),
        )
    }
}
