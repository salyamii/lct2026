package ru.nksk.lctapp.feature.learning.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.nksk.lctapp.core.ui.game.GameActionAttempt
import ru.nksk.lctapp.core.ui.game.playerMessage
import ru.nksk.lctapp.domain.analytics.DecisionContext
import ru.nksk.lctapp.domain.engine.*
import ru.nksk.lctapp.domain.finance.FinancialQuestion
import ru.nksk.lctapp.domain.finance.FinancialQuestionKind
import ru.nksk.lctapp.domain.finance.FinancialProgressionPolicy
import ru.nksk.lctapp.domain.game.GameState

internal data class TrainingUiState(
    val loading: Boolean = true, val busy: Boolean = false, val error: String? = null,
    val hasGame: Boolean = false, val canReview: Boolean = false,
    val question: FinancialQuestion? = null, val practiceOpen: Boolean = true,
    val needsBudgetRevision: Boolean = false, val needsBudgetPlanning: Boolean = false,
    val practiceRetryRequired: Boolean = false,
    val chapterPractice: Boolean = false,
    val demoMode: Boolean = false,
    val chapterStep: ChapterPracticeStep? = null,
)

internal sealed interface TrainingAction {
    data object Retry : TrainingAction
    data object Review : TrainingAction
    data object ReviewConsequences : TrainingAction
    data object ReviewTransactions : TrainingAction
    data object PracticeSaving : TrainingAction
    data object StartChapterPractice : TrainingAction
    data object SkipChapterPractice : TrainingAction
    data class Answer(val id: String) : TrainingAction
    data class QuestionPresented(val id: String) : TrainingAction
    data object CloseQuestion : TrainingAction
    data class NextQuestion(val questionId: String) : TrainingAction
}

internal enum class TrainingContinuationDestination { DAY, BUDGET }

@HiltViewModel
internal class TrainingViewModel @Inject constructor(private val session: GameSession) : ViewModel() {
    private val state = MutableStateFlow(TrainingUiState())
    val uiState = state.asStateFlow()
    private var saved: GameState? = null
    private var observation: Job? = null
    private val observing: Boolean get() = observation?.isActive == true
    private var screenActive = true
    private var presentedQuestion: String? = null
    private var pendingPracticeRequest: EngineRequest? = null
    private var pendingContinuation: GameActionAttempt? = null
    private val continuationNavigation = Channel<TrainingContinuationDestination>(Channel.BUFFERED)
    val openContinuation = continuationNavigation.receiveAsFlow()
    private var refreshRequired = false
    private val presentation = TrainingQuestionPresentation(session)

    init { observe() }

    fun setActive(active: Boolean) {
        if (screenActive == active) return
        screenActive = active
        if (active) observe() else { observation?.cancel(); observation = null }
    }

    fun setChapterPractice() {
        if (state.value.chapterPractice) return
        state.value = state.value.copy(chapterPractice = true, practiceOpen = false,
            chapterStep = saved?.chapterPracticeStep())
    }

