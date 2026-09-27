package ru.nksk.lctapp.feature.learning.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.nksk.lctapp.core.ui.game.playerMessage
import ru.nksk.lctapp.domain.engine.*
import ru.nksk.lctapp.domain.finance.FinancialQuestion
import ru.nksk.lctapp.domain.finance.FinancialQuestionKind
import ru.nksk.lctapp.domain.finance.FinancialBudgetProjection
import ru.nksk.lctapp.domain.finance.FinancialProgressionPolicy
import ru.nksk.lctapp.domain.economy.BudgetRevisionReason
import ru.nksk.lctapp.domain.economy.BudgetSection
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.history.AuditEntry
import ru.nksk.lctapp.domain.analytics.DecisionContext
import ru.nksk.lctapp.domain.timemachine.*

internal data class PeriodUi(val title: String, val body: String, val plan: String,
    val actual: List<String> = emptyList(), val comparisons: List<BudgetComparisonUi> = emptyList(), val note: String? = null)
internal data class BudgetComparisonUi(val title: String, val rows: List<String>, val note: String)
internal data class LearningUiState(
    val loading: Boolean = true, val busy: Boolean = false, val error: String? = null,
    val money: String = "", val periods: List<PeriodUi> = emptyList(), val operations: List<String> = emptyList(),
    val canReview: Boolean = false,
    val question: FinancialQuestion? = null,
    val practiceOpen: Boolean = true,
    val reflectionDay: Int? = null,
    val reflectionScope: ReflectionScope = ReflectionScope.DAY,
    val needsBudgetRevision: Boolean = false,
    val needsBudgetPlanning: Boolean = false,
    val practiceRetryRequired: Boolean = false,
    val timeMachine: TimeMachineAvailability? = null, val simulation: TimeMachineResult? = null,
    val quiz: TimeMachineQuiz? = null, val quizAnswer: TimeMachineQuizAnswer? = null,
    val chronoscopeStep: ChronoscopeStep? = null,
    val memory: ChronoscopeMemory? = null,
    val originalPath: ChronoscopePath? = null, val alternativePath: ChronoscopePath? = null,
    val realGame: GameState? = null,
    val quizHintShown: Boolean = false,
    val comparisonBoundary: String? = null,
)

internal sealed interface LearningAction {
    data object Retry : LearningAction
    data object Review : LearningAction
    data object ReviewConsequences : LearningAction
    data object ReviewTransactions : LearningAction
    data object PracticeSaving : LearningAction
    data class Answer(val id: String) : LearningAction
    data class QuestionPresented(val id: String) : LearningAction
    data object CloseQuestion : LearningAction
    data class NextQuestion(val questionId: String) : LearningAction
    data class SetReflectionScope(val scope: ReflectionScope) : LearningAction
    data object OpenTimeMachine : LearningAction
    data object ShowMoments : LearningAction
    data class SelectMoment(val entryId: String) : LearningAction
    data object ShowAlternatives : LearningAction
    data object ComparePaths : LearningAction
    data object ChronoscopeBack : LearningAction
    data object RetryQuiz : LearningAction
    data object ReturnToPresent : LearningAction
    data class Simulate(val entryId: String, val alternativeId: String) : LearningAction
    data object StartQuiz : LearningAction
    data class AnswerQuiz(val quizId: String, val id: String) : LearningAction
    data class ChronoscopeQuestionPresented(val quizId: String) : LearningAction
    data class ChronoscopeExplanationPresented(val quizId: String, val submissionId: String) : LearningAction
}

