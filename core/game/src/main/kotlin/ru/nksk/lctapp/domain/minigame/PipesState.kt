package ru.nksk.lctapp.domain.minigame

import kotlin.random.Random

/**
 * Пара концов одного цвета на поле «Свяжи концы».
 * [first] и [second] - индексы ячеек строки-мажорной сетки [PipesState.SIZE]×[PipesState.SIZE].
 */
data class PipeEndpoints(val color: Int, val first: Int, val second: Int)

/**
 * Чистое состояние игры «Свяжи концы»: на поле [SIZE]×[SIZE] даны пары концов
 * разных цветов; нужно проложить каждому цвету непрерывную тропинку между его
 * концами, не пересекая чужие тропинки. Построение идёт пошагово: [press]
 * начинает тропинку от конца и продлевает её на соседнюю свободную ячейку,
 * замыкание происходит на втором конце того же цвета; [release] отбрасывает
 * недоведённую тропинку. Все переходы возвращают новое состояние.
 */
data class PipesState(
    val endpoints: List<PipeEndpoints>,
    val paths: Map<Int, List<Int>> = emptyMap(),
    val activeColor: Int? = null,
    val activePath: List<Int> = emptyList(),
) {
    /** Готово, когда каждый цвет связал свои концы. */
    val won: Boolean get() = endpoints.isNotEmpty() && endpoints.all { it.color in paths }

    private fun endpointAt(cell: Int): PipeEndpoints? = endpoints.firstOrNull { it.first == cell || it.second == cell }

    private fun occupied(cell: Int): Boolean =
        endpointAt(cell) != null || paths.values.any { cell in it } || cell in activePath

    private fun adjacent(a: Int, b: Int): Boolean {
        val ar = a / SIZE
        val ac = a % SIZE
        val br = b / SIZE
        val bc = b % SIZE
        return kotlin.math.abs(ar - br) + kotlin.math.abs(ac - bc) == 1
    }

    /**
     * Шаг построения. Без активного цвета нажатие должно приходиться на конец
     * тропинки - он начинает свой цвет заново. С активным цветом нажатие на
     * соседнюю свободную ячейку продлевает тропинку, а на парный конец -
     * замыкает её.
     */
    fun press(cell: Int): PipesState {
        if (won || cell !in 0 until SIZE * SIZE) return this
        val active = activeColor
        if (active == null) {
            val endpoint = endpointAt(cell) ?: return this
            return copy(
                paths = paths - endpoint.color,
                activeColor = endpoint.color,
                activePath = listOf(cell),
            )
        }
        if (cell == activePath.last()) return this
        val pair = endpoints.first { it.color == active }
        val otherEnd = if (activePath.first() == pair.first) pair.second else pair.first
        if (cell == otherEnd) {
            // Замыкание допустимо только с соседней ячейки, иначе тропинка разорвана.
            if (!adjacent(activePath.last(), cell)) return this
            return copy(
                paths = paths + (active to activePath + cell),
                activeColor = null,
                activePath = emptyList(),
            )
        }
        if (occupied(cell) || !adjacent(activePath.last(), cell)) return this
        return copy(activePath = activePath + cell)
    }

    /** Завершает построение: недоведённая тропинка отбрасывается. */
    fun release(): PipesState {
        if (activeColor == null) return this
        return copy(activeColor = null, activePath = emptyList())
    }

    companion object {
        const val SIZE = 5

        private fun cell(row: Int, col: Int): Int = row * SIZE + col

        /**
         * Готовые раскладки, заданные готовыми тропинками: концы пары - первый
         * и последний путь. Каждая раскладка решаема по построению, а тест
         * дополнительно сверяет каждую тропинку с правилами партии.
         */
        private val LAYOUT_PATHS: List<List<List<Int>>> = listOf(
            // Столбцы по краям и дуга среднего цвета через верх.
            listOf(
                listOf(0, 5, 10, 15, 20),
                listOf(4, 9, 14, 19, 24),
                listOf(11, 6, 7, 8, 13),
            ),
            // Горизонтальные ряды и дуга через центр.
            listOf(
                listOf(0, 1, 2, 3, 4),
                listOf(20, 21, 22, 23, 24),
                listOf(10, 5, 6, 7, 8, 13),
            ),
            // Левый столбец, зигзаг через центр, короткая правая тропинка.
            listOf(
                listOf(0, 5, 10, 15, 20),
                listOf(4, 3, 8, 13, 18, 23),
                listOf(24, 19, 14, 9),
            ),
            // Средний столбец, левый зигзаг и левая тропинка.
            listOf(
                listOf(2, 7, 12, 17, 22),
                listOf(6, 11, 16, 21, 20),
                listOf(0, 5, 10, 15),
            ),
        )

        /** Раскладка первой партии; совместима с прежними проверками. */
        val PUZZLE: List<PipeEndpoints> = layout(0)

        fun layout(index: Int): List<PipeEndpoints> {
            require(index in LAYOUT_PATHS.indices) { "Unknown pipes layout" }
            return LAYOUT_PATHS[index].mapIndexed { color, path ->
                require(path.size >= 2) { "Pipe path needs two ends" }
                PipeEndpoints(color, path.first(), path.last())
            }
        }

        val LAYOUT_COUNT: Int get() = LAYOUT_PATHS.size

        /** Проверяет раскладку: пары концов не пересекаются и принадлежат разным цветам. */
        fun isLayoutValid(layout: List<PipeEndpoints>): Boolean {
            val cells = layout.flatMap { listOf(it.first, it.second) }
            return layout.isNotEmpty() && cells.size == cells.distinct().size &&
                layout.size == layout.map { it.color }.distinct().size &&
                cells.all { it in 0 until SIZE * SIZE }
        }

        fun create(layout: List<PipeEndpoints> = PUZZLE): PipesState {
            require(isLayoutValid(layout)) { "Invalid pipes layout" }
            return PipesState(endpoints = layout)
        }

        /** Новая партия на случайно выбранной раскладке. */
        fun createRandom(random: Random = Random.Default): PipesState =
            create(layout(random.nextInt(LAYOUT_COUNT)))
    }
}
