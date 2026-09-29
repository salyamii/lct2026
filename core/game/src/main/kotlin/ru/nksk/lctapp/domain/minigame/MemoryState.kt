package ru.nksk.lctapp.domain.minigame

import kotlin.random.Random

/** Пара открытых карт, ожидающая сверки. */
data class PendingPair(val first: Int, val second: Int)

/**
 * Чистое состояние игры «Найди пару»: [faces] хранит id картинки для каждой карты,
 * одинаковые id встречаются ровно дважды. Все переходы возвращают новое состояние.
 */
data class MemoryState(
    val faces: List<Int>,
    val faceUp: Set<Int> = emptySet(),
    val matched: Set<Int> = emptySet(),
    val pending: PendingPair? = null,
    val moves: Int = 0,
    /** Positions revealed in this board, including the current face-up cards. */
    val seen: Set<Int> = emptySet(),
    /** Mismatches where the first card's matching position was already known. */
    val recallMistakes: Int = 0,
) {
    val won: Boolean get() = matched.size == faces.size

    /**
     * Открыть карту. Пока открыта неразобранная пара, клики игнорируются -
     * UI должен сначала вызвать [resolvePending].
     */
    fun tap(index: Int): MemoryState {
        if (won || pending != null) return this
        if (index !in faces.indices) return this
        if (index in faceUp || index in matched) return this
        val open = faceUp.singleOrNull()
        return if (open == null) {
            copy(faceUp = faceUp + index, seen = seen + index)
        } else {
            // Only information revealed before this tap can make it a recall mistake.
            // A known mate of the second card does not retroactively penalize discovering it.
            val missedKnownPair = faces[open] != faces[index] && seen.any { known ->
                known != open && known !in matched && known in faces.indices && faces[known] == faces[open]
            }
            copy(faceUp = faceUp + index, pending = PendingPair(open, index), moves = moves + 1,
                seen = seen + index, recallMistakes = recallMistakes + if (missedKnownPair) 1 else 0)
        }
    }

    /** Сверить открытую пару. При совпадении карты остаются открытыми навсегда. */
    fun resolvePending(): MemoryState {
        val pair = pending ?: return this
        val matchedPair = faces[pair.first] == faces[pair.second]
        return copy(
            faceUp = faceUp - pair.first - pair.second,
            matched = if (matchedPair) matched + pair.first + pair.second else matched,
            pending = null,
        )
    }

    companion object {
        /** Раскладывает [pairs] пар карт рубашкой вверх, перемешивая [random]. */
        fun deal(pairs: Int = PAIRS, random: Random = Random.Default): MemoryState {
            require(pairs > 0) { "pairs must be positive" }
            val faces = List(pairs) { it }.let { it + it }.shuffled(random)
            return MemoryState(faces = faces)
        }

        const val PAIRS = 8
        const val REWARD = 10
    }
}
