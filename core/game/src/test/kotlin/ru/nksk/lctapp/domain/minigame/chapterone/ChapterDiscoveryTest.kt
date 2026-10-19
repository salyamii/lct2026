package ru.nksk.lctapp.domain.minigame.chapterone

import org.junit.Assert.*
import org.junit.Test

class ChapterDiscoveryTest {
    @Test fun openingTheDomeDoesNotAlsoRemoveTheTelescopeCover() {
        val initial = ChapterDiscovery(DiscoveryScene.OBSERVATORY)
        assertEquals(initial, initial.act(DiscoveryAction.RemoveCover))
        val open = initial.act(DiscoveryAction.OpenDome)
        assertFalse(open.complete)
        assertEquals(open, open.act(DiscoveryAction.OpenDome))
        val uncovered = open.act(DiscoveryAction.RemoveCover)
        assertTrue(uncovered.complete)
        assertEquals(uncovered, uncovered.act(DiscoveryAction.FlipEmblem))
    }

    @Test fun solutionCleansMoreAtOnceButBothMethodsRequireTheWholePlate() {
        var hand = ChapterDiscovery(DiscoveryScene.PLATE_HAND)
        var solution = ChapterDiscovery(DiscoveryScene.PLATE_SOLUTION)
        hand = hand.act(DiscoveryAction.Clean(.5f, .5f))
        solution = solution.act(DiscoveryAction.Clean(.5f, .5f))
        assertTrue(solution.revealed.size > hand.revealed.size)
        assertFalse(solution.complete)
        ChapterDiscoveryGame.dust.forEach { p ->
            hand = hand.act(DiscoveryAction.Clean(p.x, p.y))
            solution = solution.act(DiscoveryAction.Clean(p.x, p.y))
        }
        assertTrue(hand.complete)
        assertTrue(solution.complete)
    }

    @Test fun invalidCoordinatesCannotCleanThePlate() {
        val initial = ChapterDiscovery(DiscoveryScene.PLATE_HAND)
        listOf(Float.NaN to .5f, .5f to Float.POSITIVE_INFINITY, -.1f to .5f, .5f to 1.1f).forEach { (x, y) ->
            assertEquals(initial, initial.act(DiscoveryAction.Clean(x, y)))
        }
    }

    @Test fun inscriptionNeedsThreeDifferentFragmentsAndDoesNotAcceptCleaningActions() {
        var game = ChapterDiscovery(DiscoveryScene.INSCRIPTION)
        assertEquals(game, game.act(DiscoveryAction.Clean(.5f, .5f)))
        assertEquals(game, game.act(DiscoveryAction.Illuminate(3)))
        game = game.act(DiscoveryAction.Illuminate(1)).act(DiscoveryAction.Illuminate(1))
        assertEquals(setOf(1), game.revealed)
        assertFalse(game.complete)
        game = game.act(DiscoveryAction.Illuminate(2)).act(DiscoveryAction.Illuminate(0))
        assertTrue(game.complete)
    }

    @Test fun emblemRequiresTurningBeforeMatching() {
        val initial = ChapterDiscovery(DiscoveryScene.EMBLEM)
        assertEquals(initial, initial.act(DiscoveryAction.MatchEmblem))
        assertFalse(initial.act(DiscoveryAction.FlipEmblem).complete)
        assertTrue(initial.act(DiscoveryAction.FlipEmblem).act(DiscoveryAction.MatchEmblem).complete)
    }

    @Test fun eachReflectionNeedsAimAndExplicitCapture() {
        var game = ChapterDiscovery(DiscoveryScene.SIGNAL_POST)
        ChapterDiscoveryGame.reflectionTargets.forEachIndexed { i, target ->
            assertFalse(game.complete)
            assertEquals(game, game.act(DiscoveryAction.CaptureReflection))
            game = game.act(DiscoveryAction.Aim(target))
            assertTrue(game.reflectionReady)
            assertEquals(i, game.reflected.size)
            game = game.act(DiscoveryAction.CaptureReflection)
            assertEquals((0..i).toList(), game.reflected)
            assertEquals(0f, game.focus)
        }
        assertTrue(game.complete)
    }

    @Test fun threeFindingsAndAllNetworkEdgesAreRequiredForTheFinale() {
        var game = ChapterDiscovery(DiscoveryScene.FINALE)
        assertEquals(game, game.act(DiscoveryAction.SelectPoint(0)))
        game = game.act(DiscoveryAction.SelectFinding(1))
        assertEquals(game, game.act(DiscoveryAction.PlaceFinding(0)))
        for (i in listOf(1, 2, 0)) game = game.act(DiscoveryAction.SelectFinding(i)).act(DiscoveryAction.PlaceFinding(i))
        assertFalse(game.complete)
        assertEquals(game, game.act(DiscoveryAction.SelectFinding(0)))
        ChapterDiscoveryGame.networkEdges.forEachIndexed { i, (a, b) ->
            game = game.act(DiscoveryAction.SelectPoint(a)).act(DiscoveryAction.SelectPoint(b))
            assertEquals(i + 1, game.joined.size)
        }
        assertTrue(game.complete)
        assertNull(game.selected)
    }

