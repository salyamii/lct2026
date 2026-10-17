package ru.nksk.lctapp.domain.minigame

import kotlin.random.Random

/** Трата на чаше расходов: [essential] - обязательная, её отказывать нельзя. */
data class BalanceExpense(val name: String, val amount: Int, val essential: Boolean) {
    init {
        require(amount > 0 && name.isNotBlank())
    }
}

/**
 * Чистое состояние «Монетного баланса»: [ROUNDS] недель. Игрок снимает с чаши расходов
 * необязательные траты, чтобы расходы не перевешивали доходы. Раунд засчитан, когда после
 * подтверждения расходы не больше доходов и ни одна обязательная трата не снята.
 */
data class BalanceState(
    val income: List<Int>,
    val expenses: List<List<BalanceExpense>>,
    val current: Int = 0,
    val removed: Set<Int> = emptySet(),
    val lastCorrect: Boolean? = null,
    val correctRounds: Int = 0,
) {
    init {
        require(income.size == expenses.size)
        require(correctRounds in 0..ROUNDS)
        require(expenses.all { week -> week.isNotEmpty() && week.any { it.essential } })
        // Задача решаема: обязательные траты всегда влезают в доход.
        require(income.zip(expenses).all { (money, week) -> week.filter { it.essential }.sumOf { it.amount } <= money })
    }

    val weekExpenses: List<BalanceExpense> get() = expenses[current]
    val weekIncome: Int get() = income[current]
    val finished: Boolean get() = current >= ROUNDS

    /** Расходы после снятия игроком части необязательных трат. */
    val remainingExpenses: Int
        get() = weekExpenses.indices.filter { it !in removed }.sumOf { weekExpenses[it].amount }

    val balanced: Boolean get() = remainingExpenses <= weekIncome

    /** Подтверждает подбор трат. Фиксирует результат, ждёт [next]. */
    fun confirm(): BalanceState {
        if (finished || lastCorrect != null) return this
        val droppedEssential = weekExpenses.indices.any { it in removed && weekExpenses[it].essential }
        val correct = balanced && !droppedEssential
        return copy(correctRounds = if (correct) correctRounds + 1 else correctRounds, lastCorrect = correct)
    }

    fun next(): BalanceState {
        if (finished || lastCorrect == null) return this
        return copy(current = current + 1, removed = emptySet(), lastCorrect = null)
    }

    companion object {
        const val ROUNDS = 3
        private val ESSENTIALS = listOf("хлеб и сыр", "ремонт сапог", "лекарства", "дрова")
        private val OPTIONAL = listOf("сладости", "игрушка", "свечи для ванны", "колода карт", "ленточки")

        fun create(random: Random = Random.Default): BalanceState {
            val incomes = List(ROUNDS) { random.nextInt(4, 8) * 5 }                 // 20..35 монет
            val weeks = incomes.map { money ->
                var essentials = ESSENTIALS.shuffled(random).take(random.nextInt(1, 3))
                    .map { BalanceExpense(it, random.nextInt(5, 10), essential = true) }
                // Обязательные траты гарантированно влезают в доход: лишнее срезает самая крупная.
                while (essentials.sumOf { it.amount } > money) {
                    val heaviest = essentials.maxBy { it.amount }
                    essentials = essentials.map { if (it === heaviest) it.copy(amount = it.amount - 1) else it }
                }
                val extras = OPTIONAL.shuffled(random).toMutableList()
                val week = (essentials + extras.take(random.nextInt(2, 5)).map {
                    BalanceExpense(it, random.nextInt(4, 9), essential = false)
                }).toMutableList()
                // Перегруз гарантируем: без отказов расходы всегда больше дохода.
                var extrasIndex = 0
                while (week.sumOf { it.amount } <= money) {
                    week += BalanceExpense(extras[extrasIndex % extras.size], random.nextInt(4, 9), essential = false)
                    extrasIndex += 1
                }
                week
            }
            return BalanceState(income = incomes, expenses = weeks)
        }
    }
}
