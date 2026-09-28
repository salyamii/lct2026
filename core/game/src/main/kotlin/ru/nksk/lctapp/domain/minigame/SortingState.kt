package ru.nksk.lctapp.domain.minigame

/**
 * Чистое состояние игры «Разложи припасы»: [TUBE_COUNT] банок вместимостью
 * [CAPACITY], в банке припасы лежат столбиком (индекс 0 — дно). Перекладывать
 * можно только верхний припас, и только в пустую банку или на такой же верхний.
 * Готово, когда каждая банка пуста или содержит припасы одного вида.
 */
data class SortingState(
    val tubes: List<List<Int>>,
    val selected: Int? = null,
    val moves: Int = 0,
) {
    /** Готово: в каждой банке припасы одного вида или банка пуста. */
    val won: Boolean get() = tubes.isNotEmpty() && tubes.all { tube -> tube.isEmpty() || tube.distinct().size == 1 }

    private fun canPour(from: Int, to: Int): Boolean {
        if (from == to) return false
        val source = tubes[from]
        if (source.isEmpty() || tubes[to].size >= CAPACITY) return false
        val top = source.last()
        val target = tubes[to]
        return target.isEmpty() || target.last() == top
    }

    /**
     * Нажатие на банку. Без выделения выделяет непустую банку. Повторное
     * нажатие снимает выделение. Если переложить нельзя, выделение переходит
     * на нажатую банку.
     */
    fun tap(index: Int): SortingState {
        if (won || index !in tubes.indices) return this
        val active = selected
        if (active == null) {
            return if (tubes[index].isEmpty()) this else copy(selected = index)
        }
        if (index == active) return copy(selected = null)
        if (canPour(active, index)) {
            val moving = tubes.mapIndexed { i, tube ->
                if (i == active) tube.dropLast(1) else if (i == index) tube + tubeOf(active) else tube
            }
            return copy(tubes = moving, selected = null, moves = moves + 1)
        }
        return if (tubes[index].isEmpty()) this else copy(selected = index)
    }

    private fun tubeOf(index: Int): Int = tubes[index].last()

    companion object {
        const val TUBE_COUNT = 4
        const val CAPACITY = 4

        /** Всего припасов в стартовой раскладке: три полные банки по четыре. */
        const val BALL_COUNT = 12

        private val START = listOf(
            listOf(0, 1, 2, 3),
            listOf(3, 2, 1, 0),
            listOf(1, 0, 3, 2),
            emptyList(),
        )

        /** Стартовая раскладка: три полных банки и одна пустая. */
        fun create(): SortingState {
            require(START.size == TUBE_COUNT && START.all { it.size <= CAPACITY }) { "Invalid sorting start" }
            return SortingState(tubes = START)
        }
    }
}