    private fun observe() {
        if (!screenActive || observing) return
        observation = viewModelScope.launch {
            try {
                session.prepare()
                session.observe().collect { game ->
                    saved = checkNotNull(game)
                    render(game)
                    if (refreshRequired) {
                        refreshRequired = false
                        state.value = state.value.copy(practiceRetryRequired = pendingPracticeRequest != null)
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                refreshRequired = true
                state.value = state.value.copy(loading = false, practiceRetryRequired = true,
                    error = "Не удалось открыть тренировку. Повтори попытку.")
            }
        }
    }

    private suspend fun render(game: GameState) {
        if (!screenActive) return
        val question = presentation.display(game.financial.practice)
        if (!screenActive || saved != game) return
        state.value = state.value.copy(loading = false, hasGame = true, question = question,
            demoMode = session.demoModeEnabled,
            chapterStep = game.chapterPracticeStep().takeIf { state.value.chapterPractice },
            canReview = game.economy.planning == null && game.economy.unallocated == 0L,
            needsBudgetPlanning = game.economy.planning != null || game.economy.unallocated != 0L,
            needsBudgetRevision = game.financial.currentPeriod?.reviewEvidence?.let {
                it.answerCorrect && !FinancialProgressionPolicy.reviewReady(it)
            } == true)
    }

    fun onAction(action: TrainingAction) {
        if (action is TrainingAction.QuestionPresented) {
            if (saved?.financial?.practice?.id == action.id) presentedQuestion = action.id
            return
        }
        if (state.value.busy) return
        if (action == TrainingAction.Retry) {
            state.value = state.value.copy(error = null)
            if (pendingContinuation != null) { continueStory(); return }
            if (pendingPracticeRequest != null || observing) retryPracticeOrRefresh(pendingPracticeRequest) else observe()
            return
        }
        if (pendingPracticeRequest != null || pendingContinuation != null || refreshRequired) {
            state.value = state.value.copy(error = "Сначала сохраним предыдущее действие. Нажми «Повторить».",
                practiceRetryRequired = true)
            return
        }
        if (action == TrainingAction.SkipChapterPractice) { continueStory(skipPractice = true); return }
        if (state.value.needsBudgetPlanning) {
            state.value = state.value.copy(error = "Сначала распредели бюджет, затем вернёмся к практике.")
            return
        }
        val game = saved ?: return
        if (action == TrainingAction.StartChapterPractice &&
            (!state.value.chapterPractice || game.chapterPracticeStep().questionKind == null)) return
        if (action is TrainingAction.NextQuestion &&
            (state.value.chapterPractice || !state.value.practiceOpen || game.financial.practice?.let {
                it.id == action.questionId && it.correct && it.series != null
            } != true)) return
        state.value = state.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                when (action) {
                    TrainingAction.StartChapterPractice -> command(game, EngineCommand.RequestFinancialPractice(
                        kind = checkNotNull(game.chapterPracticeStep().questionKind), series = true))
                    TrainingAction.Review -> command(game, EngineCommand.RequestFinancialPractice(series = true))
                    TrainingAction.ReviewConsequences -> command(game,
                        EngineCommand.RequestFinancialPractice(FinancialQuestionKind.CONSEQUENCE, series = true))
                    TrainingAction.ReviewTransactions -> command(game,
                        EngineCommand.RequestFinancialPractice(FinancialQuestionKind.TRANSACTION_ACCOUNTING, series = true))
                    TrainingAction.PracticeSaving -> command(game,
                        EngineCommand.RequestFinancialPractice(FinancialQuestionKind.SAVING_PRACTICE, series = true))
                    is TrainingAction.Answer -> game.financial.practice?.let {
                        command(game, EngineCommand.AnswerFinancialQuestion(it.id, action.id))
                    }
                    TrainingAction.CloseQuestion -> {
                        command(game, EngineCommand.CloseFinancialPractice)
                        if (pendingPracticeRequest == null) state.value = state.value.copy(practiceOpen = false)
                    }
                    is TrainingAction.NextQuestion -> {
                        val question = checkNotNull(game.financial.practice)
                        command(game, if (question.series?.isLast == true)
                            EngineCommand.RequestFinancialPractice(question.kind, series = true)
                        else EngineCommand.AdvanceFinancialPractice(action.questionId))
                    }
                    TrainingAction.Retry, TrainingAction.SkipChapterPractice, is TrainingAction.QuestionPresented -> Unit
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { showActionFailure() }
            finally { state.value = state.value.copy(busy = false) }
        }
    }

    /** An explicit continuation advances the saved day before leaving the completed chapter review. */
    fun continueStory(skipPractice: Boolean = false) {
        val shown = state.value
        if (shown.busy || !shown.chapterPractice || pendingPracticeRequest != null ||
            refreshRequired && pendingContinuation == null) return
        val demoSkip = skipPractice && shown.demoMode && session.demoModeEnabled
        if (skipPractice && !demoSkip) return
        if (pendingContinuation == null && !demoSkip && shown.chapterStep !in setOf(
                ChapterPracticeStep.COMPLETE, ChapterPracticeStep.FOOD)) return
        state.value = shown.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                val current = checkNotNull(session.read()) { "The real save is unavailable" }
                saved = current
                refreshRequired = false
                if (!observing) observe()
                val mode = session.demoModeEnabled
                val attempt = pendingContinuation ?: when (val plan = withContext(Dispatchers.Default) {
                    session.continueDayPlan(current, mode)
                }) {
                    ContinueDayPlan.NeedsBudget -> {
                        continuationNavigation.send(TrainingContinuationDestination.BUDGET)
                        return@launch
                    }
                    is ContinueDayPlan.Day -> {
                        val command = plan.command
                        if (command == null) {
                            continuationNavigation.send(TrainingContinuationDestination.DAY)
                            return@launch
                        }
                        GameActionAttempt.prepare(current, command).also { pendingContinuation = it }
                    }
                }
                when (val result = attempt.submit(session)) {
                    is EngineResult.Applied -> {
                        pendingContinuation = null
                        if ((saved?.engine?.revision ?: -1) <= (result.state.engine?.revision ?: -1)) saved = result.state
                        val committed = checkNotNull(saved)
                        continuationNavigation.send(if (committed.economy.planning != null || committed.economy.unallocated != 0L)
                            TrainingContinuationDestination.BUDGET else TrainingContinuationDestination.DAY)
                    }
                    is EngineResult.Blocked -> {
                        pendingContinuation = null
                        val refreshed = checkNotNull(session.read())
                        saved = refreshed
                        render(refreshed)
                        when (result.reason) {
                            BlockReason.BudgetPlanningRequired -> continuationNavigation.send(TrainingContinuationDestination.BUDGET)
                            BlockReason.MustEat, BlockReason.MustSleep, BlockReason.EventInProgress,
                            BlockReason.DayFinished, BlockReason.NoNextEvent -> continuationNavigation.send(TrainingContinuationDestination.DAY)
                            else -> state.value = state.value.copy(error = result.reason.playerMessage(refreshed.pet.name))
                        }
                    }
                }
                state.value = state.value.copy(practiceRetryRequired = false)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                state.value = state.value.copy(error = "Не удалось продолжить историю. Повтори попытку.",
                    practiceRetryRequired = true)
            } finally { state.value = state.value.copy(busy = false) }
        }
    }

