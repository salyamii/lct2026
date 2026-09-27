package ru.nksk.lctapp.feature.tasks.ui

import ru.nksk.lctapp.domain.pet.renderPetText

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
import ru.nksk.lctapp.core.ui.game.playerDescription
import ru.nksk.lctapp.domain.economy.EconomyOperations
import ru.nksk.lctapp.domain.economy.SpendingKind
import ru.nksk.lctapp.core.ui.game.playerMessage
import ru.nksk.lctapp.core.ui.game.asGameUiText
import ru.nksk.lctapp.domain.engine.*
import ru.nksk.lctapp.domain.game.GameState

data class OfferedDeedUiState(val id: String, val title: String, val description: String, val reward: String,
    val effort: String, val deadline: String, val scene: String)
data class DeedsMealUiState(val id: String, val label: String, val enabled: Boolean, val spending: String? = null, val consequence: String? = null)
data class DeedsUiState(
    val loading: Boolean = true, val failed: Boolean = false, val busy: Boolean = false,
    val offers: List<OfferedDeedUiState> = emptyList(), val message: String? = null,
    val meals: List<DeedsMealUiState> = emptyList(), val hasCurrentEvent: Boolean = false,
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

    init { retry() }

    fun retry() {
        if (loading?.isActive == true) return
        loading = viewModelScope.launch {
            mutableState.value = DeedsUiState()
            try {
                session.prepare()
                session.observe().collect {
                    if (game != it) { message = null; needsFood = false }
                    game = checkNotNull(it)
                    render()
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { mutableState.value = DeedsUiState(loading = false, failed = true) }
        }
    }

    fun start(offerId: String) = execute(EngineCommand.StartDeed(offerId), navigate = true)
    fun feed(mealId: String) = execute(EngineCommand.Feed(mealId), navigate = false)

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
        busy = true; message = null
        mutableState.value = mutableState.value.copy(busy = true)
        viewModelScope.launch {
            try {
                when (val result = session.dispatch(EngineRequest(UUID.randomUUID().toString(), saved.engine?.revision, command))) {
                    is EngineResult.Applied -> {
                        game = result.state; needsFood = false
                        if (navigate) {
                            navigating = true
                            eventNavigation.send(checkNotNull(result.state.engine?.currentEvent).id)
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
        mutableState.value = DeedsUiState(
            loading = false, busy = busy, message = message, petName = saved.pet.name,
            hasCurrentEvent = saved.engine?.currentEvent != null,
            offers = session.engine.availableDeeds(saved).map { offer ->
                val event = catalog.content.events.single { it.id == offer.eventId }
                val card = catalog.cards.getValue(event.id)
                val reward = catalog.content.choices.single { it.eventId == event.id }.moneyDelta
                OfferedDeedUiState(offer.id, renderPetText(event.title, saved.pet.name).asGameUiText(),
                    renderPetText(event.description, saved.pet.name).asGameUiText(), "До $reward монет",
                    renderPetText(card.effort, saved.pet.name).asGameUiText(),
                    deedDeadline(saved.engine!!.day, offer.expiresDay), card.scene)
            },
            meals = if (!needsFood) emptyList() else catalog.meals.filter { it.price > 0 || saved.economy.availableBalance < catalog.meals.first().price }.map {
                DeedsMealUiState(it.id, if (it.price == 0L) "Поесть бесплатно" else "Поесть за ${it.price} монет",
                    session.engine.blockReason(saved, EngineCommand.Feed(it.id)) == null,
                    EconomyOperations.quote(saved.economy, it.price, SpendingKind.FEEDING).playerDescription(SpendingKind.FEEDING),
                    "После еды сегодня понадобится отдых. Утром будем немного уставшими.".takeIf { _ -> it.price == 0L })
            },
        )
    }
}

internal fun deedDeadline(currentDay: Int, expiresDay: Int): String = when (expiresDay - currentDay + 1) {
    1 -> "Сегодня — последний день"
    2 -> "Осталось 2 дня: сегодня и завтра"
    3 -> "Осталось 3 дня, включая сегодня"
    else -> "До конца дня $expiresDay"
}
