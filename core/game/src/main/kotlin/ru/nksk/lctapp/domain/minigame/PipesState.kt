package ru.nksk.lctapp.domain.minigame

/**
 * Пара концов одного цвета на поле «Свяжи концы».
 * [first] и [second] — индексы ячеек строки-мажорной сетки [PipesState.SIZE]×[PipesState.SIZE].
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
     * тропинки — он начинает свой цвет заново. С активным цветом нажатие на
     * соседнюю свободную ячейку продлевает тропинку, а на парный конец —
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

        /** Готовая раскладка из трёх цветов, решаемая без пересечений. */
        val PUZZLE: List<PipeEndpoints> = listOf(
            PipeEndpoints(0, cell(0, 0), cell(4, 0)),
            PipeEndpoints(1, cell(0, 4), cell(4, 4)),
            PipeEndpoints(2, cell(2, 1), cell(2, 3)),
        )

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
    }
}
