package ru.nksk.lctapp.domain.minigame.chapterone

import kotlin.math.abs

enum class StrapPhase { ALIGN, STITCH, TIGHTEN, DONE }

data class StrapPoint(val x: Float, val y: Float)

/** Temporary repair practice. It cannot repair equipment or spend coins in the saved world. */
data class StrapRepair(
    val aligned: Boolean = false,
    val stitchedPoints: Int = 0,
    val knotTightened: Boolean = false,
) {
    init {
        require(stitchedPoints in 0..STITCH_COUNT)
        require(aligned || (stitchedPoints == 0 && !knotTightened))
        require(!knotTightened || stitchedPoints == STITCH_COUNT)
    }

    val phase: StrapPhase get() = when {
        !aligned -> StrapPhase.ALIGN
        stitchedPoints < STITCH_COUNT -> StrapPhase.STITCH
        !knotTightened -> StrapPhase.TIGHTEN
        else -> StrapPhase.DONE
    }

    fun alignAt(dx: Float, dy: Float): StrapRepair {
        if (phase != StrapPhase.ALIGN || !dx.isFinite() || !dy.isFinite()) return this
        return if (abs(dx) <= .14f && abs(dy - ALIGNED_Y) <= .10f) align() else this
    }

    /** Button alternative uses the same guarded transition as a successful drag. */
    fun align(): StrapRepair = if (phase == StrapPhase.ALIGN) copy(aligned = true) else this

    fun stitch(pointIndex: Int): StrapRepair =
        if (phase == StrapPhase.STITCH && pointIndex == stitchedPoints) {
            copy(stitchedPoints = stitchedPoints + 1)
        } else this

    /** The geometry shares the illustration's 2:3 canvas; a quick stroke may cross a target. */
    fun trace(from: StrapPoint, to: StrapPoint): StrapRepair {
        if (phase != StrapPhase.STITCH || !from.finite() || !to.finite()) return this
        return if (distanceSquaredToSegment(points[stitchedPoints], from, to) <= TRACE_TOLERANCE * TRACE_TOLERANCE) {
            stitch(stitchedPoints)
        } else this
    }

    fun pull(distance: Float): StrapRepair =
        if (distance.isFinite() && distance >= .13f) tighten() else this

    fun tighten(): StrapRepair = if (phase == StrapPhase.TIGHTEN) copy(knotTightened = true) else this

    companion object {
        const val STITCH_COUNT = 5
        const val ALIGNED_Y = -.23f
        private const val TRACE_TOLERANCE = .105f
        val points = listOf(
            StrapPoint(.40f, .39f), StrapPoint(.60f, .47f),
            StrapPoint(.40f, .47f), StrapPoint(.60f, .39f), StrapPoint(.50f, .43f),
        )

        fun restore(aligned: Boolean, stitchedPoints: Int, knotTightened: Boolean): StrapRepair {
            if (stitchedPoints !in 0..STITCH_COUNT ||
                (!aligned && (stitchedPoints != 0 || knotTightened)) ||
                (knotTightened && stitchedPoints != STITCH_COUNT)
            ) return StrapRepair()
            return StrapRepair(aligned, stitchedPoints, knotTightened)
        }
    }
}

private fun StrapPoint.finite() = x.isFinite() && y.isFinite()

private fun distanceSquaredToSegment(point: StrapPoint, from: StrapPoint, to: StrapPoint): Float {
    val vx = to.x - from.x
    val vy = (to.y - from.y) * 1.5f
    val px = point.x - from.x
    val py = (point.y - from.y) * 1.5f
    val length = vx * vx + vy * vy
    val t = if (length > 0f) ((px * vx + py * vy) / length).coerceIn(0f, 1f) else 0f
    val dx = px - vx * t
    val dy = py - vy * t
    return dx * dx + dy * dy
}
