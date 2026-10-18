package ru.nksk.lctapp.domain.minigame

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test fun needsUseTheAuthoredTripContextRatherThanEveryItemBeingEssential() {
        val questions = PracticeGames.needs().questions.associateBy { it.item }
        assertEquals(8, questions.size)
        assertEquals(0, questions.getValue(PracticeItem.WATER).correctChoice)
        assertEquals(0, questions.getValue(PracticeItem.LANTERN).correctChoice)
        assertEquals(1, questions.getValue(PracticeItem.CAP).correctChoice)
        assertEquals(1, questions.getValue(PracticeItem.KEYCHAIN).correctChoice)
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
        val wrong = initial.reduce(PracticeChoiceAction.Answer(0, 0))
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
        assertEquals(PracticeItem.TEDDY, PracticeGames.extraPurchases().questions[1].extraItem)
    }

    @Test fun budgetRequiresStartAndExplicitCheck() {
        val initial = CoinBalanceState.create()
        assertEquals(37L, initial.spent)
        assertEquals(initial, initial.reduce(CoinBalanceAction.Toggle(PracticeItem.BUN, 0)))
        assertEquals(initial, initial.reduce(CoinBalanceAction.Check(0)))
        val edited = initial.reduce(CoinBalanceAction.Start)
            .reduce(CoinBalanceAction.Toggle(PracticeItem.BUN, 0))
        assertEquals(31L, edited.spent)
        assertNull(edited.verdict)
        assertEquals(edited, edited.reduce(CoinBalanceAction.Next(0)))
        val checked = edited.reduce(CoinBalanceAction.Check(0))
        assertEquals(BalanceVerdict.BALANCED, checked.verdict)
        assertEquals(1, checked.correctAnswers)
        assertEquals(checked, checked.reduce(CoinBalanceAction.Check(0)))
        assertEquals(checked, checked.reduce(CoinBalanceAction.Toggle(PracticeItem.BUN, 0)))
    }

    @Test fun removingEssentialCannotPassEvenWhenMoneyIsEnough() {
        val checked = CoinBalanceState.create().reduce(CoinBalanceAction.Start)
            .reduce(CoinBalanceAction.Toggle(PracticeItem.MEDICINE, 0))
            .reduce(CoinBalanceAction.Check(0))
        assertEquals(32L, checked.spent)
        assertEquals(BalanceVerdict.MISSING_REQUIRED, checked.verdict)
        assertEquals(checked, checked.reduce(CoinBalanceAction.Next(0)))
        val corrected = checked.reduce(CoinBalanceAction.Edit(0))
            .reduce(CoinBalanceAction.Toggle(PracticeItem.MEDICINE, 0))
            .reduce(CoinBalanceAction.Toggle(PracticeItem.BUN, 0))
            .reduce(CoinBalanceAction.Check(0))
        assertEquals(BalanceVerdict.BALANCED, corrected.verdict)
        assertEquals(0, corrected.correctAnswers) // A correction is not a first-try answer.
        assertEquals(1, corrected.reduce(CoinBalanceAction.Next(0)).current)
    }

    @Test fun overspendingCanBeCorrectedAndAnExactBudgetPasses() {
        val initial = CoinBalanceState.create().reduce(CoinBalanceAction.Start)
        val checked = initial.reduce(CoinBalanceAction.Check(0))
        assertEquals(BalanceVerdict.OVERSPENT, checked.verdict)
        assertEquals(checked, checked.reduce(CoinBalanceAction.Toggle(PracticeItem.CANDY, 0)))
        val corrected = checked.reduce(CoinBalanceAction.Edit(0))
            .reduce(CoinBalanceAction.Toggle(PracticeItem.CANDY, 0))
            .reduce(CoinBalanceAction.Check(0))
        assertEquals(BalanceVerdict.BALANCED, corrected.verdict)
        val exact = CoinBalanceState(listOf(CoinBalanceRound(17, initial.round.expenses)), started = true,
            removed = initial.round.expenses.filterNot { it.required }.map { it.item }.toSet())
        assertEquals(0L, exact.remainder)
        assertEquals(BalanceVerdict.BALANCED, exact.reduce(CoinBalanceAction.Check(0)).verdict)
    }

    @Test fun budgetRestartsAndIgnoresActionsFromPreviousRound() {
        val initial = CoinBalanceState.create()
        var state = initial.reduce(CoinBalanceAction.Start)
        repeat(initial.rounds.size) { round ->
            state.round.expenses.filterNot { it.required }.forEach {
                state = state.reduce(CoinBalanceAction.Toggle(it.item, round))
            }
            state = state.reduce(CoinBalanceAction.Check(round)).reduce(CoinBalanceAction.Next(round))
            assertEquals(state, state.reduce(CoinBalanceAction.Toggle(PracticeItem.BUN, round)))
            assertEquals(state, state.reduce(CoinBalanceAction.Next(round)))
        }
        assertTrue(state.finished)
        assertEquals(3, state.correctAnswers)
        assertEquals(initial, state.reduce(CoinBalanceAction.Restart))
    }
}
