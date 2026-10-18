package ru.nksk.lctapp.domain.minigame

import kotlinx.serialization.Serializable

@Serializable
data class PracticeExpense(val item: PracticeItem, val cost: Int) {
    init { require(cost > 0) }
}

enum class BalanceScenario { PICNIC, NIGHT_WALK, WORKBENCH }
enum class BalanceNeed { FOOD, DRINK, ONE_GAME, LIGHT, WARMTH, BOARD, FASTENERS }

@Serializable
data class BalanceRequirement(val need: BalanceNeed, val options: Set<PracticeItem>,
    val minCount: Int = 1, val maxCount: Int = options.size) {
    init { require(options.isNotEmpty() && minCount in 1..maxCount && maxCount <= options.size) }
    fun isMet(selected: Set<PracticeItem>) = options.count { it in selected } in minCount..maxCount
}

@Serializable
data class CoinBalanceRound(val scenario: BalanceScenario, val income: Int,
    val expenses: List<PracticeExpense>, val requirements: List<BalanceRequirement>) {
    init {
        require(income > 0 && expenses.isNotEmpty())
        require(expenses.map { it.item }.distinct().size == expenses.size)
        require(requirements.isNotEmpty() && requirements.map { it.need }.distinct().size == requirements.size)
        val options = requirements.flatMap { it.options }
        require(options.distinct().size == options.size && options.all { item -> expenses.any { it.item == item } })
        require(requirements.sumOf { requirement -> expenses.filter { it.item in requirement.options }
            .sortedBy { it.cost }.take(requirement.minCount).sumOf { it.cost.toLong() } } <= income)
    }
}

enum class BalanceVerdict { BALANCED, OVERSPENT, INCOMPLETE_PLAN }

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
        require(verdict !in setOf(BalanceVerdict.OVERSPENT, BalanceVerdict.INCOMPLETE_PLAN) || madeMistake)
        require(current < rounds.size || (started && verdict == null && removed.isEmpty() && !madeMistake))
        require(removed.all { item -> round.expenses.any { it.item == item } })
    }
    val finished: Boolean get() = current == rounds.size
    val round: CoinBalanceRound get() = rounds[minOf(current, rounds.lastIndex)]
    val spent: Long get() = round.expenses.filter { it.item !in removed }.sumOf { it.cost.toLong() }
    val remainder: Long get() = round.income - spent
    val unmetNeeds: List<BalanceNeed> get() {
        val selected = round.expenses.filter { it.item !in removed }.map { it.item }.toSet()
        return round.requirements.filterNot { it.isMet(selected) }.map { it.need }
    }

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
                    unmetNeeds.isNotEmpty() -> BalanceVerdict.INCOMPLETE_PLAN
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
        fun create() = CoinBalanceState(listOf(
            CoinBalanceRound(BalanceScenario.PICNIC, 20, listOf(
                PracticeExpense(PracticeItem.BREAD, 6), PracticeExpense(PracticeItem.WATER, 4),
                PracticeExpense(PracticeItem.RINGS, 8), PracticeExpense(PracticeItem.TOY_BOAT, 12),
                PracticeExpense(PracticeItem.BUN, 5), PracticeExpense(PracticeItem.CANDY, 3),
            ), listOf(
                BalanceRequirement(BalanceNeed.FOOD, setOf(PracticeItem.BREAD, PracticeItem.BUN)),
                BalanceRequirement(BalanceNeed.DRINK, setOf(PracticeItem.WATER)),
                BalanceRequirement(BalanceNeed.ONE_GAME, setOf(PracticeItem.RINGS, PracticeItem.TOY_BOAT), maxCount = 1),
            )),
            CoinBalanceRound(BalanceScenario.NIGHT_WALK, 22, listOf(
                PracticeExpense(PracticeItem.LANTERN, 9), PracticeExpense(PracticeItem.BREAD, 5),
                PracticeExpense(PracticeItem.TOY_BOAT, 7), PracticeExpense(PracticeItem.SCARF, 6),
                PracticeExpense(PracticeItem.BUN, 4), PracticeExpense(PracticeItem.WATER, 4),
            ), listOf(
                BalanceRequirement(BalanceNeed.LIGHT, setOf(PracticeItem.LANTERN)),
                BalanceRequirement(BalanceNeed.WARMTH, setOf(PracticeItem.SCARF)),
                BalanceRequirement(BalanceNeed.FOOD, setOf(PracticeItem.BREAD, PracticeItem.BUN)),
            )),
            CoinBalanceRound(BalanceScenario.WORKBENCH, 15, listOf(
                PracticeExpense(PracticeItem.HAMMER, 7), PracticeExpense(PracticeItem.PLANK, 8),
                PracticeExpense(PracticeItem.CANDY, 3), PracticeExpense(PracticeItem.NAILS, 5),
                PracticeExpense(PracticeItem.CANDLES, 4),
            ), listOf(
                BalanceRequirement(BalanceNeed.BOARD, setOf(PracticeItem.PLANK)),
                BalanceRequirement(BalanceNeed.FASTENERS, setOf(PracticeItem.NAILS)),
            )),
        ))
    }
}
