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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.nksk.lctapp.core.ui.game.playerMessage
import ru.nksk.lctapp.core.ui.game.deedCompletionMessage
import ru.nksk.lctapp.core.ui.game.eventCompletionMessage
import ru.nksk.lctapp.core.ui.game.eventSceneArtwork
import ru.nksk.lctapp.core.ui.game.eventSceneBackground
import ru.nksk.lctapp.domain.engine.*
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.minigame.*

enum class DeedGameType { MEMORY, COMPARISON, PRECISION }
data class DeedGamePresentation(val title: String, val maximumReward: Long, val canPlay: Boolean,
    val storyAction: Boolean = false, val sceneRes: Int? = null, val instructions: String? = null,
    val activityArtworkRes: Int? = null, val pairArtwork: List<Int> = emptyList())
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
    private var choiceId: String? = null
    private var latest: GameState? = null
    private var observer: Job? = null
    private var busy = false
    private var message: String? = null
    private var pending: EngineCommand? = null
    private val comparisonEvidence = linkedMapOf<String, PriceQuizEvidence>()
    private val comparisonWrites = Mutex()
    private var comparisonWriter: Job? = null
    private var comparisonSaveFailed = false
    private var leaveWhenLoaded = false

    fun load(id: String, storyChoiceId: String? = null) {
        if (occurrenceId == id && choiceId == storyChoiceId && observer?.isActive == true) return
        if (occurrenceId != id || choiceId != storyChoiceId) comparisonEvidence.clear()
        occurrenceId = id
        choiceId = storyChoiceId
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
    fun finishComparison(state: PriceQuizState, evidence: PriceQuizEvidence? = null) {
        evidence?.let(::recordComparisonAnswers)
        DeedGameScore.fromComparison(state)?.let(::finish)
    }
    fun finishPrecision(state: TargetStopState) { DeedGameScore.fromPrecision(state)?.let(::finish) }

    private fun finish(score: DeedGameScore) {
        if (busy || pending != null) return
        val id = occurrenceId ?: return
        execute(choiceId?.let { EngineCommand.CompleteStoryGame(id, it, score) }
            ?: EngineCommand.CompleteDeed(id, score))
    }

    fun leave() {
        if (busy) return
        if (latest == null) {
            leaveWhenLoaded = true
            occurrenceId?.let { load(it, choiceId) }
            return
        }
        val active = latest?.engine?.currentEvent
        if (active?.id == occurrenceId && active?.status == EventStatus.ACTIVE) {
            execute(EngineCommand.PauseEvent(checkNotNull(active).id))
        } else exit()
    }

    fun retry() {
        if (busy) return
        pending?.let(::execute) ?: if (comparisonSaveFailed) persistComparisonAnswers() else occurrenceId?.let { load(it, choiceId) }
    }

    fun recordComparisonAnswers(evidence: PriceQuizEvidence) {
        if (evidence.answers.isEmpty()) return
        comparisonEvidence[evidence.seriesId] = evidence
        if (!busy) persistComparisonAnswers()
    }

    private fun persistComparisonAnswers() {
        if (comparisonWriter?.isActive == true || comparisonEvidence.isEmpty()) return
        comparisonWriter = viewModelScope.launch {
            try {
                flushComparisonAnswers()
                if (comparisonSaveFailed) { comparisonSaveFailed = false; message = null; render() }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                comparisonSaveFailed = true
                message = "Не удалось сохранить ответы. Нажми «Повторить»."
                render()
            }
        }
    }

    private suspend fun flushComparisonAnswers() = comparisonWrites.withLock {
        val id = occurrenceId ?: return@withLock
        while (true) {
            val batches = comparisonEvidence.values.toList()
            if (batches.isEmpty()) return@withLock
            val game = checkNotNull(session.read())
            val occurrence = checkNotNull(game.engine?.events?.find { it.id == id }) { "This comparison belongs to an unavailable deed" }
            check(gameKind(occurrence) == DeedGameKind.COMPARISON)
            val history = session.history()
            val runId = checkNotNull(history.lastOrNull()?.runId) { "History is not available" }
            val existing = history.flatMap { it.facts }.associateBy { it.eventId }
            val version = session.contentFingerprint
            val candidates = batches.flatMap { evidence ->
                PriceQuizEvidenceMapper.facts(evidence, id, runId,
                    Math.addExact(history.last().sequence, 1), game.engine?.day,
                    game.financial.currentPeriod?.id, version, session.catalog.rules.id)
            }
            for (fact in candidates) existing[fact.eventId]?.let { stored ->
                check(stored.episodeId == fact.episodeId && stored.detail == fact.detail) { "Conflicting comparison answer" }
            }
            val missing = candidates.filter { it.eventId !in existing }
            if (missing.isEmpty()) return@withLock
            session.recordFacts(missing)
            // An answer may have arrived during the write; loop before allowing completion or exit.
        }
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
                flushComparisonAnswers()
                comparisonSaveFailed = false
                val evidence = if (command is EngineCommand.CompleteStoryGame)
                    eventGameStartEvidence(session.history(), game, command) else null
                val submitted = if (command is EngineCommand.CompleteStoryGame)
                    command.copy(resourcePriorityOfferId = evidence?.priorityOfferId) else command
                when (val result = session.dispatch(EngineRequest(UUID.randomUUID().toString(), game.engine?.revision, submitted, evidence?.context))) {
                    is EngineResult.Applied -> {
                        latest = result.state
                        leaving = true
                        // Applied validates this snapshot's revision; the delta is the committed payout.
                        exits.send(when (command) {
                            is EngineCommand.CompleteDeed -> deedCompletionMessage(result.state.economy.balance - game.economy.balance)
                            is EngineCommand.CompleteStoryGame -> eventCompletionMessage(game, result.state,
                                EngineCommand.CompleteEvent(command.occurrenceId, command.choiceId), session.catalog)
                            else -> null
                        })
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
        if (leaveWhenLoaded) {
            leaveWhenLoaded = false
            leave()
            return
        }
        val occurrence = game.engine?.events?.find { it.id == occurrenceId }
        if (occurrence?.status in setOf(EventStatus.COMPLETED, EventStatus.PAUSED, EventStatus.CARRIED, EventStatus.CARRIED_ACTIVE)) {
            exit()
            return
        }
        val kind = occurrence?.let(::gameKind)
        if (occurrence?.status != EventStatus.ACTIVE || kind == null) {
            mutableState.value = DeedGameUiState(loading = false, message = "Это дело больше недоступно.")
            return
        }
        val event = session.catalog.content.events.single { it.id == occurrence.eventId }
        val reward = if (choiceId == null) session.catalog.content.choices.single { it.eventId == event.id }.moneyDelta else 0L
        val storyGame = choiceId != null
        val theme = if (storyGame) storyGameTheme(event.id) else null
        val card = session.catalog.cards[event.id]
        mutableState.value = DeedGameUiState(
            loading = false,
            type = when (kind) {
                DeedGameKind.MEMORY -> DeedGameType.MEMORY
                DeedGameKind.COMPARISON -> DeedGameType.COMPARISON
                DeedGameKind.PRECISION -> DeedGameType.PRECISION
            },
            presentation = DeedGamePresentation(renderPetText(event.title, game.pet.name), reward, pending == null && message == null,
                storyAction = storyGame,
                sceneRes = eventSceneBackground(event.id, card?.scene),
                instructions = theme?.instructions ?: if (storyGame) when (kind) {
                    DeedGameKind.MEMORY -> "Найди одинаковые пары, чтобы закончить работу."
                    DeedGameKind.PRECISION -> "Останови маркер в зелёной зоне. Выполни пять точных движений, чтобы закончить работу."
                    DeedGameKind.COMPARISON -> "Сравни числа и выбери верный ответ."
                } else null,
                activityArtworkRes = theme?.objectRes ?: eventSceneArtwork(event.id, card?.character)?.resource,
                pairArtwork = theme?.pairs.orEmpty()),
            message = message,
            canRetry = pending != null || comparisonSaveFailed,
        )
    }

    private fun gameKind(occurrence: EventOccurrence): DeedGameKind? {
        val policy = session.catalog.policies[occurrence.eventId] ?: return null
        return if (choiceId == null) policy.deedGameKind.takeIf { occurrence.origin == EventOrigin.DEED }
        else policy.choiceGameKinds[choiceId].takeIf { occurrence.origin == EventOrigin.SCHEDULE }
    }
}
