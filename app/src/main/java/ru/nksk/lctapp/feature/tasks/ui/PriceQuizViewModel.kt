package ru.nksk.lctapp.feature.tasks.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.nksk.lctapp.domain.game.GameRepository
import ru.nksk.lctapp.domain.minigame.MiniGameKind
import ru.nksk.lctapp.domain.minigame.PriceQuizState
import ru.nksk.lctapp.domain.minigame.QuizQuestion

data class PriceQuizUiState(val game: PriceQuizState, val session: MiniGameSessionUiState = MiniGameSessionUiState()) {
    val leftIsAnswer: Boolean get() = !game.finished && game.lastCorrect != null && game.question.leftIsBigger
    val rightIsAnswer: Boolean get() = !game.finished && game.lastCorrect != null && !game.question.leftIsBigger
}

sealed interface PriceQuizAction {
    data class Answer(val pickedLeft: Boolean) : PriceQuizAction
    data object Retry : PriceQuizAction
    data object Restart : PriceQuizAction
}

@HiltViewModel
class PriceQuizViewModel @Inject constructor(private val savedState: SavedStateHandle, repository: GameRepository) : ViewModel() {
    private val mutableUiState = MutableStateFlow(PriceQuizUiState(restore()))
    val uiState = mutableUiState.asStateFlow()
    private var feedbackJob: Job? = null
    private val session = MiniGameSession(MiniGameKind.PRICE_QUIZ, repository, savedState, viewModelScope) {
        mutableUiState.value = mutableUiState.value.copy(session = it)
    }

    init {
        session.observe()
        publish(uiState.value.game)
        advanceAfterFeedback()
    }

    fun onAction(action: PriceQuizAction) {
        when (action) {
            PriceQuizAction.Retry -> session.retry()
            is PriceQuizAction.Answer -> {
                if (!session.state.canPlay) return
                val before = uiState.value.game
                val after = before.answer(action.pickedLeft)
                if (before != after) {
                    publish(after)
                    advanceAfterFeedback()
                }
            }
            PriceQuizAction.Restart -> {
                if (!session.restart()) return
                feedbackJob?.cancel()
                publish(PriceQuizState.create())
            }
        }
    }

    private fun advanceAfterFeedback() {
        if (uiState.value.game.lastCorrect == null) return
        feedbackJob?.cancel()
        feedbackJob = viewModelScope.launch {
            delay(750)
            publish(uiState.value.game.next())
        }
    }

    private fun publish(game: PriceQuizState) {
        savedState["questions"] = game.questions.flatMap { listOf(it.leftAmount, it.rightAmount) }.toIntArray()
        savedState["current"] = game.current
        savedState["correct"] = game.correctAnswers
        savedState["last_correct"] = game.lastCorrect
        mutableUiState.value = PriceQuizUiState(game, session.state)
        if (game.finished) session.finish(success = true)
    }

    private fun restore(): PriceQuizState {
        val amounts = savedState.get<IntArray>("questions") ?: return PriceQuizState.create()
        return PriceQuizState(
            questions = amounts.toList().chunked(2).map { QuizQuestion(it[0], it[1]) },
            current = savedState["current"] ?: 0,
            correctAnswers = savedState["correct"] ?: 0,
            lastCorrect = savedState["last_correct"],
        )
    }
}
