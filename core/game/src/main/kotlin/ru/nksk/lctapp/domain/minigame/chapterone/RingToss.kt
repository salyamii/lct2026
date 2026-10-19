package ru.nksk.lctapp.domain.minigame.chapterone

/** Coordinates share the portrait board used by the fair's ring-toss screen. */
data class RingPoint(val x: Float, val y: Float)

data class RingLanding(val point: RingPoint) {
    init {
        require(point.x.isFinite() && point.y.isFinite())
        require(point.x in .06f.. .94f && point.y in .12f.. .86f)
    }

    val pegIndex: Int? get() = RingToss.pegs.indexOfFirst { peg ->
        val dx = (point.x - peg.x) / .075f
        val dy = (point.y - peg.y) / .055f
        dx * dx + dy * dy <= 1f
    }.takeIf { it >= 0 }
}

/** A local five-ring party. Hits never determine its price, recovery or mood. */
data class RingToss(val throws: List<RingLanding> = emptyList()) {
    init { require(throws.size <= THROW_COUNT) }

    val hits: Int get() = throws.count { it.pegIndex != null }
    val complete: Boolean get() = throws.size == THROW_COUNT

    /** Pull back from the starting ring, then release. Very short gestures do not spend a ring. */
    fun pull(dx: Float, dy: Float): RingToss {
        if (complete || !dx.isFinite() || !dy.isFinite() || dy < MINIMUM_PULL) return this
        return copy(throws = throws + RingLanding(landingForPull(dx, dy)))
    }

    /** Tap/assistive alternative. It follows the same one-ring-at-a-time transition. */
    fun throwAtPeg(index: Int): RingToss =
        if (complete || index !in pegs.indices) this
        else copy(throws = throws + RingLanding(pegs[index]))

    fun save(): FloatArray = floatArrayOf(SAVE_VERSION.toFloat()) +
        throws.flatMap { listOf(it.point.x, it.point.y) }.toFloatArray()

    companion object {
        const val THROW_COUNT = 5
        const val MINIMUM_PULL = .035f
        private const val SAVE_VERSION = 1
        val start = RingPoint(.50f, .76f)
        val pegs = listOf(RingPoint(.27f, .39f), RingPoint(.50f, .29f), RingPoint(.73f, .39f))

        fun landingForPull(dx: Float, dy: Float): RingPoint = RingPoint(
            (start.x - dx.coerceIn(-.20f, .20f) * 2.7f).coerceIn(.06f, .94f),
            (start.y - dy.coerceIn(0f, .24f) * 2.5f).coerceIn(.12f, .86f),
        )

        fun restore(saved: FloatArray?): RingToss {
            if (saved == null || saved.isEmpty() || saved[0] != SAVE_VERSION.toFloat() ||
                saved.size % 2 != 1 || saved.size > 1 + THROW_COUNT * 2 || saved.any { !it.isFinite() }
            ) return RingToss()
            val points = saved.drop(1).chunked(2).map { RingPoint(it[0], it[1]) }
            if (points.any { it.x !in .06f.. .94f || it.y !in .12f.. .86f }) return RingToss()
            return RingToss(points.map(::RingLanding))
        }
    }
}
