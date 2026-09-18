package ru.nksk.lctapp.feature.tasks.logic

import kotlin.random.Random

/**
 * Чистое состояние игры «Останови стрелку»: маркер ходит по полосе 0..100,
 * у каждого из [ROUNDS] раундов своя зелёная зона. Тап останавливает маркер:
 * попал в зону — награда. Финал после [ROUNDS] остановок.
 */
data class TargetStopState(
    val zoneStart: Int,
    val round: Int = 0,
    val hits: Int = 0,
    val lastHit: Boolean? = null,
) {
    val finished: Boolean get() = round >= ROUNDS

    /** Награда в монетах за текущий результат. */
    val reward: Int get() = hits * REWARD_PER_HIT

    /**
     * Остановка маркера в позиции [position] (0..100). Фиксирует результат
     * раунда и готовит новую зону; переход к следующему раунду — через [next].
     */
    fun stop(position: Int, random: Random = Random.Default): TargetStopState {
        if (finished || lastHit != null) return this
        val clamped = position.coerceIn(0, 100)
        val hit = clamped >= zoneStart && clamped < zoneStart + ZONE_WIDTH
        return TargetStopState(
            zoneStart = random.nextInt(MIN_ZONE_START, MAX_ZONE_START + 1),
            round = round + 1,
            hits = if (hit) hits + 1 else hits,
            lastHit = hit,
        )
    }

    /** Переход к следующему раунду после показа результата. */
    fun next(): TargetStopState {
        if (finished || lastHit == null) return this
        return copy(lastHit = null)
    }

    companion object {
        const val ROUNDS = 5
        const val REWARD_PER_HIT = 2
        const val ZONE_WIDTH = 20
        const val MIN_ZONE_START = 5
        const val MAX_ZONE_START = 75

        fun create(random: Random = Random.Default): TargetStopState =
            TargetStopState(zoneStart = random.nextInt(MIN_ZONE_START, MAX_ZONE_START + 1))
    }
}
