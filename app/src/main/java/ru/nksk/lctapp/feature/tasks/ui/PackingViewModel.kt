package ru.nksk.lctapp.feature.tasks.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.nksk.lctapp.domain.minigame.PackingState

data class PackingUiState(val game: PackingState)

sealed interface PackingAction {
    data class Remove(val itemIndex: Int) : PackingAction
    data class Next(val roundIndex: Int) : PackingAction
    data object Restart : PackingAction
}

@HiltViewModel
class PackingViewModel @Inject constructor(private val savedState: SavedStateHandle) : ViewModel() {
    private val mutableUiState = MutableStateFlow(PackingUiState(restore()))
    val uiState = mutableUiState.asStateFlow()

    fun onAction(action: PackingAction) {
        when (action) {
            is PackingAction.Remove -> {
                val before = uiState.value.game
                if (before.lastCorrect != null || before.finished) return
                val after = before.pick(action.itemIndex)
                if (before != after) publish(after)
            }
            is PackingAction.Next -> {
                val before = uiState.value.game
                if (action.roundIndex == before.current) publish(before.next())
            }
            PackingAction.Restart -> publish(PackingState.create())
        }
    }

    private fun publish(game: PackingState) {
        savedState[GOALS_KEY] = ArrayList(game.rounds.map { it.goal })
        savedState[ITEMS_KEY] = ArrayList(game.rounds.flatMap { it.items })
        savedState[CORRECT_INDEX_KEY] = game.rounds.map { it.oddIndex }.toIntArray()
        savedState[CURRENT_KEY] = game.current
        savedState[CORRECT_KEY] = game.correctRounds
        savedState[LAST_KEY] = game.lastCorrect
        mutableUiState.value = PackingUiState(game)
    }

    private fun restore(): PackingState {
        val goals = savedState.get<ArrayList<String>>(GOALS_KEY)
        val items = savedState.get<ArrayList<String>>(ITEMS_KEY)
        val correct = savedState.get<IntArray>(CORRECT_INDEX_KEY)
        if (goals == null || items == null || correct == null || correct.size != goals.size ||
            items.size != goals.size * PackingState.ITEM_COUNT) return PackingState.create()
        return PackingState(
            rounds = goals.mapIndexed { round, goal ->
                val roundItems = items.drop(round * PackingState.ITEM_COUNT).take(PackingState.ITEM_COUNT)
                ru.nksk.lctapp.domain.minigame.PackingRound(goal, roundItems, correct[round])
            },
            current = savedState[CURRENT_KEY] ?: 0,
            correctRounds = savedState[CORRECT_KEY] ?: 0,
            lastCorrect = savedState[LAST_KEY],
        )
    }

    private companion object {
        const val GOALS_KEY = "packing_goals"
        const val ITEMS_KEY = "packing_items"
        const val CORRECT_INDEX_KEY = "packing_correct_index"
        const val CURRENT_KEY = "packing_current"
        const val CORRECT_KEY = "packing_correct"
        const val LAST_KEY = "packing_last_correct"
    }
}
