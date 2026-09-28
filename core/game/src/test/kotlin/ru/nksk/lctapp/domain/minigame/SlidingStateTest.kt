package ru.nksk.lctapp.domain.minigame

import org.junit.Assert.*
import org.junit.Test

class SlidingStateTest {
    @Test fun shuffledBoardIsSolvableAndNotSolved() {
        val board = SlidingState.shuffled()
        assertFalse(board.won)
        assertTrue(board.tiles.toSortedSet() == (0..15).toSortedSet())
    }

    @Test fun solvedBoardWinsImmediately() {
        val board = SlidingState((1..15).toList() + 0)
        assertTrue(board.won)
    }

    @Test fun tapSwapsOnlyTheNeighborOfTheBlank() {
        // Плитка 15 стоит под пустой ячейкой и сдвигается в неё — доска собирается.
        val oneMoveAway = SlidingState((1..14).toList() + 0 + 15)
        val after = oneMoveAway.tap(15)
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 0), after.tiles)
        assertEquals(1, after.moves)
        assertTrue(after.won)
        // Диагональная плитка не двигается.
        assertEquals(oneMoveAway, oneMoveAway.tap(11))
        // Дальняя плитка не двигается.
        assertEquals(oneMoveAway, oneMoveAway.tap(0))
    }

    @Test fun movesNeverBreakThePermutation() {
        val board = SlidingState.shuffled()
        var moved = board
        // Прокатываем все индексы как ходы: разрешённые меняют местами две ячейки,
        // запрещённые не меняют ничего — перестановка обязана сохраниться.
        repeat(SlidingState.SIZE * SlidingState.SIZE) { index ->
            moved = moved.tap(index)
            assertTrue(moved.tiles.toSortedSet() == (0..15).toSortedSet())
        }
        assertTrue(moved.moves <= board.moves + SlidingState.SIZE * SlidingState.SIZE)
    }

    @Test fun wonBoardIgnoresTaps() {
        val solved = SlidingState((1..15).toList() + 0)
        assertEquals(solved, solved.tap(14))
    }
}
