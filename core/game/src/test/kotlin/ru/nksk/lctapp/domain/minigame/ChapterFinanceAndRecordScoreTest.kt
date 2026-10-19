package ru.nksk.lctapp.domain.minigame

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.nksk.lctapp.domain.minigame.chapterone.RecordStudy
import ru.nksk.lctapp.domain.minigame.chapterone.RecordStudyKind
import ru.nksk.lctapp.domain.minigame.chapterone.ShoppingComparison
import ru.nksk.lctapp.domain.minigame.chapterone.ShoppingSide
import ru.nksk.lctapp.domain.minigame.chapterone.TripPacking
import ru.nksk.lctapp.domain.minigame.chapterone.TripSupply

class ChapterFinanceAndRecordScoreTest {
    @Test fun packingNeedsBothAValidPlanAndExplicitConfirmation() {
        assertNull(DeedGameScore.fromTripPacking(TripPacking()))
        val ready = TripPacking(setOf(TripSupply.MAP, TripSupply.LANTERN, TripSupply.BUN))
        assertNull(DeedGameScore.fromTripPacking(ready))
        val finished = checkNotNull(DeedGameScore.fromTripPacking(ready.finish()))
        assertEquals(DeedGameKind.TRIP_PACKING, finished.kind)
        assertEquals(1, finished.correct)
        assertEquals(1, finished.attempts)
        assertEquals(0L, finished.reward(0))
    }

    @Test fun unfinishedShoppingCannotBeSubmittedAndEqualPricesAcceptEitherShop() {
        var game = ShoppingComparison()
        assertNull(DeedGameScore.fromShoppingComparison(game))
        // First two choices deliberately cost more. The third pair costs the same.
        game = game.choose(ShoppingSide.LEFT)
        assertNull(DeedGameScore.fromShoppingComparison(game))
        game = game.next().choose(ShoppingSide.RIGHT)
        assertNull(DeedGameScore.fromShoppingComparison(game))
        val score = checkNotNull(DeedGameScore.fromShoppingComparison(game.next().choose(ShoppingSide.RIGHT)))
        assertEquals(DeedGameKind.SHOPPING_COMPARISON, score.kind)
        assertEquals(1, score.correct)
        assertEquals(3, score.attempts)
        assertEquals(2L, score.reward(6))
        assertEquals(0L, score.reward(0))
    }

    @Test fun completedShoppingPreservesItsActualResultsAcrossScoreSerialization() {
        val game = ShoppingComparison().choose(ShoppingSide.RIGHT).next()
            .choose(ShoppingSide.LEFT).next().choose(ShoppingSide.LEFT)
        val score = checkNotNull(DeedGameScore.fromShoppingComparison(game))
        val restored = Json.decodeFromString<DeedGameScore>(Json.encodeToString(score))
        assertEquals(DeedGameKind.SHOPPING_COMPARISON, restored.kind)
        assertEquals(3, restored.correct)
        assertEquals(3, restored.attempts)
        assertEquals(6L, restored.reward(6))
    }

    @Test fun everyRecordKindMustFinishItsOwnTask() {
        val completed = listOf(
            RecordStudy(RecordStudyKind.CARGO_JOURNAL, listOf(0, 1, 2, 3, 4)) to DeedGameKind.RECORD_JOURNAL,
            RecordStudy(RecordStudyKind.THREE_LENSES, listOf(2, 0, 1)) to DeedGameKind.RECORD_LENSES,
            RecordStudy(RecordStudyKind.LIGHT_ORDER, listOf(0, 1, 2)) to DeedGameKind.RECORD_LIGHTS,
        )
        completed.forEach { (state, kind) ->
            assertNull(DeedGameScore.fromRecordStudy(state.copy(recorded = state.recorded.dropLast(1))))
            val score = checkNotNull(DeedGameScore.fromRecordStudy(state))
            assertEquals(kind, score.kind)
            assertEquals(1, score.correct)
            assertEquals(1, score.attempts)
            assertEquals(0L, score.reward(0))
            assertNotNull(Json.decodeFromString<DeedGameScore>(Json.encodeToString(score)))
        }
        // Lens placement is unordered; the journal's signal sequence is ordered.
        assertNull(DeedGameScore.fromRecordStudy(RecordStudy(RecordStudyKind.LIGHT_ORDER, listOf(2, 0, 1))))
    }

    @Test fun incompleteAndInconsistentRecordStateCannotProduceACompletedScore() {
        assertNull(DeedGameScore.fromRecordStudy(RecordStudy(RecordStudyKind.CARGO_JOURNAL, listOf(1, 2, 3, 4))))
        assertNull(DeedGameScore.fromRecordStudy(RecordStudy(RecordStudyKind.THREE_LENSES, listOf(0, 0, 2))))
        assertNull(DeedGameScore.fromRecordStudy(RecordStudy(RecordStudyKind.THREE_LENSES,
            listOf(0, 1, 2), selectedLens = 0)))
        assertNull(DeedGameScore.fromRecordStudy(RecordStudy(RecordStudyKind.LIGHT_ORDER,
            listOf(0, 1, 2), needsCorrection = true)))
    }

    @Test fun serializedResultsCannotSkipOrInventRounds() {
        for (kind in listOf("TRIP_PACKING", "RECORD_JOURNAL", "RECORD_LENSES", "RECORD_LIGHTS")) {
            assertTrue(runCatching {
                Json.decodeFromString<DeedGameScore>("""{"kind":"$kind","correct":0,"attempts":1}""")
            }.isFailure)
            assertTrue(runCatching {
                Json.decodeFromString<DeedGameScore>("""{"kind":"$kind","correct":2,"attempts":2}""")
            }.isFailure)
        }
        assertTrue(runCatching {
            Json.decodeFromString<DeedGameScore>("""{"kind":"SHOPPING_COMPARISON","correct":2,"attempts":2}""")
        }.isFailure)
    }
}
