package ru.nksk.lctapp.domain.minigame

import org.junit.Assert.*
import org.junit.Test

class DifferencesStateTest {
    @Test fun everySceneHidesExactlyFiveDifferences() {
        repeat(3) { scene ->
            val board = DifferencesState.create(scene)
            assertEquals(DifferencesState.CELLS, board.top.size)
            assertEquals(DifferencesState.DIFF_COUNT, board.differences.size)
            assertFalse(board.won)
        }
    }

    @Test fun tappingADifferenceMarksItAndCountsTheTap() {
        val board = DifferencesState.create(0)
        val cell = board.differences.first()
        val after = board.tap(cell)
        assertEquals(setOf(cell), after.found)
        assertEquals(1, after.taps)
    }

    @Test fun regularCellsOnlySpendTaps() {
        val board = DifferencesState.create(0)
        val regular = board.top.indices.first { it !in board.differences }
        val after = board.tap(regular)
        assertTrue(after.found.isEmpty())
        assertEquals(1, after.taps)
        // Несуществующая ячейка игнорируется полностью.
        assertEquals(board, board.tap(board.top.size))
    }

    @Test fun findingAllDifferencesWins() {
        var board = DifferencesState.create(1)
        board.differences.forEach { cell -> board = board.tap(cell) }
        assertTrue(board.won)
        assertEquals(DifferencesState.DIFF_COUNT, board.taps)
    }

    @Test fun wonBoardIgnoresTaps() {
        var board = DifferencesState.create(2)
        board.differences.forEach { cell -> board = board.tap(cell) }
        assertEquals(board, board.tap(0))
    }
}
