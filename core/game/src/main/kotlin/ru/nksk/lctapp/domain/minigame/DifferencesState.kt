package ru.nksk.lctapp.domain.minigame

import kotlin.random.Random

/**
 * Чистое состояние игры «Сверка находок»: две одинаковые на вид полки
 * ([CELLS] предметов, id рисунков), на нижней спрятано [DIFF_COUNT] отличий.
 * Нажатие по отличию отмечает его; иное нажатие тратит попытку. Финал —
 * найдены все отличия. Все переходы возвращают новое состояние.
 */
data class DifferencesState(
    val top: List<Int>,
    val bottom: List<Int>,
    val found: Set<Int> = emptySet(),
    val taps: Int = 0,
) {
    /** Индексы ячеек, где полки различаются. */
    val differences: List<Int> get() = top.indices.filter { top[it] != bottom[it] }

    /** Финал: найдены все отличия. */
    val won: Boolean get() = differences.isNotEmpty() && found.containsAll(differences)

    /**
     * Нажатие по ячейке нижней полки. Отличие отмечается; повторное нажатие
     * и нажатия по обычным ячейкам тратят попытку без результата.
     */
    fun tap(cell: Int): DifferencesState {
        if (won || cell !in bottom.indices) return this
        val isDifference = top.getOrNull(cell) != bottom.getOrNull(cell)
        return copy(
            found = if (isDifference) found + cell else found,
            taps = taps + 1,
        )
    }

    companion object {
        const val CELLS = 16
        const val DIFF_COUNT = 5

        private data class Scene(val items: List<Int>, val replacements: Map<Int, Int>)

        /** Полка Смотрителя: пары находок разложены в две строки. */
        private val SCENES: List<Scene> = listOf(
            Scene(
                listOf(0, 1, 2, 3, 4, 5, 6, 7, 0, 1, 2, 3, 4, 5, 6, 7),
                mapOf(1 to 5, 6 to 2, 9 to 7, 12 to 0, 15 to 4),
            ),
            Scene(
                listOf(3, 5, 0, 7, 1, 4, 2, 6, 3, 5, 0, 7, 1, 4, 2, 6),
                mapOf(0 to 2, 5 to 1, 8 to 6, 11 to 3, 14 to 7),
            ),
            Scene(
                listOf(6, 2, 4, 0, 7, 1, 5, 3, 6, 2, 4, 0, 7, 1, 5, 3),
                mapOf(2 to 5, 4 to 6, 9 to 0, 13 to 5, 15 to 2),
            ),
        )

        private fun sceneBoard(scene: Scene): Pair<List<Int>, List<Int>> {
            val top = scene.items
            val bottom = top.toMutableList().also { copy ->
                scene.replacements.forEach { (cell, art) -> copy[cell] = art }
            }
            return top to bottom
        }

        /**
         * Готовит партию на выбранной сцене; нижняя полка отличается
         * ровно в [DIFF_COUNT] ячейках.
         */
        fun create(scene: Int = 0): DifferencesState {
            require(scene in SCENES.indices) { "Unknown differences scene" }
            val (top, bottom) = sceneBoard(SCENES[scene])
            require(top.size == CELLS && bottom.size == CELLS) { "Scene must fill every cell" }
            val board = DifferencesState(top = top, bottom = bottom)
            require(board.differences.size == DIFF_COUNT) { "Scene must hide exactly $DIFF_COUNT differences" }
            return board
        }

        /** Новая партия на случайно выбранной сцене. */
        fun createRandom(random: Random = Random.Default): DifferencesState =
            create(scene = random.nextInt(SCENES.size))
    }
}
