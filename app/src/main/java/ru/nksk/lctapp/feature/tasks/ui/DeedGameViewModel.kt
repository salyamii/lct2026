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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import ru.nksk.lctapp.core.ui.game.playerMessage
import ru.nksk.lctapp.core.ui.game.deedCompletionMessage
import ru.nksk.lctapp.domain.engine.*
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.minigame.*

enum class DeedGameType { MEMORY, COMPARISON, PRECISION }
data class DeedGamePresentation(val title: String, val maximumReward: Long, val canPlay: Boolean)
internal data class DeedGameUiState(
    val loading: Boolean = true,
    val type: DeedGameType? = null,
    val presentation: DeedGamePresentation? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val canRetry: Boolean = false,
)

/** Connects an actual offered deed to a transient board and one atomic engine outcome. */
@HiltViewModel
internal class DeedGameViewModel @Inject constructor(private val session: GameSession) : ViewModel() {
    private val mutableState = MutableStateFlow(DeedGameUiState())
    val uiState = mutableState.asStateFlow()
    private val exits = Channel<String?>(Channel.BUFFERED)
    val exit = exits.receiveAsFlow()
    private var occurrenceId: String? = null
    private var latest: GameState? = null
    private var observer: Job? = null
    private var busy = false
    private var message: String? = null
    private var pending: EngineCommand? = null

    fun load(id: String) {
        if (occurrenceId == id && observer?.isActive == true) return
        occurrenceId = id
        observer?.cancel()
        observer = viewModelScope.launch {
            try {
                session.prepare()
                session.observe().collect { latest = checkNotNull(it); render() }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                mutableState.value = DeedGameUiState(loading = false, message = "Не удалось загрузить дело. Попробуй ещё раз.", canRetry = true)
            }
        }
    }

    fun finishMemory(state: MemoryState) { DeedGameScore.fromMemory(state)?.let(::finish) }
    fun finishComparison(state: PriceQuizState) { DeedGameScore.fromComparison(state)?.let(::finish) }
    fun finishPrecision(state: TargetStopState) { DeedGameScore.fromPrecision(state)?.let(::finish) }

    private fun finish(score: DeedGameScore) {
        if (busy || pending != null) return
        val id = occurrenceId ?: return
        execute(EngineCommand.CompleteDeed(id, score))
    }

    fun leave() {
        if (busy) return
        val active = latest?.engine?.currentEvent
        if (active?.id == occurrenceId && active?.status == EventStatus.ACTIVE) {
            execute(EngineCommand.PauseEvent(checkNotNull(active).id))
        } else exit()
    }

    fun retry() {
        if (busy) return
        pending?.let(::execute) ?: occurrenceId?.let(::load)
    }

    private fun execute(command: EngineCommand) {
        val game = latest ?: return
        if (busy) return
        pending = command
        busy = true
        message = null
        mutableState.value = mutableState.value.copy(busy = true, message = null,
            presentation = mutableState.value.presentation?.copy(canPlay = false))
        viewModelScope.launch {
            var leaving = false
            try {
                when (val result = session.dispatch(EngineRequest(UUID.randomUUID().toString(), game.engine?.revision, command))) {
                    is EngineResult.Applied -> {
                        latest = result.state
                        leaving = true
                        // Applied validates this snapshot's revision; the delta is the committed payout.
                        exits.send(if (command is EngineCommand.CompleteDeed)
                            deedCompletionMessage(result.state.economy.balance - game.economy.balance) else null)
                    }
                    is EngineResult.Blocked -> message = result.reason.playerMessage(game.pet.name)
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { message = "Не удалось сохранить результат. Нажми «Повторить»."
            } finally {
                if (!leaving) { busy = false; render() }
            }
        }
    }

    private fun exit() {
        busy = true
        mutableState.value = mutableState.value.copy(busy = true,
            presentation = mutableState.value.presentation?.copy(canPlay = false))
        exits.trySend(null)
    }

    private fun render() {
        if (busy) return
        val game = latest ?: return
        val occurrence = game.engine?.events?.find { it.id == occurrenceId }
        if (occurrence?.status == EventStatus.COMPLETED || occurrence?.status == EventStatus.PAUSED) {
            exit()
            return
        }
        val kind = occurrence?.let { session.catalog.policies[it.eventId]?.deedGameKind }
        if (occurrence?.status != EventStatus.ACTIVE || occurrence.origin != EventOrigin.DEED || kind == null) {
            mutableState.value = DeedGameUiState(loading = false, message = "Это дело больше недоступно.")
            return
        }
        val event = session.catalog.content.events.single { it.id == occurrence.eventId }
        val reward = session.catalog.content.choices.single { it.eventId == event.id }.moneyDelta
        mutableState.value = DeedGameUiState(
            loading = false,
            type = when (kind) {
                DeedGameKind.MEMORY -> DeedGameType.MEMORY
                DeedGameKind.COMPARISON -> DeedGameType.COMPARISON
                DeedGameKind.PRECISION -> DeedGameType.PRECISION
            },
            presentation = DeedGamePresentation(renderPetText(event.title, game.pet.name), reward, pending == null && message == null),
            message = message,
            canRetry = pending != null,
        )
    }
}
