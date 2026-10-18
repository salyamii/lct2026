package ru.nksk.lctapp.domain.minigame

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeGamesTest {
    @Test fun allChoiceGamesFinishOnlyAfterAcknowledgementAndRestartCleanly() {
        listOf(PracticeGames.needs(), PracticeGames.discounts(), PracticeGames.traps(),
            PracticeGames.extraPurchases()).forEach { initial -> verifyLifecycle(initial) }
    }

    private fun <Q : PracticeQuestion> verifyLifecycle(initial: PracticeChoiceState<Q>) {
        var game = initial
        initial.questions.indices.forEach { round ->
            assertEquals(game, game.reduce(PracticeChoiceAction.Next(round)))
            assertEquals(game, game.reduce(PracticeChoiceAction.Answer(-1, round)))
            assertEquals(game, game.reduce(PracticeChoiceAction.Answer(game.question.choiceCount, round)))
            assertEquals(game, game.reduce(PracticeChoiceAction.Answer(0, round - 1)))
            game = game.reduce(PracticeChoiceAction.Answer(game.question.correctChoice, round))
            assertFalse(game.finished)
            assertEquals(round + 1, game.correctAnswers)
            assertEquals(game, game.reduce(PracticeChoiceAction.Answer(0, round)))
            assertEquals(game, game.reduce(PracticeChoiceAction.Next(round - 1)))
            game = game.reduce(PracticeChoiceAction.Next(round))
        }
        assertTrue(game.finished)
        assertEquals(game, game.reduce(PracticeChoiceAction.Next(game.current)))
        assertEquals(game, game.reduce(PracticeChoiceAction.Answer(0, game.current)))
        assertEquals(initial, game.reduce(PracticeChoiceAction.Restart))
    }

    @Test fun theSameItemCanBeNeededOrOptionalDependingOnTheSituation() {
        val questions = PracticeGames.needs().questions
        assertEquals(8, questions.size)
        for (item in listOf(PracticeItem.LANTERN, PracticeItem.CAP, PracticeItem.BUN)) {
            assertEquals(setOf(0, 1), questions.filter { it.item == item }.map { it.correctChoice }.toSet())
        }
        val bySituation = questions.associateBy { it.situation }
        assertEquals(0, bySituation.getValue(NeedWantSituation.NIGHT_WALK).correctChoice)
        assertEquals(1, bySituation.getValue(NeedWantSituation.DAY_WALK).correctChoice)
        assertEquals(0, bySituation.getValue(NeedWantSituation.SUNNY_DAY).correctChoice)
        assertEquals(1, bySituation.getValue(NeedWantSituation.SPARE_CAP).correctChoice)
        assertEquals(0, bySituation.getValue(NeedWantSituation.NO_SNACK).correctChoice)
        assertEquals(1, bySituation.getValue(NeedWantSituation.PACKED_LUNCH).correctChoice)
    }

    @Test fun seededOrdersAreStableCompleteAndNeverStrictlyAlternateAnswers() {
        repeat(100) { seed ->
            val needs = PracticeGames.needs(seed)
            val discounts = PracticeGames.discounts(seed)
            assertEquals(needs, PracticeGames.needs(seed))
            assertEquals(discounts, PracticeGames.discounts(seed))
            assertTrue(PracticeGames.matchesNeeds(needs.questions))
            assertTrue(PracticeGames.matchesDiscounts(discounts.questions))
            assertTrue(PracticeGames.matchesExtraPurchases(PracticeGames.extraPurchases(seed).questions))
            listOf(needs, discounts).forEach { game ->
                assertTrue(game.questions.zipWithNext().any { (a, b) -> a.correctChoice == b.correctChoice })
            }
        }
        assertTrue((0..20).map { PracticeGames.needs(it).questions }.distinct().size > 1)
        assertTrue((0..20).map { PracticeGames.discounts(it).questions }.distinct().size > 1)
        assertTrue((0..20).map { PracticeGames.extraPurchases(it).questions }.distinct().size > 1)
    }

    @Test fun discountSeriesIncludesAnUnchangedPriceBehindAnInflatedLabel() {
        val question = PracticeGames.discounts().questions.single { it.todayPrice == it.yesterdayPrice }
        assertEquals(20, question.todayPrice)
        assertEquals(30, question.crossedOutPrice)
        assertEquals(0, question.correctChoice)
        assertEquals(0, question.saving)
    }

    @Test fun inflatedLabelsCannotTurnEqualOrHigherPricesIntoDiscounts() {
        val original = DiscountQuestion(PracticeItem.LANTERN, 55, 90, 65)
        assertEquals(0, original.correctChoice)
        assertEquals(-10, original.saving)
        assertEquals(0, original.copy(todayPrice = 55).correctChoice)
        assertEquals(1, original.copy(todayPrice = 54).correctChoice)
        assertEquals(1, original.copy(todayPrice = 54, crossedOutPrice = Int.MAX_VALUE).correctChoice)
    }

    @Test fun wrongChoiceIsRecordedOnceAndDoesNotPreventFinishing() {
        val initial = PracticeGames.discounts()
        val wrong = initial.reduce(PracticeChoiceAction.Answer(1 - initial.question.correctChoice, 0))
        assertEquals(false, wrong.lastCorrect)
        assertEquals(0, wrong.correctAnswers)
        assertEquals(wrong, wrong.reduce(PracticeChoiceAction.Answer(1, 0)))
        assertEquals(1, wrong.reduce(PracticeChoiceAction.Next(0)).current)
    }

    @Test fun trapAccountsForDeadlineAndAllCostsWithoutIntegerOverflow() {
        val futureCheaper = FinancialTrapQuestion(TrapScenario.TICKET, 3,
            listOf(FinancialOption(20), FinancialOption(14, waitDays = 1)))
        assertEquals(1, futureCheaper.correctChoice)
        assertEquals(0, futureCheaper.copy(deadlineDays = 0).correctChoice)
        assertEquals(1, FinancialTrapQuestion(TrapScenario.DELIVERY_FEE, 1,
            listOf(FinancialOption(40, 15), FinancialOption(48))).correctChoice)
        assertEquals(1, FinancialTrapQuestion(TrapScenario.DELIVERY_FEE, 1,
            listOf(FinancialOption(Int.MAX_VALUE, 1), FinancialOption(Int.MAX_VALUE))).correctChoice)
    }

    @Test(expected = IllegalArgumentException::class)
    fun trapRejectsAmbiguousChoices() {
        FinancialTrapQuestion(TrapScenario.TICKET, 1,
            listOf(FinancialOption(20), FinancialOption(14, 6)))
    }

    @Test fun extraPurchaseAnswersFollowItemsWhenCardsAreReordered() {
        PracticeGames.extraPurchases().questions.forEach { question ->
            val reordered = question.copy(items = question.items.reversed())
            assertEquals(question.extraItem, reordered.items[reordered.correctChoice])
        }
        assertEquals(listOf(PracticeItem.CANDLES, PracticeItem.SCARF, PracticeItem.BUCKET),
            PracticeGames.extraPurchases().questions.map { it.extraItem })
    }

}
