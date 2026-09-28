package ru.nksk.lctapp.core.ui.game

import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.domain.economy.*

class SpendingMessagesTest {
    @Test fun fallbackStillUsesAvailableMoneyAndKeepsTheBankSeparate() {
        val quote = EconomyOperations.quote(EconomyState(BudgetPlan(35, 0, 20, 8)), 5, SpendingKind.WANT)
        val text = checkNotNull(quote.playerDescription(SpendingKind.WANT))
        assertEquals("Возьмём для оплаты 5 монет из запаса.", text)
    }

    @Test fun insufficientQuoteDoesNotPromisePartialPayment() {
        val quote = EconomyOperations.quote(EconomyState(BudgetPlan(35, 20, 2, 25), savingsBalance = 2), 10, SpendingKind.GOAL)
        assertEquals("Не хватает 8 монет. Деньги не будут списаны.", quote.playerDescription(SpendingKind.GOAL))
    }

    @Test fun ordinaryPaymentDoesNotWarnAboutPlanning() {
        val quote = EconomyOperations.quote(EconomyState(BudgetPlan(35, 20, 20, 25)), 5, SpendingKind.FEEDING)
        assertEquals("Возьмём для оплаты 5 монет из денег на необходимое.", quote.playerDescription(SpendingKind.FEEDING))
    }

    @Test fun unaffordableOfferStillShowsItsPriceWithoutPromisingAPartialPayment() {
        val quote = EconomyOperations.quote(EconomyState(BudgetPlan(0, 4, 0, 0)), 7, SpendingKind.WANT)
        assertEquals("Цена — 7 монет. Не хватает ещё 3.", quote.playerDescription(SpendingKind.WANT))
    }

    @Test fun forbiddenEarningCostDoesNotAdvertiseASpend() {
        val quote = EconomyOperations.quote(EconomyState(BudgetPlan(35, 20, 20, 25)), 5, SpendingKind.EARNING)
        assertNull(quote.playerDescription(SpendingKind.EARNING))
    }
}
