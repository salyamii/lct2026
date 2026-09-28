package ru.nksk.lctapp.domain.minigame

import org.junit.Assert.*
import org.junit.Test

class StackingStateTest {
    @Test fun firstDropKeepsTheFullWidthWhereItStands() {
        val board = StackingState.create().dropAt(20)
        assertEquals(1, board.placed)
        assertEquals(StackingState.START_WIDTH, board.locked.single().width)
        assertEquals(20, board.locked.single().x)
        assertFalse(board.finished)
    }

    @Test fun overlapShrinksTheNextBlock() {
        val base = StackingState.create().dropAt(20)
        // Следующий блок сдвинут вправо на 10: пересечение 70 - 30 = 40.
        val second = base.dropAt(base.locked.single().x + 10)
        assertEquals(2, second.placed)
        assertEquals(40, second.currentWidth)
        assertEquals(30, second.locked.last().x)
    }

    @Test fun missFinishesTheRoundWithPartialProgress() {
        // Основание 20..80; следующий блок смещён к левому краю (20..60), затем уведён вправо.
        var board = StackingState.create().dropAt(20)
        board = board.dropAt(0)
        assertEquals(2, board.placed)
        val missed = board.dropAt(60)
        assertTrue(missed.finished)
        assertEquals(2, missed.placed)
        assertFalse(missed.won)
    }

    @Test fun perfectStackingWins() {
        var board = StackingState.create().dropAt(50)
        repeat(StackingState.ROUNDS - 1) { board = board.dropAt(board.locked.last().x) }
        assertTrue(board.won)
        assertEquals(StackingState.ROUNDS, board.placed)
        assertEquals(StackingState.START_WIDTH, board.currentWidth)
    }

    @Test fun finishedBoardIgnoresDrops() {
        var board = StackingState.create().dropAt(50)
        repeat(StackingState.ROUNDS - 1) { board = board.dropAt(board.locked.last().x) }
        assertEquals(board, board.dropAt(0))
    }
}
