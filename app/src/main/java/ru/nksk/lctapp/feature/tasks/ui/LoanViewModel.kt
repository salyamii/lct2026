package ru.nksk.lctapp.feature.tasks.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.nksk.lctapp.domain.minigame.LoanState

data class LoanUiState(val game: LoanState)

sealed interface LoanAction {
    data class Choose(val pickedFirst: Boolean, val scenarioIndex: Int? = null) : LoanAction
    data class Next(val scenarioIndex: Int) : LoanAction
    data object Restart : LoanAction
}

@HiltViewModel
class LoanViewModel @Inject constructor(private val savedState: SavedStateHandle) : ViewModel() {
    private val mutableUiState = MutableStateFlow(LoanUiState(restore()))
    val uiState = mutableUiState.asStateFlow()

    fun onAction(action: LoanAction) {
        when (action) {
            is LoanAction.Choose -> {
                val before = uiState.value.game
                if (action.scenarioIndex != null && action.scenarioIndex != before.current) return
                val after = before.answer(action.pickedFirst)
                if (before != after) publish(after)
            }
            is LoanAction.Next -> {
                val before = uiState.value.game
                if (action.scenarioIndex == before.current) publish(before.next())
            }
            LoanAction.Restart -> publish(LoanState.create())
        }
    }

    private fun publish(game: LoanState) {
        val fixed = LoanState.fixedScenarios()
        savedState[ORDER_KEY] = game.scenarios.map { fixed.indexOf(it) }.toIntArray()
        savedState[CURRENT_KEY] = game.current
        savedState[CORRECT_KEY] = game.correctAnswers
        savedState[LAST_KEY] = game.lastCorrect
        mutableUiState.value = LoanUiState(game)
    }

    private fun restore(): LoanState {
        val fixed = LoanState.fixedScenarios()
        val order = savedState.get<IntArray>(ORDER_KEY)
            ?.takeIf { it.size == LoanState.SCENARIO_COUNT && it.all { index -> index in fixed.indices } }
            ?: return LoanState.create()
        return LoanState(
            scenarios = order.map { fixed[it] },
            current = savedState[CURRENT_KEY] ?: 0,
            correctAnswers = savedState[CORRECT_KEY] ?: 0,
            lastCorrect = savedState[LAST_KEY],
        )
    }

    private companion object {
        const val ORDER_KEY = "loan_order"
        const val CURRENT_KEY = "loan_current"
        const val CORRECT_KEY = "loan_correct"
        const val LAST_KEY = "loan_last_correct"
    }
}
