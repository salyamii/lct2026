package ru.nksk.lctapp.domain.minigame.chapterone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordStudyTest {
    @Test fun cargoNeedsTheCrateThenTheWholeRoute() {
        var game = RecordStudy(RecordStudyKind.CARGO_JOURNAL)
        game = RecordStudyGame.visit(game, 4)
        assertTrue(game.needsCorrection)
        assertTrue(game.recorded.isEmpty())
        RecordStudyGame.cargoSteps.forEach { step ->
            assertFalse(game.complete)
            game = RecordStudyGame.visit(game, step)
        }
        assertTrue(game.complete)
        assertEquals(game, RecordStudyGame.visit(game, 0))
    }

    @Test fun lensesMatchByColourInAnyOrderWithoutTeachingSignalOrderEarly() {
        var game = RecordStudy(RecordStudyKind.THREE_LENSES)
        game = RecordStudyGame.selectLens(game, SignalLight.BLUE)
        game = RecordStudyGame.placeLens(game, SignalLight.RED)
        assertTrue(game.needsCorrection)
        assertTrue(game.recorded.isEmpty())
        for (light in listOf(SignalLight.BLUE, SignalLight.RED, SignalLight.AMBER)) {
            game = RecordStudyGame.placeLens(RecordStudyGame.selectLens(game, light), light)
        }
        assertTrue(game.complete)
        assertEquals(listOf(2, 0, 1), game.recorded)
    }

    @Test fun journalCopiesTheSameOrderAsTheLaterTowerSignal() {
        var game = RecordStudy(RecordStudyKind.LIGHT_ORDER)
        game = RecordStudyGame.stamp(game, SignalLight.AMBER)
        assertTrue(game.needsCorrection)
        assertTrue(game.recorded.isEmpty())
        SignalGame.code.forEach { game = RecordStudyGame.stamp(game, it) }
        assertTrue(game.complete)
        assertEquals(SignalGame.code.map { it.ordinal }, game.recorded)
    }

    @Test fun actionsCannotCompleteAnotherKindOfClue() {
        val cargo = RecordStudy(RecordStudyKind.CARGO_JOURNAL)
        assertEquals(cargo, RecordStudyGame.stamp(cargo, SignalLight.RED))
        val lights = RecordStudy(RecordStudyKind.LIGHT_ORDER)
        assertEquals(lights, RecordStudyGame.visit(lights, 0))
        assertEquals(lights, RecordStudyGame.selectLens(lights, SignalLight.BLUE))
    }

    @Test fun restoreRejectsSkippedRepeatedAndUnknownSteps() {
        assertTrue(RecordStudyGame.restore(RecordStudyKind.CARGO_JOURNAL, listOf(0, 2, 4), null).recorded.isEmpty())
        assertFalse(RecordStudyGame.restore(RecordStudyKind.THREE_LENSES, listOf(0, 0, 2), 1).complete)
        assertFalse(RecordStudyGame.restore(RecordStudyKind.LIGHT_ORDER, listOf(0, 2, 1), null).complete)
        assertTrue(RecordStudyGame.restore(RecordStudyKind.THREE_LENSES, listOf(0, 1, 9), null).recorded.isEmpty())
        assertEquals(null, RecordStudyGame.restore(RecordStudyKind.THREE_LENSES, listOf(0), 0).selectedLens)
        assertTrue(RecordStudyGame.restore(RecordStudyKind.LIGHT_ORDER, listOf(0, 1, 2), null).complete)
    }
}
