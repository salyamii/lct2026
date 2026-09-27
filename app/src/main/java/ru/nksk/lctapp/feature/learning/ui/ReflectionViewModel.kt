package ru.nksk.lctapp.feature.learning.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.nksk.lctapp.domain.engine.*
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.history.AuditEntry
import ru.nksk.lctapp.domain.timemachine.*

internal data class ReflectionUiState(
    val loading: Boolean = true, val busy: Boolean = false, val error: String? = null,
    val reflectionDay: Int? = null, val reflectionScope: ReflectionScope = ReflectionScope.DAY,
    val timeMachine: TimeMachineAvailability? = null, val simulation: TimeMachineResult? = null,
    val quiz: TimeMachineQuiz? = null, val quizAnswer: TimeMachineQuizAnswer? = null,
    val chronoscopeStep: ChronoscopeStep? = null, val memory: ChronoscopeMemory? = null,
    val originalPath: ChronoscopePath? = null, val alternativePath: ChronoscopePath? = null,
    val realGame: GameState? = null, val quizHintShown: Boolean = false, val comparisonBoundary: String? = null,
)

internal sealed interface ReflectionAction {
    data object Retry : ReflectionAction
    data class SetReflectionScope(val scope: ReflectionScope) : ReflectionAction
    data object OpenTimeMachine : ReflectionAction
    data object ShowMoments : ReflectionAction
    data class SelectMoment(val entryId: String) : ReflectionAction
    data object ShowAlternatives : ReflectionAction
    data object ComparePaths : ReflectionAction
    data object ChronoscopeBack : ReflectionAction
    data object RetryQuiz : ReflectionAction
    data object ReturnToPresent : ReflectionAction
    data class Simulate(val entryId: String, val alternativeId: String) : ReflectionAction
    data object StartQuiz : ReflectionAction
    data class AnswerQuiz(val quizId: String, val id: String) : ReflectionAction
    data class ChronoscopeQuestionPresented(val quizId: String) : ReflectionAction
    data class ChronoscopeExplanationPresented(val quizId: String, val submissionId: String) : ReflectionAction
}

@HiltViewModel
internal class ReflectionViewModel @Inject constructor(private val session: GameSession) : ViewModel() {
    private val state = MutableStateFlow(ReflectionUiState())
    val uiState = state.asStateFlow()
    private var saved: GameState? = null
    private var history: List<AuditEntry> = emptyList()
    private var active = false
    private var observation: Job? = null
    private val observing: Boolean get() = observation?.isActive == true
    private data class QuizSubmission(val quizId: String, val optionId: String, val id: String,
        val questionPresented: Boolean, val usedHint: Boolean)
    private var pendingQuizSubmission: QuizSubmission? = null
    private val presentedChronoscopeQuestions = mutableSetOf<String>()
    private sealed interface ChronoscopeRecord {
        val key: String
        data class Simulation(val id: String) : ChronoscopeRecord { override val key = "simulation:$id" }
        data class Question(val id: String) : ChronoscopeRecord { override val key = "question:$id" }
        data class Explanation(val quizId: String, val submissionId: String) : ChronoscopeRecord { override val key = "explanation:$submissionId" }
    }
    private val pendingChronoscopeRecords = linkedMapOf<String, ChronoscopeRecord>()
    private val recordingChronoscope = mutableSetOf<String>()
    private val recordedChronoscope = mutableSetOf<String>()

    private var reflectionConfigured = false

    fun openReflection(day: Int) {
        if (reflectionConfigured || state.value.busy) return
        state.value = state.value.copy(reflectionDay = day, reflectionScope = ReflectionScope.DAY)
        if (!state.value.loading && saved != null && state.value.error == null) onAction(ReflectionAction.OpenTimeMachine)
    }

    private fun scopedAvailability(value: TimeMachineAvailability): TimeMachineAvailability =
        value.forReflection(state.value.reflectionDay, state.value.reflectionScope)

    /** Stop expensive reads while hidden; pending commands and telemetry keep their own jobs. */
    fun setActive(value: Boolean) {
        if (active == value) return
        active = value
        if (value) observe() else {
            observation?.cancel()
            observation = null
        }
    }

