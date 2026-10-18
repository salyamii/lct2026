package ru.nksk.lctapp.domain.minigame.chapterone

import org.junit.Assert.*
import org.junit.Test

class SignalGameTest {
    @Test fun wrongSequenceCanBeEditedWithoutRestartingWholeGame() {
        var state = SignalState()
        listOf(SignalLight.RED, SignalLight.AMBER, SignalLight.RED).forEach { state = SignalGame.choose(state, it) }
        state = SignalGame.send(state)
        assertTrue(state.needsCorrection)
        state = SignalGame.choose(SignalGame.undo(state), SignalLight.BLUE)
        assertFalse(state.complete)
        assertTrue(SignalGame.send(state).complete)
    }

    @Test fun partialOrCorruptInputCannotBecomeSuccess() {
        assertFalse(SignalGame.send(SignalState(listOf(SignalLight.RED))).complete)
        assertEquals(SignalState(), SignalGame.restore(listOf(0, 20, 2), true))
        assertTrue(SignalGame.restore(listOf(0, 1, 2), true).complete)
    }
}
