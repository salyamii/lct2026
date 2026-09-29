package ru.nksk.lctapp.feature.tasks.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.nksk.lctapp.domain.minigame.PriceQuizState
import ru.nksk.lctapp.domain.minigame.QuizQuestion

data class PriceQuizUiState(val game: PriceQuizState) {
    val leftIsAnswer: Boolean get() = !game.finished && game.lastCorrect != null && game.question.leftIsBigger
    val rightIsAnswer: Boolean get() = !game.finished && game.lastCorrect != null && !game.question.leftIsBigger
}

sealed interface PriceQuizAction {
    data class Answer(val pickedLeft: Boolean, val questionIndex: Int? = null) : PriceQuizAction
    data class Next(val questionIndex: Int) : PriceQuizAction
    data object Restart : PriceQuizAction
}

@HiltViewModel
class PriceQuizViewModel @Inject constructor(private val savedState: SavedStateHandle) : ViewModel() {
    private val mutableUiState = MutableStateFlow(PriceQuizUiState(restore()))
    val uiState = mutableUiState.asStateFlow()
    private var seriesId: String = savedState.get<String>("comparison_series_id")
        ?: UUID.randomUUID().toString().also { savedState["comparison_series_id"] = it }
    private var selectedAnswers: IntArray = savedState.get<IntArray>("comparison_answers")
        ?.takeIf { it.size == uiState.value.game.questions.size }
        ?: IntArray(uiState.value.game.questions.size) { -1 }
    private var presentedQuestions: BooleanArray = savedState.get<BooleanArray>("comparison_presented")
        ?.takeIf { it.size == uiState.value.game.questions.size }
        ?: BooleanArray(uiState.value.game.questions.size)

    init {
        publish(uiState.value.game)
    }

    fun onAction(action: PriceQuizAction) {
        when (action) {
            is PriceQuizAction.Answer -> {
                val before = uiState.value.game
                if (action.questionIndex != null && action.questionIndex != before.current) return
                val after = before.answer(action.pickedLeft)
                if (before != after) {
                    selectedAnswers[before.current] = if (action.pickedLeft) 1 else 0
                    saveEvidence()
                    publish(after)
                }
            }
            is PriceQuizAction.Next -> {
                val before = uiState.value.game
                if (action.questionIndex == before.current) publish(before.next())
            }
            PriceQuizAction.Restart -> {
                val fresh = PriceQuizState.create()
                seriesId = UUID.randomUUID().toString()
                selectedAnswers = IntArray(fresh.questions.size) { -1 }
                presentedQuestions = BooleanArray(fresh.questions.size)
                saveEvidence()
                publish(fresh)
            }
        }
    }

    /** Called by the resumed entry after this question is composed, never by a background observer. */
    internal fun questionPresented(index: Int = uiState.value.game.current) {
        val game = uiState.value.game
        if (index != game.current || game.finished || game.lastCorrect != null || presentedQuestions[game.current]) return
        presentedQuestions[game.current] = true
        saveEvidence()
    }

    internal fun comparisonEvidence(): PriceQuizEvidence {
        val game = uiState.value.game
        val complete = selectedAnswers.all { it != -1 }
        return PriceQuizEvidence(seriesId, game.questions.mapIndexedNotNull { index, question ->
            selectedAnswers[index].takeIf { it != -1 }?.let { selected ->
                PriceQuizAnswerEvidence(index, question.leftAmount, question.rightAmount, selected == 1,
                    presentedQuestions[index], complete && index == game.questions.lastIndex)
            }
        })
    }

    private fun saveEvidence() {
        savedState["comparison_series_id"] = seriesId
        savedState["comparison_answers"] = selectedAnswers.copyOf()
        savedState["comparison_presented"] = presentedQuestions.copyOf()
    }

    private fun publish(game: PriceQuizState) {
        savedState["questions"] = game.questions.flatMap { listOf(it.leftAmount, it.rightAmount) }.toIntArray()
        savedState["current"] = game.current
        savedState["correct"] = game.correctAnswers
        savedState["last_correct"] = game.lastCorrect
        mutableUiState.value = PriceQuizUiState(game)
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
