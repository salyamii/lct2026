package ru.nksk.lctapp.core.ui.game

import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.domain.economy.*

class SpendingMessagesTest {
    @Test fun fallbackIsExplainedEvenWhenTheFirstArticleIsEmpty() {
        val quote = EconomyOperations.quote(EconomyState(BudgetPlan(35, 0, 20, 8)), 5, SpendingKind.WANT)
        val text = checkNotNull(quote.playerDescription(SpendingKind.WANT))
        assertTrue(text.contains("Запас: 5"))
        assertTrue(text.contains("других статей"))
        assertFalse(text.contains("Коплю: 20"))
    }

    @Test fun insufficientQuoteDoesNotPromisePartialPayment() {
        val quote = EconomyOperations.quote(EconomyState(BudgetPlan(35, 20, 2, 25)), 10, SpendingKind.GOAL)
        assertEquals("Не хватает 8 монет. Деньги не будут списаны.", quote.playerDescription(SpendingKind.GOAL))
    }

    @Test fun ordinaryPaymentDoesNotWarnAboutPlanning() {
        val quote = EconomyOperations.quote(EconomyState(BudgetPlan(35, 20, 20, 25)), 5, SpendingKind.FEEDING)
        assertEquals("Спишется: Нужно: 5.", quote.playerDescription(SpendingKind.FEEDING))
    }

    @Test fun forbiddenEarningCostDoesNotAdvertiseASpend() {
        val quote = EconomyOperations.quote(EconomyState(BudgetPlan(35, 20, 20, 25)), 5, SpendingKind.EARNING)
        assertNull(quote.playerDescription(SpendingKind.EARNING))
    }
}
