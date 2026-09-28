package ru.nksk.lctapp.domain.minigame

import org.junit.Assert.*
import org.junit.Test

class SortingStateTest {
    @Test fun startDealsThreeFullTubesAndOneEmpty() {
        val board = SortingState.create()
        assertEquals(4, board.tubes.size)
        assertEquals(3, board.tubes.count { it.size == SortingState.CAPACITY })
        assertEquals(SortingState.BALL_COUNT, board.tubes.sumOf { it.size })
        assertFalse(board.won)
    }

    @Test fun selectionFollowsTapsAndPourMovesTheTopBall() {
        var board = SortingState.create()
        board = board.tap(0)
        assertEquals(0, board.selected)
        // В пустую банку переливается верхний припас.
        board = board.tap(3)
        assertNull(board.selected)
        assertEquals(3, board.tubes[0].size)
        assertEquals(1, board.tubes[3].size)
        assertEquals(1, board.moves)
    }

    @Test fun pouringOnADifferentColorIsRejectedAndReselects() {
        val board = SortingState.create().tap(0).tap(1)
        // Верхние цвета банок 0 и 1 разные (3 и 2): переложить нельзя, выделение переехало.
        assertEquals(1, board.selected)
        assertEquals(SortingState.create().tubes, board.tubes)
        assertEquals(0, board.moves)
    }

    @Test fun repeatedTapDeselects() {
        val board = SortingState.create().tap(0)
        assertNull(board.tap(0).selected)
    }

    @Test fun sortedBoardWins() {
        val won = SortingState(
            tubes = listOf(
                listOf(0, 0, 0, 0),
                listOf(1, 1, 1),
                emptyList(),
                emptyList(),
            ),
        )
        assertTrue(won.won)
    }

    @Test fun wonBoardIgnoresTaps() {
        val won = SortingState(tubes = listOf(emptyList(), emptyList(), emptyList(), emptyList()))
        assertEquals(won, won.tap(0))
    }
}
