package ru.nksk.lctapp.domain.minigame.chapterone

import kotlin.math.abs

/** A self-contained practice trip. These coins never belong to a saved game. */
enum class BoatPhase { PREPARATION, SAILING, DAMAGED, TYING, LANDING, ARRIVED }
enum class BoatPlan { RESERVE, POSTCARD }
enum class BoatRepair { NONE, PAID, MANUAL }

data class BoatRock(val x: Float, val distance: Float)

data class BoatJourney(
    val phase: BoatPhase = BoatPhase.PREPARATION,
    val plan: BoatPlan? = null,
    val distance: Float = 0f,
    val x: Float = .5f,
    val targetX: Float = .5f,
    val touchedRocks: Set<Int> = emptySet(),
    val activeRock: Int? = null,
    val repair: BoatRepair = BoatRepair.NONE,
    val knotPoints: Int = 0,
    val practiceCoinsSpent: Int = 0,
    val paidRepairs: Int = 0,
    val manualRepairs: Int = 0,
) {
    val reserve: Int get() = if (plan == BoatPlan.RESERVE) (2 - practiceCoinsSpent).coerceAtLeast(0) else 0
    val moving: Boolean get() = phase == BoatPhase.SAILING || phase == BoatPhase.LANDING

    fun choosePlan(value: BoatPlan): BoatJourney =
        if (phase == BoatPhase.PREPARATION) copy(plan = value) else this

    fun launch(): BoatJourney =
        if (phase == BoatPhase.PREPARATION && plan != null) copy(phase = BoatPhase.SAILING) else this

    fun steer(value: Float): BoatJourney =
        if (phase == BoatPhase.SAILING && value.isFinite()) copy(targetX = value.coerceIn(.18f, .82f)) else this

    /** Bounded frames cannot skip a rock. The final approach always ends at the pier. */
    fun tick(seconds: Float): BoatJourney {
        if (!moving || !seconds.isFinite() || seconds <= 0f) return this
        val dt = seconds.coerceAtMost(.05f)
        val steeringTarget = if (phase == BoatPhase.LANDING) .5f else targetX
        val nextX = x + (steeringTarget - x).coerceIn(-dt * .85f, dt * .85f)
        val nextDistance = (distance + dt * .038f).coerceAtMost(1f)
        val collision = rocks.indices.firstOrNull { index ->
            index !in touchedRocks && abs(rocks[index].distance - nextDistance) < .018f &&
                abs(rocks[index].x - nextX) < .095f
        }
        return copy(
            phase = when {
                collision != null -> BoatPhase.DAMAGED
                nextDistance >= 1f -> BoatPhase.ARRIVED
                nextDistance >= .94f -> BoatPhase.LANDING
                else -> phase
            },
            x = nextX,
            targetX = steeringTarget,
            distance = nextDistance,
            activeRock = collision,
            knotPoints = if (collision != null) 0 else knotPoints,
            touchedRocks = if (collision != null) touchedRocks + collision else touchedRocks,
        )
    }

    /** External payments must already be committed by the host before this confirmation. */
    fun confirmPaidRepair(rockIndex: Int, spendPracticeCoins: Boolean): BoatJourney = if (
        phase == BoatPhase.DAMAGED && activeRock == rockIndex && rockIndex in rocks.indices &&
        rockIndex in touchedRocks &&
        (!spendPracticeCoins || reserve >= REPAIR_PRICE)
    ) {
        copy(phase = BoatPhase.SAILING, activeRock = null, repair = BoatRepair.PAID,
            paidRepairs = paidRepairs + 1,
            practiceCoinsSpent = practiceCoinsSpent + if (spendPracticeCoins) REPAIR_PRICE else 0)
    } else this

    fun beginKnot(): BoatJourney = if (phase == BoatPhase.DAMAGED) copy(phase = BoatPhase.TYING, knotPoints = 0) else this

    fun threadRing(index: Int): BoatJourney {
        if (phase != BoatPhase.TYING || index != knotPoints || index !in 0..2) return this
        val count = knotPoints + 1
        return copy(
            knotPoints = count,
            phase = if (count == 3) BoatPhase.SAILING else phase,
            activeRock = if (count == 3) null else activeRock,
            repair = if (count == 3) BoatRepair.MANUAL else repair,
            manualRepairs = manualRepairs + if (count == 3) 1 else 0,
        )
    }

    companion object {
        const val ALLOWANCE = 10
        const val TICKET_PRICE = 6
        const val FOOD_PRICE = 2
        const val REPAIR_PRICE = 2
        val rocks = listOf(
            BoatRock(.37f, .12f), BoatRock(.67f, .24f), BoatRock(.47f, .36f),
            BoatRock(.69f, .61f), BoatRock(.32f, .73f), BoatRock(.55f, .86f),
        )
    }
}
