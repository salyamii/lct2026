package ru.nksk.lctapp.domain.minigame

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BalanceStateTest {
    private val expenses = listOf(
        BalanceExpense("хлеб", 10, essential = true),
        BalanceExpense("сладости", 8, essential = false),
        BalanceExpense("игрушка", 7, essential = false),
    )

    private fun state(income: Int = 20) = BalanceState(income = listOf(income), expenses = listOf(expenses))

    @Test
    fun create_roundsAreSolvableAndOverloaded() {
        val created = BalanceState.create(random = Random(13))
        assertEquals(BalanceState.ROUNDS, created.income.size)
        created.income.zip(created.expenses).forEach { (money, week) ->
            // Обязательные траты всегда влезают в доход.
            assertTrue(week.filter { it.essential }.sumOf { it.amount } <= money)
            // Без отказов расходы перевешивают доход.
            assertTrue(week.sumOf { it.amount } > money)
        }
    }

    @Test
    fun confirm_balancedWithEssentials_countsCorrect() {
        val balanced = state().copy(removed = setOf(1, 2))
        val confirmed = balanced.confirm()
        assertEquals(true, confirmed.lastCorrect)
        assertEquals(1, confirmed.correctRounds)
    }

    @Test
    fun confirm_droppedEssential_countsIncorrect() {
        val dropped = state().copy(removed = setOf(0, 1))
        assertEquals(false, dropped.confirm().lastCorrect)
    }

    @Test
    fun confirm_overBudget_countsIncorrect() {
        val overBudget = state().copy(removed = emptySet())
        assertTrue(!overBudget.balanced)
        assertEquals(false, overBudget.confirm().lastCorrect)
    }

    @Test
    fun next_resetsRemovedAndAdvances() {
        var moved = state(income = 20).copy(removed = setOf(1))
        moved = moved.confirm().next()
        assertEquals(1, moved.current)
        assertTrue(moved.removed.isEmpty())
        assertFalse(moved.finished)
    }

    @Test
    fun finishedParty_producesScore() {
        var moved: BalanceState = BalanceState.create()
        repeat(BalanceState.ROUNDS) {
            moved = moved.copy(removed = moved.weekExpenses.indices.filter { !moved.weekExpenses[it].essential }.toSet())
            moved = moved.confirm().next()
        }
        assertTrue(moved.finished)
        assertNotNull(DeedGameScore.fromBalance(moved))
    }
}
