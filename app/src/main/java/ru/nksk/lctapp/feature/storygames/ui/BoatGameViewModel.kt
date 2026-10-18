package ru.nksk.lctapp.feature.storygames.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.nksk.lctapp.domain.minigame.chapterone.BoatJourney
import ru.nksk.lctapp.domain.minigame.chapterone.BoatPhase
import ru.nksk.lctapp.domain.minigame.chapterone.BoatPlan
import ru.nksk.lctapp.domain.minigame.chapterone.BoatRepair

/** The host must use requestId for idempotency and confirm Paid only after its own write. */
data class BoatRepairRequest(val requestId: String, val rockIndex: Int, val amount: Int)

sealed interface BoatRepairPaymentResult {
    data object Paid : BoatRepairPaymentResult
    data object NotEnoughCoins : BoatRepairPaymentResult
    /** An unconfirmed payment stays retryable under the same request ID. */
    data object Failed : BoatRepairPaymentResult
}

data class BoatGameUiState(
    val phase: BoatPhase,
    val plan: BoatPlan?,
    val reserve: Int,
    val repair: BoatRepair,
    val knotPoints: Int,
    val paymentPending: Boolean = false,
    val paymentRetryRequired: Boolean = false,
    val paymentMessage: String? = null,
)

sealed interface BoatGameAction {
    data class ChoosePlan(val plan: BoatPlan) : BoatGameAction
    data object Launch : BoatGameAction
    data class Steer(val x: Float) : BoatGameAction
    data class Nudge(val direction: Int) : BoatGameAction
    data object RequestPaidRepair : BoatGameAction
    data object BeginKnot : BoatGameAction
    data class ThreadRing(val index: Int) : BoatGameAction
    data object Restart : BoatGameAction
}

@HiltViewModel
class BoatGameViewModel @Inject constructor(private val savedState: SavedStateHandle) : ViewModel() {
    private var journey = restore()
    private var tripId: String = savedState["boat_trip_id"] ?: UUID.randomUUID().toString()
    private var preparedMode: Boolean = savedState["boat_prepared"] ?: false
    private var usePracticeCoins: Boolean = savedState["boat_payment_practice"] ?: true
    private var paymentRetryRequired: Boolean = journey.phase == BoatPhase.DAMAGED &&
        (savedState["boat_payment_retry"] ?: false)
    private var paymentMessage: String? = if (paymentRetryRequired) PAYMENT_RETRY_MESSAGE else null
    private val mutablePaymentRequest = MutableStateFlow(restorePayment())
    val paymentRequest = mutablePaymentRequest.asStateFlow()
    private val mutableUiState = MutableStateFlow(presentation())
    val uiState = mutableUiState.asStateFlow()
    private val mutableMotion = MutableStateFlow(journey)
    val motion = mutableMotion.asStateFlow()
    private var active = false
    private var sailingJob: Job? = null

    fun setStartPrepared(value: Boolean) {
        preparedMode = value
        if (value && journey.phase == BoatPhase.PREPARATION) {
            publish(journey.choosePlan(BoatPlan.RESERVE).launch(), save = true)
            ensureSailing()
        }
    }

    fun onAction(action: BoatGameAction) {
        if (action == BoatGameAction.RequestPaidRepair) {
            requestPaidRepair(external = false)
            return
        }
        if ((mutablePaymentRequest.value != null || paymentRetryRequired) &&
            (action == BoatGameAction.BeginKnot || action == BoatGameAction.Restart)) return
        val next = when (action) {
            is BoatGameAction.ChoosePlan -> journey.choosePlan(action.plan)
            BoatGameAction.Launch -> journey.launch()
            is BoatGameAction.Steer -> journey.steer(action.x)
            is BoatGameAction.Nudge -> journey.steer(journey.targetX + action.direction.coerceIn(-1, 1) * .18f)
            BoatGameAction.RequestPaidRepair -> journey
            BoatGameAction.BeginKnot -> journey.beginKnot()
            is BoatGameAction.ThreadRing -> journey.threadRing(action.index)
            BoatGameAction.Restart -> {
                tripId = UUID.randomUUID().toString()
                paymentMessage = null
                paymentRetryRequired = false
                if (preparedMode) BoatJourney().choosePlan(BoatPlan.RESERVE).launch() else BoatJourney()
            }
        }
        if (action == BoatGameAction.BeginKnot) paymentMessage = null
        val discrete = action !is BoatGameAction.Steer && action !is BoatGameAction.Nudge
        publish(next, save = discrete)
        ensureSailing()
    }

    fun requestPaidRepair(external: Boolean) {
        val rock = journey.activeRock ?: return
        if (journey.phase != BoatPhase.DAMAGED || mutablePaymentRequest.value != null) return
        if (!external && journey.reserve < BoatJourney.REPAIR_PRICE) return
        usePracticeCoins = !external
        paymentMessage = null
        mutablePaymentRequest.value = BoatRepairRequest("$tripId:rock:$rock", rock, BoatJourney.REPAIR_PRICE)
        mutableUiState.value = presentation()
        save()
    }

    internal fun isPracticePayment(request: BoatRepairRequest): Boolean =
        mutablePaymentRequest.value == request && usePracticeCoins

