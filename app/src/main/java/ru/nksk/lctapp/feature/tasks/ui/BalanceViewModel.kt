package ru.nksk.lctapp.feature.tasks.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.nksk.lctapp.domain.minigame.BalanceExpense
import ru.nksk.lctapp.domain.minigame.BalanceState

data class BalanceUiState(val game: BalanceState)

sealed interface BalanceAction {
    data class Toggle(val expenseIndex: Int) : BalanceAction
    data object Confirm : BalanceAction
    data object Next : BalanceAction
    data object Restart : BalanceAction
}

@HiltViewModel
class BalanceViewModel @Inject constructor(private val savedState: SavedStateHandle) : ViewModel() {
    private val mutableUiState = MutableStateFlow(BalanceUiState(restore()))
    val uiState = mutableUiState.asStateFlow()

    fun onAction(action: BalanceAction) {
        when (action) {
            is BalanceAction.Toggle -> {
                val game = uiState.value.game
                if (game.lastCorrect != null || game.finished ||
                    action.expenseIndex !in game.weekExpenses.indices) return
                val removed = if (action.expenseIndex in game.removed) game.removed - action.expenseIndex
                    else game.removed + action.expenseIndex
                publish(game.copy(removed = removed))
            }
            BalanceAction.Confirm -> publish(uiState.value.game.confirm())
            BalanceAction.Next -> publish(uiState.value.game.next())
            BalanceAction.Restart -> publish(BalanceState.create())
        }
    }

    private fun publish(game: BalanceState) {
        savedState[INCOME_KEY] = game.income.toIntArray()
        savedState[NAMES_KEY] = ArrayList(game.expenses.flatten().map { it.name })
        savedState[AMOUNTS_KEY] = game.expenses.flatten().map { it.amount }.toIntArray()
        savedState[ESSENTIAL_KEY] = game.expenses.flatten().map { it.essential }.toBooleanArray()
        savedState[SIZES_KEY] = game.expenses.map { it.size }.toIntArray()
        savedState[CURRENT_KEY] = game.current
        savedState[REMOVED_KEY] = game.removed.toIntArray()
        savedState[CORRECT_KEY] = game.correctRounds
        savedState[LAST_KEY] = game.lastCorrect
        mutableUiState.value = BalanceUiState(game)
    }

    private fun restore(): BalanceState {
        val income = savedState.get<IntArray>(INCOME_KEY)
        val names = savedState.get<ArrayList<String>>(NAMES_KEY)
        val amounts = savedState.get<IntArray>(AMOUNTS_KEY)
        val essential = savedState.get<BooleanArray>(ESSENTIAL_KEY)
        val sizes = savedState.get<IntArray>(SIZES_KEY)
        if (income == null || names == null || amounts == null || essential == null || sizes == null ||
            names.size != amounts.size || amounts.size != essential.size ||
            sizes.sum() != names.size || sizes.size != income.size) return BalanceState.create()
        val expenses = mutableMapOf<Int, MutableList<BalanceExpense>>()
        var cursor = 0
        sizes.forEachIndexed { week, size ->
            expenses[week] = (cursor until cursor + size).map { index ->
                BalanceExpense(names[index], amounts[index], essential[index])
            }.toMutableList()
            cursor += size
        }
        return BalanceState(
            income = income.toList(),
            expenses = income.indices.map { checkNotNull(expenses[it]) },
            current = savedState[CURRENT_KEY] ?: 0,
            removed = (savedState.get<IntArray>(REMOVED_KEY) ?: IntArray(0)).toSet(),
            correctRounds = savedState[CORRECT_KEY] ?: 0,
            lastCorrect = savedState[LAST_KEY],
        )
    }

    private companion object {
        const val INCOME_KEY = "balance_income"
        const val NAMES_KEY = "balance_names"
        const val AMOUNTS_KEY = "balance_amounts"
        const val ESSENTIAL_KEY = "balance_essential"
        const val SIZES_KEY = "balance_sizes"
        const val CURRENT_KEY = "balance_current"
        const val REMOVED_KEY = "balance_removed"
        const val CORRECT_KEY = "balance_correct"
        const val LAST_KEY = "balance_last_correct"
    }
}