@HiltViewModel
internal class LearningViewModel @Inject constructor(private val session: GameSession) : ViewModel() {
    private val state = MutableStateFlow(LearningUiState())
    val uiState = state.asStateFlow()
    private var saved: GameState? = null
    private var history: List<AuditEntry> = emptyList()
    private var observing = false
    private data class QuizSubmission(val quizId: String, val optionId: String, val id: String,
        val questionPresented: Boolean, val usedHint: Boolean)
    private var pendingQuizSubmission: QuizSubmission? = null
    private val presentedChronoscopeQuestions = mutableSetOf<String>()
    private var presentedQuestion: String? = null
    private var pendingPracticeRequest: EngineRequest? = null
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
        if (!state.value.loading && saved != null) onAction(LearningAction.OpenTimeMachine)
    }

    private fun scopedAvailability(value: TimeMachineAvailability): TimeMachineAvailability =
        value.forReflection(state.value.reflectionDay, state.value.reflectionScope)

    init { observe() }

    private fun observe() {
        if (observing) return
        observing = true
        viewModelScope.launch {
            try {
                session.prepare()
                session.observe().collect { game ->
                    saved = checkNotNull(game)
                    history = session.history()
                    render(game)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { state.value = state.value.copy(loading = false, error = "Не удалось прочитать историю. Повтори попытку.") }
            finally { observing = false }
        }
    }

    private suspend fun render(game: GameState) {
        val shownHistory = history
        val reports = withContext(Dispatchers.Default) {
            FinancialBudgetProjection.report(game, shownHistory, session.catalog.content).associateBy { it.periodId }
        }
        if (saved != game) return
        val periods = game.financial.periods.reversed().map { period ->
            val report = reports.getValue(period.id)
            PeriodUi("Период ${period.ordinal}: ${session.catalog.content.goals.find { it.id == period.goalId }?.title ?: "Большая цель"}",
                "Получили ${period.income} монет, потратили ${period.spentAvailable + period.spentSavings}. " +
                    "В копилку положили ${period.deposited}, обратно взяли ${period.withdrawn}.", plan = "",
                actual = with(report.actual) { buildList {
                    add("На необходимое потратили $needs монет")
                    add("На приятные покупки потратили $wants монет")
                    add("На неожиданности и другие покупки потратили $reserve монет")
                    add("Пополнения копилки за вычетом снятого: $netSaved монет")
                    add("Из копилки потратили $goalPurchases монет на снаряжение для цели")
                    if (unknownExpenses > 0) add("Назначение старых трат неизвестно: $unknownExpenses монет")
                } },
                comparisons = report.comparisons.map { comparison ->
                    val revision = comparison.revision
                    val reason = when (revision.reason) {
                        BudgetRevisionReason.INITIAL -> "Начальный план."
                        BudgetRevisionReason.KNOWN_NEED_OMITTED -> "Вспомнили, что ещё понадобится."
                        BudgetRevisionReason.UNEXPECTED_EXPENSE -> "Пересмотрели после неожиданной траты."
                        BudgetRevisionReason.NEW_INCOME -> "Получили новые монеты."
                        BudgetRevisionReason.CHANGED_PRIORITY -> "Решили, что сейчас важнее."
                        BudgetRevisionReason.UNSPECIFIED -> "Причина изменения не указана."
                    }
                    val interval = if (comparison.nextRevisionId != null) "Здесь все действия до следующего изменения плана."
                        else if (comparison.finalised) "Здесь все действия после этого плана до конца главы."
                        else "Глава ещё идёт. Здесь всё, что произошло после этого плана к текущему моменту."
                    BudgetComparisonUi(if (revision.ordinal == 1) "Первоначальный план, день ${revision.day}"
                        else "План ${revision.ordinal}, день ${revision.day}",
                        BudgetSection.entries.map { section ->
                            val label = when (section) {
                                BudgetSection.NEEDS -> "Нужно"
                                BudgetSection.WANTS -> "Хочу"
                                BudgetSection.SAVINGS -> "В копилку"
                                BudgetSection.RESERVE -> "Запас"
                            }
                            val planned = revision.allocation.amount(section)
                            val actual = comparison.actual.amount(section)
                            val difference = when {
                                !comparison.complete -> "часть истории неизвестна"
                                actual > planned -> "на ${actual - planned} больше"
                                actual < planned -> "на ${planned - actual} меньше"
                                else -> "совпадает"
                            }
                            if (comparison.complete) "$label: планировали $planned, получилось $actual ($difference)"
                            else "$label: планировали $planned, в истории есть $actual ($difference)"
                        }, "$reason $interval")
                },
                note = if (!report.complete) "В старой истории не хватает подробностей. Показываем только то, что знаем точно."
                    else "Чтобы сравнить накопления с планом, из пополнений вычитаем монеты, которые взяли обратно. Купленное для цели показываем отдельно.")
        }
        val operations = learningHistoryRows(shownHistory, session.catalog, game.pet.name)
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
        val displayedPractice = withContext(Dispatchers.Default) {
            financialPracticePresentation(game.financial.practice, shownHistory, session.catalog)
        }
        if (saved != game) return
        state.value = state.value.copy(loading = false, realGame = game,
            money = "Можно потратить: ${game.economy.availableBalance}. В копилке: ${game.economy.savingsBalance}",
            periods = periods, operations = operations, question = displayedPractice,
            canReview = game.economy.planning == null && game.economy.unallocated == 0L,
            needsBudgetPlanning = game.economy.planning != null || game.economy.unallocated != 0L,
            needsBudgetRevision = game.financial.currentPeriod?.reviewEvidence?.let {
                it.answerCorrect && !FinancialProgressionPolicy.reviewReady(it)
            } == true)
    }

    fun onAction(action: LearningAction) {
        if (action is LearningAction.ChronoscopeQuestionPresented) {
            if (state.value.chronoscopeStep == ChronoscopeStep.QUIZ && state.value.quiz?.id == action.quizId) {
                presentedChronoscopeQuestions += action.quizId
                recordChronoscope(ChronoscopeRecord.Question(action.quizId))
            }
            return
        }
        if (action is LearningAction.ChronoscopeExplanationPresented) {
            if (state.value.chronoscopeStep in setOf(ChronoscopeStep.RETRY, ChronoscopeStep.EXPLANATION) &&
                state.value.quizAnswer?.quizId == action.quizId && state.value.quizAnswer?.submissionId == action.submissionId) {
                recordChronoscope(ChronoscopeRecord.Explanation(action.quizId, action.submissionId))
            }
            return
        }
        if (action is LearningAction.QuestionPresented) {
            if (saved?.financial?.practice?.id == action.id) presentedQuestion = action.id
            return
        }
        if (state.value.busy) return
        if (action == LearningAction.Retry) {
            state.value = state.value.copy(error = null)
            val pending = pendingQuizSubmission
            if (pendingPracticeRequest != null) {
                retryPracticeOrRefresh(pendingPracticeRequest)
            } else if (pending != null && pending.quizId == state.value.quiz?.id && state.value.chronoscopeStep == ChronoscopeStep.QUIZ) {
                onAction(LearningAction.AnswerQuiz(pending.quizId, pending.optionId))
            } else if (pendingChronoscopeRecords.isNotEmpty()) {
                pendingChronoscopeRecords.values.toList().forEach { recordChronoscope(it, retry = true) }
            } else if (state.value.reflectionDay != null && state.value.chronoscopeStep == null && saved != null) {
                onAction(LearningAction.OpenTimeMachine)
            } else if (observing) retryPracticeOrRefresh(null) else observe()
            return
        }
        if (action.isPracticeAction()) {
            if (pendingPracticeRequest != null) {
                state.value = state.value.copy(error = "Сначала сохраним предыдущее действие. Нажми «Повторить».",
                    practiceRetryRequired = true)
                return
            }
            if (state.value.needsBudgetPlanning) {
                state.value = state.value.copy(error = "Сначала заверши план монет, затем вернёмся к практике.")
                return
            }
        }
        val game = saved ?: return
        state.value = state.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                when (action) {
                    LearningAction.Review -> command(game, EngineCommand.RequestFinancialPractice(series = true))
                    LearningAction.ReviewConsequences -> command(game,
                        EngineCommand.RequestFinancialPractice(FinancialQuestionKind.CONSEQUENCE, series = true))
                    LearningAction.ReviewTransactions -> command(game,
                        EngineCommand.RequestFinancialPractice(FinancialQuestionKind.TRANSACTION_ACCOUNTING, series = true))
                    LearningAction.PracticeSaving -> command(game,
                        EngineCommand.RequestFinancialPractice(FinancialQuestionKind.SAVING_PRACTICE, series = true))
                    is LearningAction.Answer -> game.financial.practice?.let {
                        command(game, EngineCommand.AnswerFinancialQuestion(it.id, action.id))
                    }
                    LearningAction.CloseQuestion -> {
                        command(game, EngineCommand.CloseFinancialPractice)
                        if (pendingPracticeRequest == null) state.value = state.value.copy(practiceOpen = false)
                    }
                    is LearningAction.NextQuestion -> command(game, EngineCommand.AdvanceFinancialPractice(action.questionId))
                    is LearningAction.SetReflectionScope -> {
                        val availability = session.timeMachine.availableReflections()
                            .forReflection(state.value.reflectionDay, action.scope)
                        history = session.history()
                        state.value = state.value.copy(reflectionScope = action.scope, timeMachine = availability, memory = null,
                            simulation = null, quiz = null, quizAnswer = null,
                            originalPath = null, alternativePath = null, chronoscopeStep = ChronoscopeStep.MOMENTS)
                    }
                    LearningAction.OpenTimeMachine -> {
                        val availability = scopedAvailability(session.timeMachine.availableReflections())
                        history = session.history()
                        reflectionConfigured = true
                        state.value = state.value.copy(timeMachine = availability,
                            simulation = null, quiz = null, quizAnswer = null, memory = null,
                            originalPath = null, alternativePath = null, chronoscopeStep = ChronoscopeStep.MOMENTS,
                            quizHintShown = false, comparisonBoundary = null)
                        pendingQuizSubmission = null
                    }
                    LearningAction.ShowMoments -> {
                        val availability = scopedAvailability(session.timeMachine.availableReflections())
                        history = session.history()
                        state.value = state.value.copy(timeMachine = availability,
                            chronoscopeStep = ChronoscopeStep.MOMENTS)
                    }
                    is LearningAction.SelectMoment -> {
                        if (state.value.chronoscopeStep != ChronoscopeStep.MOMENTS) return@launch
                        val decision = state.value.timeMachine?.decisions?.find { it.entryId == action.entryId } ?: return@launch
                        val entry = history.find { it.id == action.entryId } ?: return@launch
                        val memory = chronoscopeMemory(decision, entry, session.catalog) ?: return@launch
                        state.value = state.value.copy(memory = memory, simulation = null, quiz = null, quizAnswer = null,
                            originalPath = null, alternativePath = null, chronoscopeStep = ChronoscopeStep.ALTERNATIVES,
                            quizHintShown = false, comparisonBoundary = null)
                        pendingQuizSubmission = null
                    }
                    LearningAction.ShowAlternatives -> if (state.value.memory != null && state.value.chronoscopeStep == ChronoscopeStep.MEMORY) {
                        state.value = state.value.copy(chronoscopeStep = ChronoscopeStep.ALTERNATIVES)
                    }
                    LearningAction.ComparePaths -> if (state.value.originalPath != null && state.value.alternativePath != null) {
                        state.value = state.value.copy(chronoscopeStep = ChronoscopeStep.COMPARISON)
                    }
                    LearningAction.ChronoscopeBack -> state.value.chronoscopeStep?.let { step ->
                        state.value = state.value.copy(chronoscopeStep = chronoscopeBack(step))
                    }
                    LearningAction.ReturnToPresent -> {
                        // Read/observe the actual aggregate; never restore the pre-simulation copy.
                        val current = checkNotNull(session.read()) { "The real save is unavailable" }
                        saved = current
                        history = session.history()
                        render(current)
                        state.value = state.value.copy(chronoscopeStep = ChronoscopeStep.PRESENT)
                    }
                    LearningAction.RetryQuiz -> if (state.value.chronoscopeStep == ChronoscopeStep.RETRY && state.value.quiz != null) {
                        state.value = state.value.copy(chronoscopeStep = ChronoscopeStep.QUIZ, quizAnswer = null, quizHintShown = true)
                    }
                    is LearningAction.Simulate -> {
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
                    LearningAction.StartQuiz -> state.value.simulation?.simulationId?.let {
                        if (state.value.chronoscopeStep != ChronoscopeStep.COMPARISON) return@launch
                        // Reopening comparison keeps the same question and its attempt history.
                        val quiz = state.value.quiz
                        state.value = state.value.copy(quiz = quiz,
                            chronoscopeStep = if (quiz == null || state.value.quizAnswer?.correct == true)
                                ChronoscopeStep.EXPLANATION else ChronoscopeStep.QUIZ)
                        if (pendingQuizSubmission?.quizId != quiz?.id) pendingQuizSubmission = null
                    }
                    is LearningAction.AnswerQuiz -> state.value.quiz?.let { quiz ->
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
                    LearningAction.Retry -> Unit
                    is LearningAction.QuestionPresented -> Unit
                    is LearningAction.ChronoscopeQuestionPresented, is LearningAction.ChronoscopeExplanationPresented -> Unit
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { showActionFailure() }
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

    private suspend fun command(game: GameState, command: EngineCommand) {
        val context = (command as? EngineCommand.AnswerFinancialQuestion)?.let {
            DecisionContext(presentationId = "question:${it.questionId}",
                informationPresented = presentedQuestion == it.questionId,
                complete = presentedQuestion == it.questionId)
        }
        val request = EngineRequest(UUID.randomUUID().toString(), game.engine?.revision, command, context)
        pendingPracticeRequest = request
        applyPracticeRequest(request)
    }

    /** Preserve the original identity, revision and displayed context across uncertain writes. */
    private suspend fun applyPracticeRequest(request: EngineRequest) {
        when (val result = session.dispatch(request)) {
            is EngineResult.Applied -> {
                if (request.command is EngineCommand.RequestFinancialPractice || request.command is EngineCommand.AdvanceFinancialPractice)
                    state.value = state.value.copy(practiceOpen = true)
                if (request.command is EngineCommand.CloseFinancialPractice)
                    state.value = state.value.copy(practiceOpen = false)
                saved = result.state
                history = session.history()
                render(result.state)
                pendingPracticeRequest = null
                state.value = state.value.copy(error = null, practiceRetryRequired = false)
            }
            is EngineResult.Blocked -> {
                // Rejection is definite. A fresh answer must use the refreshed question;
                // never silently turn this request into a new command at a newer revision.
                pendingPracticeRequest = null
                presentedQuestion = null
                val reason = result.reason.playerMessage(saved?.pet?.name.orEmpty())
                state.value = state.value.copy(error = reason, practiceRetryRequired = false)
                try {
                    refreshPracticeState()
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { state.value = state.value.copy(error = "$reason Не удалось обновить историю. Повтори загрузку.") }
            }
        }
    }

    private suspend fun refreshPracticeState() {
        val current = checkNotNull(session.read()) { "The real save is unavailable" }
        saved = current
        history = session.history()
        render(current)
    }

    private fun retryPracticeOrRefresh(request: EngineRequest?) {
        state.value = state.value.copy(busy = true)
        viewModelScope.launch {
            try {
                if (request != null) applyPracticeRequest(request) else refreshPracticeState()
                if (!observing) observe()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { showActionFailure() }
            finally { state.value = state.value.copy(busy = false) }
        }
    }

    private fun showActionFailure() {
        state.value = state.value.copy(error = "Не удалось выполнить действие. Попробуй ещё раз.",
            practiceRetryRequired = pendingPracticeRequest != null)
    }
}

private fun LearningAction.isPracticeAction(): Boolean = when (this) {
    LearningAction.Review, LearningAction.ReviewConsequences, LearningAction.ReviewTransactions,
    LearningAction.PracticeSaving, is LearningAction.Answer, is LearningAction.NextQuestion, LearningAction.CloseQuestion -> true
    else -> false
}
