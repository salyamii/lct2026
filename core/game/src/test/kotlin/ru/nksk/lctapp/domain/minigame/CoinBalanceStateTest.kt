package ru.nksk.lctapp.domain.minigame

import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.domain.minigame.PracticeItem.*

class CoinBalanceStateTest {
    @Test fun budgetRequiresStartAndExplicitCheck() {
        val initial = CoinBalanceState.create()
        assertEquals(38L, initial.spent)
        assertEquals(initial, initial.reduce(CoinBalanceAction.Toggle(BUN, 0)))
        assertEquals(initial, initial.reduce(CoinBalanceAction.Check(0)))
        val edited = initial.reduce(CoinBalanceAction.Start).select(BREAD, WATER, RINGS)
        assertEquals(18L, edited.spent)
        assertNull(edited.verdict)
        assertEquals(edited, edited.reduce(CoinBalanceAction.Next(0)))
        val checked = edited.reduce(CoinBalanceAction.Check(0))
        assertEquals(BalanceVerdict.BALANCED, checked.verdict)
        assertEquals(1, checked.correctAnswers)
        assertEquals(checked, checked.reduce(CoinBalanceAction.Check(0)))
        assertEquals(checked, checked.reduce(CoinBalanceAction.Toggle(BUN, 0)))
    }

    @Test fun picnicAcceptsSeveralBasketsIncludingAnExactBudgetAndATreat() {
        val initial = CoinBalanceState.create().reduce(CoinBalanceAction.Start)
        listOf(listOf(BREAD, WATER, RINGS), listOf(BUN, WATER, RINGS),
            listOf(BUN, WATER, RINGS, CANDY)).forEach { basket ->
            assertEquals(BalanceVerdict.BALANCED,
                initial.select(*basket.toTypedArray()).reduce(CoinBalanceAction.Check(0)).verdict)
        }
        assertEquals(0L, initial.select(BUN, WATER, RINGS, CANDY).remainder)
    }

    @Test fun picnicNeedsFoodDrinkAndExactlyOneGameEvenWithMoneyLeft() {
        val initial = CoinBalanceState.create().reduce(CoinBalanceAction.Start)
        listOf(listOf(BREAD, WATER), listOf(WATER, RINGS, CANDY), listOf(BREAD, RINGS))
            .forEach { basket ->
                val checked = initial.select(*basket.toTypedArray()).reduce(CoinBalanceAction.Check(0))
                assertTrue(checked.remainder >= 0)
                assertEquals(BalanceVerdict.INCOMPLETE_PLAN, checked.verdict)
                assertEquals(checked, checked.reduce(CoinBalanceAction.Next(0)))
            }
        val largerBudget = CoinBalanceState(listOf(initial.round.copy(income = 40)), started = true)
            .select(BREAD, WATER, RINGS, TOY_BOAT).reduce(CoinBalanceAction.Check(0))
        assertEquals(BalanceVerdict.INCOMPLETE_PLAN, largerBudget.verdict)
        assertEquals(listOf(BalanceNeed.ONE_GAME), largerBudget.unmetNeeds)
    }

    @Test fun removingEveryGameFailsAndCanBeCorrectedWithoutFirstTryCredit() {
        val checked = CoinBalanceState.create().reduce(CoinBalanceAction.Start)
            .select(BREAD, WATER).reduce(CoinBalanceAction.Check(0))
        val corrected = checked.reduce(CoinBalanceAction.Edit(0))
            .reduce(CoinBalanceAction.Toggle(RINGS, 0)).reduce(CoinBalanceAction.Check(0))
        assertEquals(BalanceVerdict.BALANCED, corrected.verdict)
        assertEquals(0, corrected.correctAnswers)
        assertEquals(1, corrected.reduce(CoinBalanceAction.Next(0)).current)
    }

    @Test fun satisfyingTheTaskIsNotEnoughWhenTheBasketIsTooExpensive() {
        val checked = CoinBalanceState.create().reduce(CoinBalanceAction.Start)
            .select(BREAD, WATER, TOY_BOAT).reduce(CoinBalanceAction.Check(0))
        assertEquals(22L, checked.spent)
        assertEquals(BalanceVerdict.OVERSPENT, checked.verdict)
        val corrected = checked.reduce(CoinBalanceAction.Edit(0)).select(BREAD, WATER, RINGS)
            .reduce(CoinBalanceAction.Check(0))
        assertEquals(BalanceVerdict.BALANCED, corrected.verdict)
        assertEquals(0, corrected.correctAnswers)
    }

    @Test fun everySituationHasItsOwnRequirementsAndSolvableBudget() {
        val initial = CoinBalanceState.create()
        assertEquals(listOf(BalanceScenario.PICNIC, BalanceScenario.NIGHT_WALK, BalanceScenario.WORKBENCH),
            initial.rounds.map { it.scenario })
        assertEquals(listOf(20, 22, 15), initial.rounds.map { it.income })
        val night = initial.copy(current = 1, started = true)
        for (food in listOf(BREAD, BUN)) {
            assertEquals(BalanceVerdict.BALANCED,
                night.select(LANTERN, SCARF, food).reduce(CoinBalanceAction.Check(1)).verdict)
        }
        assertEquals(BalanceVerdict.INCOMPLETE_PLAN,
            night.select(LANTERN, BUN).reduce(CoinBalanceAction.Check(1)).verdict)
        val workbench = initial.copy(current = 2, started = true)
        assertEquals(BalanceVerdict.BALANCED,
            workbench.select(PLANK, NAILS).reduce(CoinBalanceAction.Check(2)).verdict)
        assertEquals(BalanceVerdict.INCOMPLETE_PLAN,
            workbench.select(PLANK, HAMMER).reduce(CoinBalanceAction.Check(2)).verdict)
    }

    @Test fun budgetRestartsAndIgnoresActionsFromPreviousRound() {
        val initial = CoinBalanceState.create()
        var state = initial.reduce(CoinBalanceAction.Start)
        listOf(listOf(BREAD, WATER, RINGS), listOf(LANTERN, SCARF, BUN), listOf(PLANK, NAILS))
            .forEachIndexed { round, basket ->
                state = state.select(*basket.toTypedArray()).reduce(CoinBalanceAction.Check(round))
                    .reduce(CoinBalanceAction.Next(round))
                assertEquals(state, state.reduce(CoinBalanceAction.Toggle(BUN, round)))
                assertEquals(state, state.reduce(CoinBalanceAction.Next(round)))
            }
        assertTrue(state.finished)
        assertEquals(3, state.correctAnswers)
        assertEquals(initial, state.reduce(CoinBalanceAction.Restart))
    }

    private fun CoinBalanceState.select(vararg kept: PracticeItem): CoinBalanceState =
        round.expenses.fold(this) { state, expense ->
            if ((expense.item in kept) == (expense.item in state.removed))
                state.reduce(CoinBalanceAction.Toggle(expense.item, state.current)) else state
        }
}