    fun confirmPayment(request: BoatRepairRequest, result: BoatRepairPaymentResult) {
        if (mutablePaymentRequest.value != request || journey.activeRock != request.rockIndex) return
        mutablePaymentRequest.value = null
        when (result) {
            BoatRepairPaymentResult.Paid -> {
                paymentRetryRequired = false
                paymentMessage = null
                publish(journey.confirmPaidRepair(request.rockIndex, usePracticeCoins), save = true)
                ensureSailing()
            }
            BoatRepairPaymentResult.NotEnoughCoins -> {
                paymentRetryRequired = false
                paymentMessage = "На ремонт не хватает монет."
                publish(journey, save = true)
            }
            BoatRepairPaymentResult.Failed -> {
                paymentRetryRequired = true
                paymentMessage = PAYMENT_RETRY_MESSAGE
                publish(journey, save = true)
            }
        }
    }

    fun setScreenActive(value: Boolean) {
        active = value
        if (!value) {
            sailingJob?.cancel()
            sailingJob = null
            save()
        } else ensureSailing()
    }

    private fun ensureSailing() {
        if (!active || !journey.moving) return
        if (sailingJob?.isActive == true) return
        sailingJob = viewModelScope.launch {
            var lastFrame = System.nanoTime()
            while (active && journey.moving) {
                delay(33L)
                val now = System.nanoTime()
                val next = journey.tick((now - lastFrame) / 1_000_000_000f)
                lastFrame = now
                publish(next, save = next.phase != journey.phase)
            }
        }
    }

    private fun publish(next: BoatJourney, save: Boolean) {
        journey = next
        mutableMotion.value = next
        // Frames only invalidate the river drawing and sprite layer, not the whole screen.
        mutableUiState.value = presentation()
        if (save) save()
    }

    private fun presentation() = BoatGameUiState(journey.phase, journey.plan, journey.reserve,
        journey.repair, journey.knotPoints, mutablePaymentRequest.value != null,
        paymentRetryRequired, paymentMessage)

    private fun save() {
        savedState["boat_state_version"] = 2
        savedState["boat_trip_id"] = tripId
        savedState["boat_prepared"] = preparedMode
        savedState["boat_phase"] = journey.phase.name
        savedState["boat_plan"] = journey.plan?.name
        savedState["boat_distance"] = journey.distance
        savedState["boat_x"] = journey.x
        savedState["boat_target"] = journey.targetX
        savedState["boat_repair"] = journey.repair.name
        savedState["boat_knot"] = journey.knotPoints
        savedState["boat_rocks"] = journey.touchedRocks.toIntArray()
        savedState["boat_active_rock"] = journey.activeRock
        savedState["boat_practice_spent"] = journey.practiceCoinsSpent
        savedState["boat_paid_repairs"] = journey.paidRepairs
        savedState["boat_manual_repairs"] = journey.manualRepairs
        savedState["boat_payment_pending"] = mutablePaymentRequest.value != null
        savedState["boat_payment_practice"] = usePracticeCoins
        savedState["boat_payment_retry"] = paymentRetryRequired
    }

    private fun restorePayment(): BoatRepairRequest? {
        val rock = journey.activeRock ?: return null
        if (journey.phase != BoatPhase.DAMAGED || savedState.get<Boolean>("boat_payment_pending") != true) return null
        return BoatRepairRequest("$tripId:rock:$rock", rock, BoatJourney.REPAIR_PRICE)
    }

    private fun restore(): BoatJourney {
        // The old temporary prototype had a compulsory mid-route stop, not a collision.
        if (savedState.get<Int>("boat_state_version") != 2) return BoatJourney()
        val phase = BoatPhase.entries.firstOrNull { it.name == savedState.get<String>("boat_phase") }
            ?: return BoatJourney()
        val plan = BoatPlan.entries.firstOrNull { it.name == savedState.get<String>("boat_plan") }
        if (phase != BoatPhase.PREPARATION && plan == null) return BoatJourney()
        fun number(key: String, default: Float): Float = savedState.get<Float>(key)
            ?.takeIf { it.isFinite() } ?: default
        val rocks = savedState.get<IntArray>("boat_rocks")?.filter { it in BoatJourney.rocks.indices }
            ?.toSet().orEmpty()
        val activeRock = savedState.get<Int>("boat_active_rock")?.takeIf { it in rocks }
        if ((phase == BoatPhase.DAMAGED || phase == BoatPhase.TYING) && activeRock == null) return BoatJourney()
        return BoatJourney(
            phase = phase,
            plan = plan,
            distance = number("boat_distance", 0f).coerceIn(0f, 1f),
            x = number("boat_x", .5f).coerceIn(.18f, .82f),
            targetX = number("boat_target", .5f).coerceIn(.18f, .82f),
            repair = BoatRepair.entries.firstOrNull { it.name == savedState.get<String>("boat_repair") }
                ?: BoatRepair.NONE,
            knotPoints = (savedState.get<Int>("boat_knot") ?: 0).coerceIn(0, 3),
            touchedRocks = rocks,
            activeRock = activeRock,
            practiceCoinsSpent = (savedState.get<Int>("boat_practice_spent") ?: 0).coerceIn(0, 2),
            paidRepairs = (savedState.get<Int>("boat_paid_repairs") ?: 0).coerceIn(0, BoatJourney.rocks.size),
            manualRepairs = (savedState.get<Int>("boat_manual_repairs") ?: 0).coerceIn(0, BoatJourney.rocks.size),
        )
    }

    private companion object {
        const val PAYMENT_RETRY_MESSAGE = "Не удалось проверить оплату. Повтори попытку."
    }
}
