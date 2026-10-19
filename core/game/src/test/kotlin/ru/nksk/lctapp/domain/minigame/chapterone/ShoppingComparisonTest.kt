package ru.nksk.lctapp.domain.minigame.chapterone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShoppingComparisonTest {
    @Test fun theSameSuppliesKeepTheirPackingPrices() {
        val map = ShoppingComparison.rounds.single { it.item == ShoppingItem.MAP }
        val lantern = ShoppingComparison.rounds.single { it.item == ShoppingItem.LANTERN }
        assertEquals(TripSupply.MAP.price, minOf(map.leftPrice, map.rightPrice))
        assertEquals(TripSupply.LANTERN.price, minOf(lantern.leftPrice, lantern.rightPrice))
    }

    @Test fun theFirstAnswerIsKeptUntilTheChildReadsFeedback() {
        val answered = ShoppingComparison().choose(ShoppingSide.LEFT)
        assertTrue(answered.feedbackShown)
        assertEquals(answered, answered.choose(ShoppingSide.RIGHT))
        assertEquals(0, answered.correctAnswers)
        assertEquals(2, answered.answers.single().extraCost)
        assertEquals(1, answered.next().currentIndex)
    }

    @Test fun onlyAllThreeAnswersCompleteTheOrderAndEqualPricesAcceptEitherSide() {
        ShoppingSide.entries.forEach { finalSide ->
            val completed = ShoppingComparison().choose(ShoppingSide.RIGHT).next()
                .choose(ShoppingSide.LEFT).next().choose(finalSide)
            assertTrue(completed.completed)
            assertEquals(3, completed.correctAnswers)
            assertEquals(completed, completed.next().choose(ShoppingSide.LEFT))
        }
        assertFalse(ShoppingComparison().choose(ShoppingSide.RIGHT).next().completed)
    }

    @Test fun wrongDecisionsRemainRealEvidenceAndDoNotBlockCompletion() {
        val completed = ShoppingComparison().choose(ShoppingSide.LEFT).next()
            .choose(ShoppingSide.RIGHT).next().choose(ShoppingSide.LEFT)
        assertTrue(completed.completed)
        assertEquals(1, completed.correctAnswers)
        assertEquals(listOf(6, 5, 3), completed.answers.map { it.selectedPrice })
        assertEquals(listOf(4, 5, 3), completed.answers.map { it.rightPrice })
    }

    @Test fun restorationReconstructsAuthoredPricesAndFeedback() {
        val game = ShoppingComparison().choose(ShoppingSide.RIGHT).next().choose(ShoppingSide.LEFT)
        assertEquals(game, ShoppingComparison.restore(listOf("RIGHT", "LEFT"), true))
        assertEquals(game.next(), ShoppingComparison.restore(listOf("RIGHT", "LEFT"), false))
        assertEquals(ShoppingComparison(), ShoppingComparison.restore(listOf("UNKNOWN"), true))
        assertEquals(ShoppingComparison(), ShoppingComparison.restore(emptyList(), true))
        assertEquals(ShoppingComparison(), ShoppingComparison.restore(listOf("LEFT", "RIGHT", "LEFT"), false))
    }
}
