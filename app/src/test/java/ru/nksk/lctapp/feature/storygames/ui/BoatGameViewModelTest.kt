package ru.nksk.lctapp.feature.storygames.ui

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.nksk.lctapp.domain.minigame.chapterone.BoatPhase

class BoatGameViewModelTest {
    @Test fun unconfirmedPaymentKeepsItsIdAndCannotBeBypassedByAnotherChoice() {
        val model = BoatGameViewModel(damagedSave())
        model.requestPaidRepair(external = true)
        val request = requireNotNull(model.paymentRequest.value)
        assertFalse(model.isPracticePayment(request))
        model.confirmPayment(request, BoatRepairPaymentResult.Failed)
        assertTrue(model.uiState.value.paymentRetryRequired)
        model.onAction(BoatGameAction.BeginKnot)
        model.onAction(BoatGameAction.Restart)
        assertEquals(BoatPhase.DAMAGED, model.uiState.value.phase)
        model.requestPaidRepair(external = true)
        assertEquals(request, model.paymentRequest.value)
        model.confirmPayment(request, BoatRepairPaymentResult.Paid)
        assertEquals(BoatPhase.SAILING, model.uiState.value.phase)
        assertFalse(model.uiState.value.paymentRetryRequired)
        assertEquals(0, model.motion.value.practiceCoinsSpent)
    }

    @Test fun pendingHostPaymentSurvivesRecreationWithTheSameId() {
        val savedState = damagedSave()
        val first = BoatGameViewModel(savedState)
        first.requestPaidRepair(external = true)
        val request = requireNotNull(first.paymentRequest.value)
        val restored = BoatGameViewModel(savedState)
        assertEquals(request, restored.paymentRequest.value)
        assertTrue(restored.uiState.value.paymentPending)
        assertFalse(restored.isPracticePayment(request))
        restored.confirmPayment(request, BoatRepairPaymentResult.Paid)
        assertEquals(BoatPhase.SAILING, restored.uiState.value.phase)
    }

    @Test fun aDefiniteLackOfMoneyAllowsTheFreeRepair() {
        val model = BoatGameViewModel(damagedSave())
        model.requestPaidRepair(external = true)
        model.confirmPayment(requireNotNull(model.paymentRequest.value), BoatRepairPaymentResult.NotEnoughCoins)
        assertNull(model.paymentRequest.value)
        assertFalse(model.uiState.value.paymentRetryRequired)
        model.onAction(BoatGameAction.BeginKnot)
        assertEquals(BoatPhase.TYING, model.uiState.value.phase)
    }

    @Test fun anOldPaymentResultCannotRepairANewJourney() {
        val model = BoatGameViewModel(damagedSave())
        model.requestPaidRepair(external = true)
        val request = requireNotNull(model.paymentRequest.value)
        model.confirmPayment(request, BoatRepairPaymentResult.NotEnoughCoins)
        model.onAction(BoatGameAction.Restart)
        model.confirmPayment(request, BoatRepairPaymentResult.Paid)
        assertEquals(BoatPhase.PREPARATION, model.uiState.value.phase)
        assertEquals(0, model.motion.value.paidRepairs)
    }

    @Test fun galleryPaymentOnlyConsumesThePracticeReserve() {
        val model = BoatGameViewModel(damagedSave())
        model.requestPaidRepair(external = false)
        val request = requireNotNull(model.paymentRequest.value)
        assertNotNull(request)
        assertTrue(model.isPracticePayment(request))
        model.confirmPayment(request, BoatRepairPaymentResult.Paid)
        assertEquals(0, model.uiState.value.reserve)
        assertEquals(2, model.motion.value.practiceCoinsSpent)
    }

    @Test fun preparedModeRestartsOnTheRiverWithoutThePracticeShoppingScreen() {
        val model = BoatGameViewModel(SavedStateHandle())
        model.setStartPrepared(true)
        assertEquals(BoatPhase.SAILING, model.uiState.value.phase)
        model.onAction(BoatGameAction.Restart)
        assertEquals(BoatPhase.SAILING, model.uiState.value.phase)
        assertEquals(0f, model.motion.value.distance, 0f)
    }

    private fun damagedSave() = SavedStateHandle(mapOf(
        "boat_state_version" to 2,
        "boat_phase" to "DAMAGED",
        "boat_plan" to "RESERVE",
        "boat_distance" to .115f,
        "boat_x" to .37f,
        "boat_target" to .37f,
        "boat_rocks" to intArrayOf(0),
        "boat_active_rock" to 0,
    ))
}
