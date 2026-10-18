package ru.nksk.lctapp.domain.minigame.chapterone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BoatJourneyTest {
    @Test fun tripNeedsAPlanAndPlanCannotChangeAfterLaunch() {
        assertEquals(BoatPhase.PREPARATION, BoatJourney().launch().phase)
        val sailing = BoatJourney().choosePlan(BoatPlan.RESERVE).launch()
        assertEquals(sailing, sailing.choosePlan(BoatPlan.POSTCARD))
    }

    @Test fun aRockStopsTheJourneyWithoutAutomaticallySpendingCoins() {
        val hit = hitFirstRock()
        assertEquals(BoatPhase.DAMAGED, hit.phase)
        assertEquals(0, hit.activeRock)
        assertEquals(setOf(0), hit.touchedRocks)
        assertEquals(2, hit.reserve)
        assertEquals(hit, hit.tick(10f))
        assertEquals(hit, hit.steer(.8f))
    }

    @Test fun theFormerMidpointStopDoesNotExist() {
        val sailing = BoatJourney(phase = BoatPhase.SAILING, plan = BoatPlan.POSTCARD, distance = .479f)
        val after = sailing.tick(.05f)
        assertEquals(BoatPhase.SAILING, after.phase)
        assertTrue(after.distance > .48f)
    }

    @Test fun largeFrameGapsCannotSkipRocks() {
        val sailing = BoatJourney(phase = BoatPhase.SAILING, plan = BoatPlan.RESERVE,
            x = .37f, targetX = .37f, distance = .09f)
        val bounded = sailing.tick(20f)
        assertTrue(bounded.distance - sailing.distance < .002f)
        assertEquals(sailing, sailing.tick(Float.NaN))
        var advanced = bounded
        repeat(20) { advanced = advanced.tick(.05f) }
        assertEquals(BoatPhase.DAMAGED, advanced.phase)
    }

    @Test fun practiceRepairUsesTheReserveOnceAndOnlyForTheCurrentRock() {
        val stopped = hitFirstRock()
        assertEquals(stopped, stopped.confirmPaidRepair(1, spendPracticeCoins = true))
        val paid = stopped.confirmPaidRepair(0, spendPracticeCoins = true)
        assertEquals(BoatPhase.SAILING, paid.phase)
        assertEquals(0, paid.reserve)
        assertEquals(BoatRepair.PAID, paid.repair)
        assertEquals(1, paid.paidRepairs)
        assertNull(paid.activeRock)
        assertEquals(paid, paid.confirmPaidRepair(0, spendPracticeCoins = true))
        val withoutReserve = stopped.copy(plan = BoatPlan.POSTCARD)
        assertEquals(withoutReserve, withoutReserve.confirmPaidRepair(0, spendPracticeCoins = true))
    }

    @Test fun hostPaymentConfirmationDoesNotSpendPracticeMoney() {
        val stopped = hitFirstRock().copy(plan = BoatPlan.POSTCARD)
        val repaired = stopped.confirmPaidRepair(0, spendPracticeCoins = false)
        assertEquals(BoatPhase.SAILING, repaired.phase)
        assertEquals(0, repaired.practiceCoinsSpent)
        assertEquals(1, repaired.paidRepairs)
    }

    @Test fun freeRepairRequiresThreeOrderedRingsAndWorksWithoutReserve() {
        val knot = hitFirstRock().copy(plan = BoatPlan.POSTCARD).beginKnot()
        assertEquals(knot, knot.threadRing(2))
        val first = knot.threadRing(0)
        assertEquals(first, first.threadRing(0))
        val finished = first.threadRing(1).threadRing(2)
        assertEquals(BoatPhase.SAILING, finished.phase)
        assertEquals(BoatRepair.MANUAL, finished.repair)
        assertEquals(1, finished.manualRepairs)
        assertEquals(0, finished.reserve)
    }

    @Test fun repairingContinuesFromTheSamePlaceAndTheSameRockDoesNotHitAgain() {
        val hit = hitFirstRock()
        val repaired = hit.beginKnot().threadRing(0).threadRing(1).threadRing(2)
        assertEquals(hit.distance, repaired.distance, 0f)
        assertEquals(hit.x, repaired.x, 0f)
        assertEquals(BoatPhase.SAILING, repaired.tick(.05f).phase)
    }

    @Test fun aLaterRockCanStopTheBoatAgainAndStartsANewManualRepair() {
        val repaired = hitFirstRock().beginKnot().threadRing(0).threadRing(1).threadRing(2)
        val later = repaired.copy(x = .67f, targetX = .67f, distance = .23f).tick(.03f)
        assertEquals(BoatPhase.DAMAGED, later.phase)
        assertEquals(1, later.activeRock)
        assertEquals(0, later.knotPoints)
        assertEquals(later, later.confirmPaidRepair(0, spendPracticeCoins = false))
        assertEquals(BoatPhase.SAILING, later.beginKnot().threadRing(0).threadRing(1).threadRing(2).phase)
    }

    @Test fun theFullRouteFinishesWithManualRepairs() {
        var journey = BoatJourney().choosePlan(BoatPlan.POSTCARD).launch()
        repeat(2000) {
            journey = when (journey.phase) {
                BoatPhase.DAMAGED -> journey.beginKnot().threadRing(0).threadRing(1).threadRing(2)
                else -> journey.tick(.05f)
            }
        }
        assertEquals(BoatPhase.ARRIVED, journey.phase)
        assertEquals(1f, journey.distance, 0f)
        assertEquals(0, journey.reserve)
    }

    @Test fun reachingTheEndAlwaysFinishesInsteadOfWaitingForSidewaysSteering() {
        val landing = BoatJourney(phase = BoatPhase.LANDING, plan = BoatPlan.RESERVE,
            distance = .999f, x = .8f, targetX = .8f)
        val arrived = landing.tick(.05f)
        assertEquals(BoatPhase.ARRIVED, arrived.phase)
        assertEquals(1f, arrived.distance, 0f)
        assertEquals(.5f, arrived.targetX, 0f)
        assertEquals(arrived, arrived.tick(.05f))
    }

    private fun hitFirstRock() = BoatJourney(phase = BoatPhase.SAILING, plan = BoatPlan.RESERVE,
        x = .37f, targetX = .37f, distance = .115f).tick(.03f)
}