    private fun observe() {
        if (!active || observing) return
        observation = viewModelScope.launch {
            try {
                session.prepare()
                session.observe().collect { game ->
                    saved = checkNotNull(game)
                    history = session.history()
                    render(game)
                    if (state.value.error == HISTORY_READ_ERROR) state.value = state.value.copy(error = null)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { state.value = state.value.copy(loading = false, error = HISTORY_READ_ERROR) }
        }
    }

    private fun render(game: GameState) {
        val shownHistory = history
        val previousMemory = state.value.memory
        if (previousMemory != null && shownHistory.lastOrNull()?.runId != previousMemory.runId) {
            val oldRequest = state.value.simulation?.request ?: TimeMachineRequest(previousMemory.decision.entryId, "unavailable")
            state.value = state.value.copy(memory = null, originalPath = null, alternativePath = null,
                quiz = null, quizAnswer = null, comparisonBoundary = null,
                timeMachine = null, chronoscopeStep = ChronoscopeStep.UNAVAILABLE,
                simulation = TimeMachineResult(TimeMachineStatus.UNAVAILABLE, oldRequest,
                    reason = "Сейчас открыта другая история. Выбери момент заново."))
            pendingQuizSubmission = null
            pendingChronoscopeRecords.clear()
            recordedChronoscope.clear()
            presentedChronoscopeQuestions.clear()
        }
        state.value = state.value.copy(loading = false, realGame = game)
    }

    fun onAction(action: ReflectionAction) {
        if (action is ReflectionAction.ChronoscopeQuestionPresented) {
            if (state.value.chronoscopeStep == ChronoscopeStep.QUIZ && state.value.quiz?.id == action.quizId) {
                presentedChronoscopeQuestions += action.quizId
                recordChronoscope(ChronoscopeRecord.Question(action.quizId))
            }
            return
        }
        if (action is ReflectionAction.ChronoscopeExplanationPresented) {
            if (state.value.chronoscopeStep in setOf(ChronoscopeStep.RETRY, ChronoscopeStep.EXPLANATION) &&
                state.value.quizAnswer?.quizId == action.quizId && state.value.quizAnswer?.submissionId == action.submissionId) {
                recordChronoscope(ChronoscopeRecord.Explanation(action.quizId, action.submissionId))
            }
            return
        }
        if (state.value.busy) return
        if (action == ReflectionAction.Retry) {
            state.value = state.value.copy(error = null)
            val pending = pendingQuizSubmission
            if (pending != null && pending.quizId == state.value.quiz?.id && state.value.chronoscopeStep == ChronoscopeStep.QUIZ) {
                onAction(ReflectionAction.AnswerQuiz(pending.quizId, pending.optionId))
            } else if (pendingChronoscopeRecords.isNotEmpty()) {
                pendingChronoscopeRecords.values.toList().forEach { recordChronoscope(it, retry = true) }
            } else if (state.value.reflectionDay != null && state.value.chronoscopeStep == null && saved != null) {
                onAction(ReflectionAction.OpenTimeMachine)
            } else if (!observing) observe() else refresh()
            return
        }
        if (saved == null) return
        state.value = state.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                when (action) {
                    is ReflectionAction.SetReflectionScope -> {
                        val availability = session.timeMachine.availableReflections()
                            .forReflection(state.value.reflectionDay, action.scope)
                        history = session.history()
                        state.value = state.value.copy(reflectionScope = action.scope, timeMachine = availability, memory = null,
                            simulation = null, quiz = null, quizAnswer = null,
                            originalPath = null, alternativePath = null, chronoscopeStep = ChronoscopeStep.MOMENTS)
                    }
                    ReflectionAction.OpenTimeMachine -> {
                        val availability = scopedAvailability(session.timeMachine.availableReflections())
                        history = session.history()
                        val current = checkNotNull(session.read())
                        saved = current
                        render(current)
                        reflectionConfigured = true
                        state.value = state.value.copy(timeMachine = availability,
                            simulation = null, quiz = null, quizAnswer = null, memory = null,
                            originalPath = null, alternativePath = null, chronoscopeStep = ChronoscopeStep.MOMENTS,
                            quizHintShown = false, comparisonBoundary = null)
                        pendingQuizSubmission = null
                    }
                    ReflectionAction.ShowMoments -> {
                        val availability = scopedAvailability(session.timeMachine.availableReflections())
                        history = session.history()
                        state.value = state.value.copy(timeMachine = availability,
                            chronoscopeStep = ChronoscopeStep.MOMENTS)
                    }
                    is ReflectionAction.SelectMoment -> {
                        if (state.value.chronoscopeStep != ChronoscopeStep.MOMENTS) return@launch
                        val decision = state.value.timeMachine?.decisions?.find { it.entryId == action.entryId } ?: return@launch
                        val entry = history.find { it.id == action.entryId } ?: return@launch
                        val memory = chronoscopeMemory(decision, entry, session.catalog) ?: return@launch
                        state.value = state.value.copy(memory = memory, simulation = null, quiz = null, quizAnswer = null,
                            originalPath = null, alternativePath = null, chronoscopeStep = ChronoscopeStep.ALTERNATIVES,
                            quizHintShown = false, comparisonBoundary = null)
                        pendingQuizSubmission = null
                    }
                    ReflectionAction.ShowAlternatives -> if (state.value.memory != null && state.value.chronoscopeStep == ChronoscopeStep.MEMORY) {
                        state.value = state.value.copy(chronoscopeStep = ChronoscopeStep.ALTERNATIVES)
                    }
                    ReflectionAction.ComparePaths -> if (state.value.originalPath != null && state.value.alternativePath != null) {
                        state.value = state.value.copy(chronoscopeStep = ChronoscopeStep.COMPARISON)
                    }
                    ReflectionAction.ChronoscopeBack -> state.value.chronoscopeStep?.let { step ->
                        state.value = state.value.copy(chronoscopeStep = chronoscopeBack(step))
                    }
                    ReflectionAction.ReturnToPresent -> {
                        // Read/observe the actual aggregate; never restore the pre-simulation copy.
                        val current = checkNotNull(session.read()) { "The real save is unavailable" }
                        saved = current
                        history = session.history()
                        render(current)
                        state.value = state.value.copy(chronoscopeStep = ChronoscopeStep.PRESENT)
                    }
                    ReflectionAction.RetryQuiz -> if (state.value.chronoscopeStep == ChronoscopeStep.RETRY && state.value.quiz != null) {
                        state.value = state.value.copy(chronoscopeStep = ChronoscopeStep.QUIZ, quizAnswer = null, quizHintShown = true)
                    }
                    is ReflectionAction.Simulate -> {
                        if (state.value.chronoscopeStep != ChronoscopeStep.ALTERNATIVES) return@launch
                        val memory = state.value.memory?.takeIf { it.decision.entryId == action.entryId } ?: return@launch
                        if (memory.decision.alternatives.none { it.id == action.alternativeId }) return@launch
                        val target = history.find { it.id == action.entryId }
                        val day = target?.after?.engine?.day
                        val through = history.lastOrNull { it.sequence >= (target?.sequence ?: Long.MAX_VALUE) &&
                            it.after?.engine?.day == day }?.sequence ?: target?.sequence
                        val result = session.timeMachine.simulate(TimeMachineRequest(action.entryId, action.alternativeId, through))
                        val comparable = result.hasComparablePaths(memory.decision.sequence)
                        val quiz = result.simulationId?.takeIf { comparable }?.let { id ->
                            session.timeMachine.quiz(id, TimeMachineQuizKind.CAUSE)
                                ?: session.timeMachine.quiz(id, TimeMachineQuizKind.LEDGER)
                        }
                        state.value = state.value.copy(simulation = result, quiz = quiz, quizAnswer = null,
                            originalPath = if (comparable) chronoscopePath(requireNotNull(result.baseline), memory.before, session.catalog) else null,
                            alternativePath = if (comparable) chronoscopePath(requireNotNull(result.alternative), memory.before, session.catalog) else null,
                            chronoscopeStep = if (comparable) ChronoscopeStep.COMPARISON else ChronoscopeStep.UNAVAILABLE,
                            quizHintShown = false,
                            comparisonBoundary = if (comparable) chronoscopeBoundary(result, history, session.catalog) else null)
                        pendingQuizSubmission = null
                        result.simulationId?.let { recordChronoscope(ChronoscopeRecord.Simulation(it)) }
                    }
                    ReflectionAction.StartQuiz -> state.value.simulation?.simulationId?.let {
                        if (state.value.chronoscopeStep != ChronoscopeStep.COMPARISON) return@launch
                        // Reopening comparison keeps the same question and its attempt history.
                        val quiz = state.value.quiz
                        state.value = state.value.copy(quiz = quiz,
                            chronoscopeStep = if (quiz == null || state.value.quizAnswer?.correct == true)
                                ChronoscopeStep.EXPLANATION else ChronoscopeStep.QUIZ)
                        if (pendingQuizSubmission?.quizId != quiz?.id) pendingQuizSubmission = null
                    }
                    is ReflectionAction.AnswerQuiz -> state.value.quiz?.let { quiz ->
                        if (state.value.chronoscopeStep != ChronoscopeStep.QUIZ || action.quizId != quiz.id ||
                            quiz.options.none { it.id == action.id }) return@launch
                        val previous = pendingQuizSubmission
                        if (previous != null && previous.quizId == quiz.id && previous.optionId != action.id) {
                            state.value = state.value.copy(error = "Сначала сохраним предыдущий ответ. Нажми «Повторить».")
                            return@launch
                        }
                        val submission = previous?.takeIf { it.quizId == quiz.id && it.optionId == action.id }
                            ?: QuizSubmission(quiz.id, action.id, UUID.randomUUID().toString(),
                                questionPresented = quiz.id in presentedChronoscopeQuestions,
                                usedHint = state.value.quizHintShown).also { pendingQuizSubmission = it }
                        val answer = session.timeMachine.submitQuiz(quiz.id, action.id, submission.id,
                            usedHint = submission.usedHint, questionPresented = submission.questionPresented)
                        state.value = state.value.copy(quizAnswer = answer,
                            quizHintShown = state.value.quizHintShown || !answer.correct,
                            chronoscopeStep = if (answer.correct) ChronoscopeStep.EXPLANATION else ChronoscopeStep.RETRY)
                        pendingQuizSubmission = null
                    }
                    ReflectionAction.Retry -> Unit
                    is ReflectionAction.ChronoscopeQuestionPresented, is ReflectionAction.ChronoscopeExplanationPresented -> Unit
                }
                if (!observing) observe()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { state.value = state.value.copy(error = "Не удалось выполнить действие. Попробуй ещё раз.") }
            finally { state.value = state.value.copy(busy = false) }
        }
    }

    private fun refresh() {
        state.value = state.value.copy(busy = true)
        viewModelScope.launch {
            try {
                val current = checkNotNull(session.read())
                history = session.history()
                saved = current
                render(current)
                if (!observing) observe()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { state.value = state.value.copy(error = "Не удалось обновить разбор. Повтори попытку.") }
            finally { state.value = state.value.copy(busy = false) }
        }
    }

    /** Telemetry cannot block navigation or turn a successful replay into a failed game action. */
    private fun recordChronoscope(record: ChronoscopeRecord, retry: Boolean = false) {
        if (record.key in recordedChronoscope || record.key in recordingChronoscope ||
            !retry && record.key in pendingChronoscopeRecords) return
        pendingChronoscopeRecords[record.key] = record
        recordingChronoscope += record.key
        viewModelScope.launch {
            try {
                when (record) {
                    is ChronoscopeRecord.Simulation -> session.timeMachine.recordSimulationLifecycle(record.id)
                    is ChronoscopeRecord.Question -> session.timeMachine.recordQuestionPresented(record.id)
                    is ChronoscopeRecord.Explanation -> session.timeMachine.recordExplanationShown(record.quizId, record.submissionId)
                }
                recordedChronoscope += record.key
                pendingChronoscopeRecords.remove(record.key)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (record.key in pendingChronoscopeRecords) {
                    state.value = state.value.copy(error = "Не удалось сохранить разбор. Можно повторить или вернуться к приключению.")
                }
            } finally { recordingChronoscope -= record.key }
        }
    }

    private companion object {
        const val HISTORY_READ_ERROR = "Не удалось прочитать историю. Повтори попытку."
    }

}
