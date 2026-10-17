package ru.nksk.lctapp.domain.minigame

import kotlin.random.Random

/** Предмет набора для «Лишней покупки»; [odd] - предмет, не нужный для цели поездки. */
data class PackingRound(val goal: String, val items: List<String>, val oddIndex: Int) {
    init {
        require(items.size == PackingState.ITEM_COUNT && oddIndex in items.indices && goal.isNotBlank())
        require(items.distinct().size == PackingState.ITEM_COUNT)
    }
}

/**
 * Чистое состояние «Лишней покупки»: [ROUNDS] набора по четыре предмета. Игрок убирает
 * один предмет - тот, что не понадобится для заявленной цели. Финал после последнего набора.
 */
data class PackingState(
    val rounds: List<PackingRound>,
    val current: Int = 0,
    val correctRounds: Int = 0,
    val lastCorrect: Boolean? = null,
) {
    init {
        require(correctRounds in 0..rounds.size)
    }

    val round: PackingRound get() = rounds[current]
    val finished: Boolean get() = current >= rounds.size

    /** Убирает предмет по индексу. Фиксирует результат, ждёт [next]. */
    fun pick(index: Int): PackingState {
        if (finished || lastCorrect != null) return this
        val correct = index == round.oddIndex
        return copy(correctRounds = if (correct) correctRounds + 1 else correctRounds, lastCorrect = correct)
    }

    fun next(): PackingState {
        if (finished || lastCorrect == null) return this
        return copy(current = current + 1, lastCorrect = null)
    }

    companion object {
        const val ROUNDS = 3
        const val ITEM_COUNT = 4

        /** Фиксированные наборы мира игры; случайным остаётся порядок предметов и наборов. */
        private val ROUNDS_SOURCE = listOf(
            PackingRound(
                goal = "Ночной поход к обсерватории",
                items = listOf("фонарь", "тёплый плащ", "термос", "бумажный змей"),
                oddIndex = 3,
            ),
            PackingRound(
                goal = "Рыбалка с Бобром на рассвете",
                items = listOf("удочка", "ведро", "непромокаемый плащ", "музыкальная шкатулка"),
                oddIndex = 3,
            ),
            PackingRound(
                goal = "Починка верстака с Тико",
                items = listOf("молоток", "гвозди", "доска", "свечи для торта"),
                oddIndex = 3,
            ),
        )

        fun create(random: Random = Random.Default): PackingState = PackingState(
            rounds = ROUNDS_SOURCE.map { round ->
                val order = round.items.indices.shuffled(random)
                round.copy(items = order.map(round.items::get), oddIndex = order.indexOf(round.oddIndex))
            }.shuffled(random),
        )
    }
}
