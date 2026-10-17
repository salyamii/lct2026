package ru.nksk.lctapp.feature.tasks.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.nksk.lctapp.domain.minigame.DiscountQuestion
import ru.nksk.lctapp.domain.minigame.DiscountState

data class DiscountUiState(val game: DiscountState)

sealed interface DiscountAction {
    data class Answer(val believedReal: Boolean, val questionIndex: Int? = null) : DiscountAction
    data class Next(val questionIndex: Int) : DiscountAction
    data object Restart : DiscountAction
}

@HiltViewModel
class DiscountViewModel @Inject constructor(private val savedState: SavedStateHandle) : ViewModel() {
    private val mutableUiState = MutableStateFlow(DiscountUiState(restore()))
    val uiState = mutableUiState.asStateFlow()

    fun onAction(action: DiscountAction) {
        when (action) {
            is DiscountAction.Answer -> {
                val before = uiState.value.game
                if (action.questionIndex != null && action.questionIndex != before.current) return
                val after = before.answer(action.believedReal)
                if (before != after) publish(after)
            }
            is DiscountAction.Next -> {
                val before = uiState.value.game
                if (action.questionIndex == before.current) publish(before.next())
            }
            DiscountAction.Restart -> publish(DiscountState.create())
        }
    }

    private fun publish(game: DiscountState) {
        savedState[ITEMS_KEY] = ArrayList(game.questions.map { it.item })
        savedState[PRICES_KEY] = game.questions.flatMap { listOf(it.stickerOld, it.priceNow, it.fairPrice) }.toIntArray()
        savedState[CURRENT_KEY] = game.current
        savedState[CORRECT_KEY] = game.correctAnswers
        savedState[LAST_KEY] = game.lastCorrect
        mutableUiState.value = DiscountUiState(game)
    }

    private fun restore(): DiscountState {
        val items = savedState.get<ArrayList<String>>(ITEMS_KEY)
        val prices = savedState.get<IntArray>(PRICES_KEY)
        if (items == null || prices == null || prices.size != items.size * 3) return DiscountState.create()
        return DiscountState(
            questions = items.mapIndexed { index, item ->
                DiscountQuestion(item, prices[index * 3], prices[index * 3 + 1], prices[index * 3 + 2])
            },
            current = savedState[CURRENT_KEY] ?: 0,
            correctAnswers = savedState[CORRECT_KEY] ?: 0,
            lastCorrect = savedState[LAST_KEY],
        )
    }

    private companion object {
        const val ITEMS_KEY = "discount_items"
        const val PRICES_KEY = "discount_prices"
        const val CURRENT_KEY = "discount_current"
        const val CORRECT_KEY = "discount_correct"
        const val LAST_KEY = "discount_last_correct"
    }
}
