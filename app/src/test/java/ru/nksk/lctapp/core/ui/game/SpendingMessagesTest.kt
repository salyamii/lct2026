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
        assertEquals("Цена - 7 монет. Не хватает ещё 3.", quote.playerDescription(SpendingKind.WANT))
    }

    @Test fun forbiddenEarningCostDoesNotAdvertiseASpend() {
        val quote = EconomyOperations.quote(EconomyState(BudgetPlan(35, 20, 20, 25)), 5, SpendingKind.EARNING)
        assertNull(quote.playerDescription(SpendingKind.EARNING))
    }

    @Test fun reserveReminderIsAbsentWhenTheActualPaymentDoesNotTouchIt() {
        val economy = EconomyState(BudgetPlan(35, 20, 0, 10))
        val quote = EconomyOperations.quote(economy, 5, SpendingKind.WANT)
        assertNull(quote.reserveSpendingDescription(economy.plan.reserve))
        assertEquals("Возьмём для оплаты 5 монет из денег на желания.",
            quote.playerDescription(SpendingKind.WANT, economy.plan.reserve))
        val emptyReserve = EconomyState(BudgetPlan(35, 20, 0, 0))
        assertNull(EconomyOperations.quote(emptyReserve, 5, SpendingKind.FEEDING).reserveSpendingDescription(0))
    }

    @Test fun mixedPaymentNamesTheReserveOnceAndShowsItsRemainingAllocation() {
        val economy = EconomyState(BudgetPlan(35, 3, 0, 10), savingsBalance = 50)
        val quote = EconomyOperations.quote(economy, 7, SpendingKind.WANT)
        assertEquals("Из запаса: 4. Останется: 6.", quote.reserveSpendingDescription(economy.plan.reserve))
        assertEquals("Всего: 7 монет.\nВозьмём для оплаты 3 монеты из денег на желания.\nИз запаса: 4. Останется: 6.",
            quote.playerDescription(SpendingKind.WANT, economy.plan.reserve))
        assertEquals(50L, economy.savingsBalance)
    }

    @Test fun reserveOnlyPaymentDoesNotRepeatTheSource() {
        val economy = EconomyState(BudgetPlan(35, 0, 0, 10))
        val quote = EconomyOperations.quote(economy, 4, SpendingKind.GENERAL)
        assertEquals("Из запаса: 4. Останется: 6.", quote.playerDescription(SpendingKind.GENERAL, 10))
    }

    @Test fun unaffordableAndBlockedQuotesDoNotPromiseToSpendTheReserve() {
        val economy = EconomyState(BudgetPlan(35, 0, 0, 4))
        val quote = EconomyOperations.quote(economy, 7, SpendingKind.WANT)
        assertNull(quote.reserveSpendingDescription(4))
        assertEquals("Цена - 7 монет. Не хватает ещё 3.", quote.playerDescription(SpendingKind.WANT, 4))
        assertNull(quote.copy(missing = 0, blocked = true).reserveSpendingDescription(4))
    }
}
