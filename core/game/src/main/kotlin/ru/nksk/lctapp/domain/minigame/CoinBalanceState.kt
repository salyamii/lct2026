package ru.nksk.lctapp.domain.minigame

import kotlinx.serialization.Serializable

@Serializable
data class PracticeExpense(val item: PracticeItem, val cost: Int, val required: Boolean) {
    init { require(cost > 0) }
}

@Serializable
data class CoinBalanceRound(val income: Int, val expenses: List<PracticeExpense>) {
    init {
        require(income > 0 && expenses.isNotEmpty())
        require(expenses.map { it.item }.distinct().size == expenses.size)
        require(expenses.filter { it.required }.sumOf { it.cost.toLong() } <= income)
    }
}

enum class BalanceVerdict { BALANCED, OVERSPENT, MISSING_REQUIRED }

sealed interface CoinBalanceAction {
    data object Start : CoinBalanceAction
    data class Toggle(val item: PracticeItem, val round: Int) : CoinBalanceAction
    data class Check(val round: Int) : CoinBalanceAction
    data class Edit(val round: Int) : CoinBalanceAction
    data class Next(val round: Int) : CoinBalanceAction
    data object Restart : CoinBalanceAction
}

@Serializable
data class CoinBalanceState(
    val rounds: List<CoinBalanceRound>,
    val current: Int = 0,
    val started: Boolean = false,
    val removed: Set<PracticeItem> = emptySet(),
    val verdict: BalanceVerdict? = null,
    val madeMistake: Boolean = false,
    val correctAnswers: Int = 0,
) {
    init {
        require(rounds.isNotEmpty() && current in 0..rounds.size)
        val currentCorrect = verdict == BalanceVerdict.BALANCED && !madeMistake
        require(correctAnswers in (if (currentCorrect) 1 else 0)..(current + if (currentCorrect) 1 else 0))
        require(verdict == null || started)
        require(verdict !in setOf(BalanceVerdict.OVERSPENT, BalanceVerdict.MISSING_REQUIRED) || madeMistake)
        require(current < rounds.size || (started && verdict == null && removed.isEmpty() && !madeMistake))
        require(removed.all { item -> round.expenses.any { it.item == item } })
    }
    val finished: Boolean get() = current == rounds.size
    val round: CoinBalanceRound get() = rounds[minOf(current, rounds.lastIndex)]
    val spent: Long get() = round.expenses.filter { it.item !in removed }.sumOf { it.cost.toLong() }
    val remainder: Long get() = round.income - spent
    val missingRequired: List<PracticeItem> get() = round.expenses
        .filter { it.required && it.item in removed }.map { it.item }

    fun reduce(action: CoinBalanceAction): CoinBalanceState {
        if (action == CoinBalanceAction.Restart) return CoinBalanceState(rounds)
        if (finished) return this
        return when (action) {
            CoinBalanceAction.Start -> copy(started = true)
            is CoinBalanceAction.Toggle -> if (!started || verdict != null || action.round != current ||
                round.expenses.none { it.item == action.item }) this else copy(
                removed = if (action.item in removed) removed - action.item else removed + action.item)
            is CoinBalanceAction.Check -> if (!started || verdict != null || action.round != current) this else {
                val result = when {
                    missingRequired.isNotEmpty() -> BalanceVerdict.MISSING_REQUIRED
                    spent > round.income -> BalanceVerdict.OVERSPENT
                    else -> BalanceVerdict.BALANCED
                }
                copy(verdict = result, madeMistake = madeMistake || result != BalanceVerdict.BALANCED,
                    correctAnswers = correctAnswers + if (result == BalanceVerdict.BALANCED && !madeMistake) 1 else 0)
            }
            is CoinBalanceAction.Edit -> if (action.round != current || verdict == null ||
                verdict == BalanceVerdict.BALANCED) this else copy(verdict = null)
            is CoinBalanceAction.Next -> if (action.round != current || verdict != BalanceVerdict.BALANCED) this
                else copy(current = current + 1, removed = emptySet(), verdict = null, madeMistake = false)
            CoinBalanceAction.Restart -> this
        }
    }

    companion object {
        fun create(): CoinBalanceState {
            val expenses = listOf(
                PracticeExpense(PracticeItem.MEDICINE, 5, true),
                PracticeExpense(PracticeItem.BUN, 6, false),
                PracticeExpense(PracticeItem.TOY_BOAT, 5, false),
                PracticeExpense(PracticeItem.RINGS, 4, false),
                PracticeExpense(PracticeItem.BACKPACK_REPAIR, 7, true),
                PracticeExpense(PracticeItem.BREAD, 5, true),
                PracticeExpense(PracticeItem.CANDY, 5, false),
            )
            return CoinBalanceState(listOf(35, 30, 25).map { CoinBalanceRound(it, expenses) })
        }
    }
}
