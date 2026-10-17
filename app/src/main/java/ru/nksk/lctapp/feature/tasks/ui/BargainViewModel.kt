package ru.nksk.lctapp.feature.tasks.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.nksk.lctapp.domain.minigame.BargainOffer
import ru.nksk.lctapp.domain.minigame.BargainQuestion
import ru.nksk.lctapp.domain.minigame.BargainState

data class BargainUiState(val game: BargainState) {
    val leftIsAnswer: Boolean get() = !game.finished && game.lastCorrect != null && game.question.leftIsBetter
    val rightIsAnswer: Boolean get() = !game.finished && game.lastCorrect != null && !game.question.leftIsBetter
}

sealed interface BargainAction {
    data class Answer(val pickedLeft: Boolean, val questionIndex: Int? = null) : BargainAction
    data class Next(val questionIndex: Int) : BargainAction
    data object Restart : BargainAction
}

@HiltViewModel
class BargainViewModel @Inject constructor(private val savedState: SavedStateHandle) : ViewModel() {
    private val mutableUiState = MutableStateFlow(BargainUiState(restore()))
    val uiState = mutableUiState.asStateFlow()

    fun onAction(action: BargainAction) {
        when (action) {
            is BargainAction.Answer -> {
                val before = uiState.value.game
                if (action.questionIndex != null && action.questionIndex != before.current) return
                val after = before.answer(action.pickedLeft)
                if (before != after) publish(after)
            }
            is BargainAction.Next -> {
                val before = uiState.value.game
                if (action.questionIndex == before.current) publish(before.next())
            }
            BargainAction.Restart -> publish(BargainState.create())
        }
    }

    private fun publish(game: BargainState) {
        savedState[QUESTIONS_KEY] = game.questions.flatMap {
            listOf(it.left.count, it.left.totalPrice, it.right.count, it.right.totalPrice)
        }.toIntArray()
        savedState[CURRENT_KEY] = game.current
        savedState[CORRECT_KEY] = game.correctAnswers
        savedState[LAST_KEY] = game.lastCorrect
        mutableUiState.value = BargainUiState(game)
    }

    private fun restore(): BargainState {
        val flat = savedState.get<IntArray>(QUESTIONS_KEY)
            ?: return BargainState.create()
        return BargainState(
            questions = flat.toList().chunked(4).map {
                BargainQuestion(BargainOffer(it[0], it[1]), BargainOffer(it[2], it[3]))
            },
            current = savedState[CURRENT_KEY] ?: 0,
            correctAnswers = savedState[CORRECT_KEY] ?: 0,
            lastCorrect = savedState[LAST_KEY],
        )
    }

    private companion object {
        const val QUESTIONS_KEY = "bargain_questions"
        const val CURRENT_KEY = "bargain_current"
        const val CORRECT_KEY = "bargain_correct"
        const val LAST_KEY = "bargain_last_correct"
    }
}
