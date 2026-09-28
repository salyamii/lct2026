package ru.nksk.lctapp.domain.minigame

import org.junit.Assert.*
import org.junit.Test

class LightsStateTest {
    @Test fun tapFlipsTheCrossAndCountsTheMove() {
        val board = LightsState.create(puzzle = 0)
        val center = 2 * LightsState.SIZE + 2
        val after = board.tap(center)
        assertEquals(board.moves + 1, after.moves)
        LightsState.NEIGHBOR_OFFSETS.forEach { (dr, dc) ->
            val r = 2 + dr
            val c = 2 + dc
            assertEquals(!board.grid[r * LightsState.SIZE + c], after.grid[r * LightsState.SIZE + c])
        }
    }

    @Test fun offBoardIsImmediatelyWonAndIgnoresTaps() {
        val won = LightsState(List(LightsState.SIZE * LightsState.SIZE) { false })
        assertTrue(won.won)
        assertEquals(won, won.tap(12))
    }

    @Test fun everyPuzzleIsSolvable() {
        LightsState.PUZZLES.forEachIndexed { index, _ ->
            assertTrue("Puzzle $index must be solvable", solvable(index))
        }
    }

    @Test fun createRejectsUnknownPuzzles() {
        try {
            LightsState.create(puzzle = LightsState.PUZZLES.size)
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) { }
    }

    /**
     * Проверка решаемости «догонянием света» вниз: перебор нажатий верхней
     * строки (2^5 вариантов) с последующим дожимом под каждую горящую ячейку
     * покрывает все решения — если партия решаема, какой-то верхний набор её
     * закроет.
     */
    private fun solvable(puzzle: Int): Boolean {
        val size = LightsState.SIZE
        for (mask in 0 until (1 shl size)) {
            var board = LightsState.create(puzzle)
            repeat(size) { col -> if (mask and (1 shl col) != 0) board = board.tap(col) }
            for (row in 0 until size - 1) {
                for (col in 0 until size) {
                    if (board.grid[row * size + col]) board = board.tap((row + 1) * size + col)
                }
            }
            if (board.won) return true
        }
        return false
    }
}
