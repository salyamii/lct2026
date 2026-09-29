package ru.nksk.lctapp.domain.minigame

import kotlin.random.Random

/**
 * Чистое состояние игры «Погаси фонари»: сетка [SIZE]×[SIZE], нажатие переключает
 * сам фонарь и четырёх соседей. Цель - погасить все фонари. Все переходы
 * возвращают новое состояние; раскладки фиксированы и всегда решаемы.
 */
data class LightsState(
    val grid: List<Boolean>,
    val moves: Int = 0,
) {
    val won: Boolean get() = grid.none { it }

    /** Нажатие на фонарь по индексу строки-мажорной сетки. */
    fun tap(index: Int): LightsState {
        if (won || index !in grid.indices) return this
        val row = index / SIZE
        val col = index % SIZE
        val toggled = grid.toBooleanArray()
        NEIGHBOR_OFFSETS.forEach { (dr, dc) ->
            val r = row + dr
            val c = col + dc
            if (r in 0 until SIZE && c in 0 until SIZE) {
                val neighbor = r * SIZE + c
                toggled[neighbor] = !toggled[neighbor]
            }
        }
        return copy(grid = toggled.toList(), moves = moves + 1)
    }

    companion object {
        const val SIZE = 5

        /** Сам фонарь и сосед по четырём сторонам. */
        val NEIGHBOR_OFFSETS = listOf(0 to 0, -1 to 0, 1 to 0, 0 to -1, 0 to 1)

        /** Нажатия, которыми собраны стартовые раскладки; каждая партия гарантированно решаема. */
        private val PUZZLE_PRESS_SETS: List<List<Int>> = listOf(
            listOf(0, 4, 12, 20, 24),
            listOf(0, 1, 2, 3, 4),
            listOf(0, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24),
        )

        /** Переключает фонарь и его крест без учёта счёта ходов. */
        private fun toggle(grid: List<Boolean>, index: Int): List<Boolean> {
            val row = index / SIZE
            val col = index % SIZE
            val toggled = grid.toBooleanArray()
            NEIGHBOR_OFFSETS.forEach { (dr, dc) ->
                val r = row + dr
                val c = col + dc
                if (r in 0 until SIZE && c in 0 until SIZE) toggled[r * SIZE + c] = !toggled[r * SIZE + c]
            }
            return toggled.toList()
        }

        val PUZZLES: List<List<Boolean>> = PUZZLE_PRESS_SETS.map { presses ->
            presses.fold(List(SIZE * SIZE) { false }) { grid, cell -> toggle(grid, cell) }
        }

        /** Создаёт партию на выбранной раскладке; по умолчанию первая. */
        fun create(puzzle: Int = 0): LightsState {
            require(puzzle in PUZZLES.indices) { "Unknown lights puzzle" }
            return LightsState(grid = PUZZLES[puzzle])
        }

        /** Новая партия на случайно выбранной раскладке. */
        fun createRandom(random: Random = Random.Default): LightsState =
            create(puzzle = random.nextInt(PUZZLES.size))

        /** Демонстрационная награда за собранную партию вне дел. */
        const val REWARD = 10
    }
}