    private suspend fun command(game: GameState, command: EngineCommand) {
        val context = (command as? EngineCommand.AnswerFinancialQuestion)?.let {
            DecisionContext(presentationId = "question:${it.questionId}",
                informationPresented = presentedQuestion == it.questionId,
                complete = presentedQuestion == it.questionId)
        }
        val request = EngineRequest(UUID.randomUUID().toString(), game.engine?.revision, command, context)
        if (command is EngineCommand.RequestFinancialPractice) {
            presentation.expectCurrentQuestion("${request.id}:question")
        }
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
                pendingPracticeRequest = null
                render(result.state)
                refreshRequired = false
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
                catch (_: Exception) {
                    refreshRequired = true
                    state.value = state.value.copy(error = "$reason Не удалось обновить вопрос. Повтори загрузку.",
                        practiceRetryRequired = true)
                }
            }
        }
    }

    private suspend fun refreshPracticeState() {
        val current = checkNotNull(session.read()) { "The real save is unavailable" }
        saved = current
        render(current)
        refreshRequired = false
        state.value = state.value.copy(practiceRetryRequired = false)
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
        refreshRequired = pendingPracticeRequest == null
        state.value = state.value.copy(error = "Не удалось выполнить действие. Попробуй ещё раз.",
            practiceRetryRequired = pendingPracticeRequest != null || refreshRequired)
    }
}
