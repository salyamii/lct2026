package ru.nksk.lctapp.domain.minigame

import org.junit.Assert.*
import org.junit.Test

class PipesStateTest {
    private val size = PipesState.SIZE

    @Test fun pressingAnEndpointStartsThatColor() {
        val board = PipesState.create()
        val started = board.press(0)
        assertEquals(0, started.activeColor)
        assertEquals(listOf(0), started.activePath)
        // Повторное нажатие на тот же конец ничего не меняет.
        assertEquals(started, started.press(0))
    }

    @Test fun pathExtendsStepByStepAndLocksOnThePairedEndpoint() {
        var board = PipesState.create().press(0)
        listOf(5, 10, 15).forEach { cell -> board = board.press(cell) }
        val locked = board.press(20)
        assertFalse(locked.won) // связан только левый столбец
        assertEquals(listOf(0, 5, 10, 15, 20), locked.paths[0])
        assertNull(locked.activeColor)
    }

    @Test fun incompletePathIsDiscardedOnRelease() {
        val board = PipesState.create().press(0).press(5)
        val released = board.release()
        assertTrue(released.paths.isEmpty())
        assertNull(released.activeColor)
    }

    @Test fun pathsCannotCrossOtherColorsOrThemselves() {
        // Средний цвет занял дугу 11-6-7-8-13.
        var middle = PipesState.create().press(11)
        listOf(6, 7, 8).forEach { cell -> middle = middle.press(cell) }
        middle = middle.press(13)
        assertEquals(listOf(11, 6, 7, 8, 13), middle.paths[2])

        val blocked = middle.press(0).press(5).press(10).press(11)
        assertEquals(listOf(0, 5, 10), blocked.activePath)

        // Прыжок через клетку запрещён: только соседние ячейки продлевают тропинку.
        val jumped = middle.press(0).press(5).press(15)
        assertEquals(listOf(0, 5), jumped.activePath)
    }

    @Test fun allThreeColorsTogetherWinThePuzzle() {
        var board = PipesState.create()
        fun route(start: Int, middle: List<Int>) {
            board = board.press(start)
            middle.forEach { cell -> board = board.press(cell) }
        }
        route(11, listOf(6, 7, 8, 13)) // средний цвет: дуга через верх
        route(0, listOf(5, 10, 15, 20)) // левый столбец
        route(4, listOf(9, 14, 19, 24)) // правый столбец
        assertTrue(board.won)
        assertEquals(setOf(0, 1, 2), board.paths.keys)
        // Незавершённое построение не даёт зачётного результата.
        assertNull(DeedGameScore.fromPipes(board.copy(activeColor = 0, activePath = listOf(24))))
    }

    @Test fun createRejectsInvalidLayouts() {
        try {
            PipesState.create(listOf(PipeEndpoints(0, 0, 0)))
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) { }
    }
}
