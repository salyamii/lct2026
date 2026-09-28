package ru.nksk.lctapp.domain.minigame

/**
 * Чистое состояние игры «Карта маршрута»: поле [SIZE]×[SIZE], плитки 1..15 и
 * пустая ячейка (0). Нажатие по плитке рядом с пустой ячейкой сдвигает её.
 * Финал — плитки по порядку. Перемешивание фиксированной цепочкой ходов,
 * поэтому стартовая партия всегда решаема.
 */
data class SlidingState(
    val tiles: List<Int>,
    val moves: Int = 0,
) {
    /** Собранная карта: на первых позициях плитки 1..15, в углу — пустая. */
    val won: Boolean get() = tiles.size == SIZE * SIZE && tiles.dropLast(1) == (1..SIZE * SIZE - 1).toList() && tiles.last() == BLANK

    private fun blankAt(): Int = tiles.indexOf(BLANK)

    /** Нажатие по плитке: двигает её, если она соседствует с пустой ячейкой. */
    fun tap(index: Int): SlidingState {
        if (won || index !in tiles.indices || tiles[index] == BLANK) return this
        val blank = blankAt()
        val adjacent = (index / SIZE == blank / SIZE && kotlin.math.abs(index - blank) == 1) ||
            (index % SIZE == blank % SIZE && kotlin.math.abs(index - blank) == SIZE)
        if (!adjacent) return this
        val swapped = tiles.toMutableList()
        swapped[blank] = swapped[index]
        swapped[index] = BLANK
        return copy(tiles = swapped, moves = moves + 1)
    }

    companion object {
        const val SIZE = 4
        const val BLANK = 0

        /** Детерминированная цепочка сдвигов пустой ячейки: 0 — вправо, 1 — вниз, 2 — влево, 3 — вверх. */
        private val SHUFFLE_PATTERN = listOf(
            0, 1, 0, 3, 2, 1, 0, 3, 2, 1,
            0, 1, 2, 3, 0, 1, 2, 3, 0, 1,
            2, 1, 0, 3, 2, 1, 0, 1, 2, 3,
            0, 1, 2, 3, 0, 3, 2, 1, 0, 1,
            2, 3, 0, 1, 2, 3, 0, 1, 2, 3,
            0, 1, 0, 3, 2, 1, 0, 3, 2, 1,
            0, 1, 2, 3, 0, 1, 2, 3, 0, 1,
            2, 1, 0, 3, 2, 1, 0, 1, 2, 3,
            0, 1, 2, 3, 0, 3, 2, 1, 0, 1,
            2, 3, 0, 1, 2, 3, 0, 1, 2, 3,
            0, 1, 0, 3, 2, 1, 0, 3, 2, 1,
            0, 1, 2, 3, 0, 1, 2, 3, 0, 1,
            2, 1, 0, 3, 2, 1, 0, 1, 2, 3,
            0, 1, 2, 3, 0, 3, 2, 1, 0, 1,
            2, 3, 0, 1, 2, 3, 0, 1, 2, 3,
            0, 1, 0, 3, 2, 1, 0, 3, 2, 1,
            0, 1, 2, 3, 0, 1, 2, 3, 0, 1,
            2, 1, 0, 3, 2, 1, 0, 1, 2, 3,
            0, 1, 2, 3, 0, 3, 2, 1, 0, 1,
            2, 3, 0, 1, 2, 3, 0, 1, 2, 3,
        )

        private val DELTAS = listOf(0 to 1, 1 to 0, 0 to -1, -1 to 0)

        /** Перемешивает собранную карту фиксированной решаемой цепочкой ходов. */
        fun shuffled(): SlidingState {
            val tiles = (1..SIZE * SIZE - 1).toList() + BLANK
            var blank = tiles.indexOf(BLANK)
            val board = tiles.toMutableList()
            SHUFFLE_PATTERN.forEach { direction ->
                val row = blank / SIZE
                val col = blank % SIZE
                val (dr, dc) = DELTAS[direction]
                val r = row + dr
                val c = col + dc
                if (r in 0 until SIZE && c in 0 until SIZE) {
                    val target = r * SIZE + c
                    board[blank] = board[target]
                    board[target] = BLANK
                    blank = target
                }
            }
            return SlidingState(tiles = board.toList())
        }
    }
}