    @Test fun arbitraryConnectionsDoNotCountAsRoutes() {
        val findings = ChapterDiscovery(DiscoveryScene.FINALE, placed = setOf(0, 1, 2))
        val wrongEdge = findings.act(DiscoveryAction.SelectPoint(0)).act(DiscoveryAction.SelectPoint(4))
        assertTrue(wrongEdge.joined.isEmpty())
        assertFalse(wrongEdge.complete)
    }

    @Test fun completedBoardsRemainCompletedForAnExplicitRetryOfSaving() {
        val finished = listOf(
            ChapterDiscovery(DiscoveryScene.OBSERVATORY, stage = 2),
            ChapterDiscovery(DiscoveryScene.PLATE_HAND, revealed = ChapterDiscoveryGame.dust.indices.toSet()),
            ChapterDiscovery(DiscoveryScene.PLATE_SOLUTION, revealed = ChapterDiscoveryGame.dust.indices.toSet()),
            ChapterDiscovery(DiscoveryScene.INSCRIPTION, revealed = setOf(0, 1, 2)),
            ChapterDiscovery(DiscoveryScene.EMBLEM, stage = 2),
            ChapterDiscovery(DiscoveryScene.SIGNAL_POST, reflected = listOf(0, 1, 2)),
            ChapterDiscovery(DiscoveryScene.FINALE, placed = setOf(0, 1, 2), joined = ChapterDiscoveryGame.networkEdges.indices.toSet()),
        )
        finished.forEach { expected ->
            assertTrue(expected.complete)
            val restored = restore(expected)
            assertEquals(expected, restored)
            assertTrue(restored.complete)
            assertEquals(restored, restored.act(DiscoveryAction.OpenDome))
        }
    }

    @Test fun corruptProgressCannotProduceACompletion() {
        val invalid = listOf(
            ChapterDiscovery(DiscoveryScene.OBSERVATORY, stage = 3),
            ChapterDiscovery(DiscoveryScene.EMBLEM, stage = 2, selected = 0),
            ChapterDiscovery(DiscoveryScene.INSCRIPTION, revealed = setOf(0, 1, 2, 3)),
            ChapterDiscovery(DiscoveryScene.SIGNAL_POST, reflected = listOf(2, 1, 0)),
            ChapterDiscovery(DiscoveryScene.SIGNAL_POST, reflected = listOf(0, 1, 2), focus = Float.NaN),
            ChapterDiscovery(DiscoveryScene.FINALE, joined = ChapterDiscoveryGame.networkEdges.indices.toSet()),
            ChapterDiscovery(DiscoveryScene.FINALE, placed = setOf(0, 1, 2), joined = ChapterDiscoveryGame.networkEdges.indices.toSet(), selected = 1),
        )
        invalid.forEach { state ->
            assertFalse(state.complete)
            assertFalse(ChapterDiscoveryGame.valid(state))
            assertEquals(ChapterDiscovery(state.scene), restore(state))
        }
        assertFalse(ChapterDiscoveryGame.restore(DiscoveryScene.PLATE_HAND,
            revealed = ChapterDiscoveryGame.dust.indices.toList() + 0).complete)
        assertFalse(ChapterDiscoveryGame.restore(DiscoveryScene.EMBLEM, stage = 2, version = 999).complete)
    }

    @Test fun partialProgressRestoresWithoutInventingTheNextAction() {
        val partial = listOf(
            ChapterDiscovery(DiscoveryScene.OBSERVATORY, stage = 1),
            ChapterDiscovery(DiscoveryScene.PLATE_HAND, revealed = setOf(0, 2, 8)),
            ChapterDiscovery(DiscoveryScene.INSCRIPTION, revealed = setOf(2)),
            ChapterDiscovery(DiscoveryScene.EMBLEM, stage = 1),
            ChapterDiscovery(DiscoveryScene.SIGNAL_POST, focus = .72f, reflected = listOf(0)),
            ChapterDiscovery(DiscoveryScene.FINALE, placed = setOf(2), selected = 0),
            ChapterDiscovery(DiscoveryScene.FINALE, placed = setOf(0, 1, 2), joined = setOf(0), selected = 1),
        )
        partial.forEach { assertEquals(it, restore(it)); assertFalse(it.complete) }
    }

    private fun ChapterDiscovery.act(action: DiscoveryAction) = ChapterDiscoveryGame.reduce(this, action)
    private fun restore(game: ChapterDiscovery) = ChapterDiscoveryGame.restore(game.scene, game.stage,
        game.revealed.toList(), game.placed.toList(), game.selected, game.joined.toList(), game.focus, game.reflected)
}
