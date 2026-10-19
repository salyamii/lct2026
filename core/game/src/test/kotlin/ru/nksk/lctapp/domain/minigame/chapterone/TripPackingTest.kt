package ru.nksk.lctapp.domain.minigame.chapterone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripPackingTest {
    @Test fun finishingRequiresBothEssentialsAndAReserve() {
        assertFalse(TripPacking().finish().completed)
        val essentials = TripPacking().toggle(TripSupply.MAP).toggle(TripSupply.LANTERN)
        assertTrue(essentials.ready)
        assertEquals(6, essentials.reserve)
        assertTrue(essentials.finish().completed)
        assertFalse(essentials.toggle(TripSupply.TOY_BOAT).finish().completed)
    }

    @Test fun smallPleasuresCanFitAlongsideEssentialsAndReserve() {
        val packed = TripPacking().toggle(TripSupply.MAP).toggle(TripSupply.LANTERN)
            .toggle(TripSupply.BUN).toggle(TripSupply.KEYCHAIN).finish()
        assertTrue(packed.completed)
        assertEquals(TripPacking.MINIMUM_RESERVE, packed.reserve)
    }

    @Test fun removingAnOptionalPurchaseLetsTheChildReviseTheirPlan() {
        val crowded = TripPacking().toggle(TripSupply.TOY_BOAT).toggle(TripSupply.MAP).toggle(TripSupply.LANTERN)
        assertEquals(1, crowded.reserve)
        assertFalse(crowded.ready)
        assertTrue(crowded.toggle(TripSupply.TOY_BOAT).ready)
    }

    @Test fun overBudgetAndLateInputsCannotChangeTheOrder() {
        val full = TripPacking().toggle(TripSupply.TOY_BOAT).toggle(TripSupply.MAP).toggle(TripSupply.KEYCHAIN)
        assertEquals(full, full.toggle(TripSupply.LANTERN))
        val done = TripPacking().toggle(TripSupply.MAP).toggle(TripSupply.LANTERN).finish()
        assertEquals(done, done.toggle(TripSupply.MAP).finish())
    }

    @Test fun restorationPreservesAcceptedChoicesButRejectsForgedCompletion() {
        val packed = TripPacking().toggle(TripSupply.MAP).toggle(TripSupply.LANTERN)
        assertEquals(packed, TripPacking.restore(listOf("MAP", "LANTERN"), false))
        assertEquals(packed.finish(), TripPacking.restore(listOf("MAP", "LANTERN"), true))
        assertEquals(TripPacking(), TripPacking.restore(listOf("MAP"), true))
        assertEquals(TripPacking(), TripPacking.restore(listOf("MAP", "MAP"), false))
        assertEquals(TripPacking(), TripPacking.restore(listOf("UNKNOWN"), false))
    }
}
