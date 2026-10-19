package ru.nksk.lctapp.core.ui.game

import org.junit.Assert.assertEquals
import org.junit.Test

class PurchaseActionLabelTest {
    @Test fun actualChargeReplacesTheLegacyPriceWithoutDuplicatingIt() {
        assertEquals("Купить за 25 монет", "Купить · 5".asPurchaseActionLabel(25))
        assertEquals("Купить за 25 монет", "Купить".asPurchaseActionLabel(25))
    }

    @Test fun paidActivityKeepsItsActionAndShowsThePrice() {
        assertEquals("Сыграть за 7 монет", "Сыграть".asPurchaseActionLabel(7))
    }
}
