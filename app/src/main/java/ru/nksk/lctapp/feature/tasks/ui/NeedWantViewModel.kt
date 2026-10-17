package ru.nksk.lctapp.feature.tasks.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.nksk.lctapp.domain.minigame.NeedWantItem
import ru.nksk.lctapp.domain.minigame.NeedWantState

data class NeedWantUiState(val game: NeedWantState)

sealed interface NeedWantAction {
    data class Classify(val saidNeed: Boolean, val itemIndex: Int? = null) : NeedWantAction
    data class Next(val itemIndex: Int) : NeedWantAction
    data object Restart : NeedWantAction
}

@HiltViewModel
class NeedWantViewModel @Inject constructor(private val savedState: SavedStateHandle) : ViewModel() {
    private val mutableUiState = MutableStateFlow(NeedWantUiState(restore()))
    val uiState = mutableUiState.asStateFlow()

    fun onAction(action: NeedWantAction) {
        when (action) {
            is NeedWantAction.Classify -> {
                val before = uiState.value.game
                if (action.itemIndex != null && action.itemIndex != before.current) return
                val after = before.answer(action.saidNeed)
                if (before != after) publish(after)
            }
            is NeedWantAction.Next -> {
                val before = uiState.value.game
                if (action.itemIndex == before.current) publish(before.next())
            }
            NeedWantAction.Restart -> publish(NeedWantState.create())
        }
    }

    private fun publish(game: NeedWantState) {
        savedState[NAMES_KEY] = ArrayList(game.items.map { it.name })
        savedState[NEEDS_KEY] = game.items.map { it.need }.toBooleanArray()
        savedState[CURRENT_KEY] = game.current
        savedState[CORRECT_KEY] = game.correctAnswers
        savedState[LAST_KEY] = game.lastCorrect
        mutableUiState.value = NeedWantUiState(game)
    }

    private fun restore(): NeedWantState {
        val names = savedState.get<ArrayList<String>>(NAMES_KEY)
        val needs = savedState.get<BooleanArray>(NEEDS_KEY)
            ?: return NeedWantState.create()
        val storedNames = names ?: return NeedWantState.create()
        return NeedWantState(
            items = storedNames.zip(needs.toList()) { name, need -> NeedWantItem(name, need) },
            current = savedState[CURRENT_KEY] ?: 0,
            correctAnswers = savedState[CORRECT_KEY] ?: 0,
            lastCorrect = savedState[LAST_KEY],
        )
    }

    private companion object {
        const val NAMES_KEY = "needwant_names"
        const val NEEDS_KEY = "needwant_needs"
        const val CURRENT_KEY = "needwant_current"
        const val CORRECT_KEY = "needwant_correct"
        const val LAST_KEY = "needwant_last_correct"
    }
}
