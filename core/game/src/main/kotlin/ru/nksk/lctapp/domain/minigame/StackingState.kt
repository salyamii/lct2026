package ru.nksk.lctapp.domain.minigame

/** Уложенный ящик: координата левого края и ширина на полосе [StackingState.SPACE]. */
data class StackedBlock(val x: Int, val width: Int)

/**
 * Чистое состояние игры «Ящики на причале»: блок ходит туда-обратно по полосе,
 * нажатие укладывает его на предыдущий. Остаётся только пересечение - промах
 * завершает партию. Цель - уложить [ROUNDS] ящиков. Движение блока ведёт UI;
 * состояние фиксирует только укладку.
 */
data class StackingState(
    val locked: List<StackedBlock> = emptyList(),
    val blockWidth: Int = START_WIDTH,
    val placed: Int = 0,
    val finished: Boolean = false,
) {
    /** Ширина блока, который сейчас переносит игрок. */
    val currentWidth: Int get() = blockWidth

    /** Собрано ровно [ROUNDS] ящиков без промаха. */
    val won: Boolean get() = finished && placed == ROUNDS

    /**
     * Укладка блока левым краем в [x]. Первый ящик ставится как стоит, каждый
     * следующий обрезается до пересечения с предыдущим; нулевое пересечение -
     * промах и конец партии.
     */
    fun dropAt(x: Int): StackingState {
        if (finished) return this
        val dropped = x.coerceIn(0, SPACE - blockWidth)
        val top = locked.lastOrNull() ?: return copy(locked = listOf(StackedBlock(x = dropped, width = blockWidth)), placed = 1)
        val left = maxOf(dropped, top.x)
        val right = minOf(dropped + blockWidth, top.x + top.width)
        val kept = right - left
        if (kept <= 0) {
            return copy(finished = true)
        }
        return copy(
            locked = locked + StackedBlock(x = left, width = kept),
            blockWidth = kept,
            placed = placed + 1,
            finished = placed + 1 == ROUNDS,
        )
    }

    companion object {
        const val ROUNDS = 4
        const val SPACE = 100
        const val START_WIDTH = 50

        fun create(): StackingState = StackingState()
    }
}
