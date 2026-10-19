package ru.nksk.lctapp.domain.minigame.chapterone

import kotlin.math.abs

/** Local work with story objects. No wallet, reward, timing or financial assessment. */
enum class DiscoveryScene { OBSERVATORY, PLATE_HAND, PLATE_SOLUTION, INSCRIPTION, EMBLEM, SIGNAL_POST, FINALE }

data class DiscoveryPoint(val x: Float, val y: Float)

data class ChapterDiscovery(
    val scene: DiscoveryScene,
    val stage: Int = 0,
    val revealed: Set<Int> = emptySet(),
    val placed: Set<Int> = emptySet(),
    val selected: Int? = null,
    val joined: Set<Int> = emptySet(),
    val focus: Float = 0f,
    val reflected: List<Int> = emptyList(),
) {
    val complete: Boolean get() = ChapterDiscoveryGame.valid(this) && when (scene) {
        DiscoveryScene.OBSERVATORY, DiscoveryScene.EMBLEM -> stage == 2
        DiscoveryScene.PLATE_HAND, DiscoveryScene.PLATE_SOLUTION -> revealed == ChapterDiscoveryGame.dust.indices.toSet()
        DiscoveryScene.INSCRIPTION -> revealed == setOf(0, 1, 2)
        DiscoveryScene.SIGNAL_POST -> reflected == listOf(0, 1, 2)
        DiscoveryScene.FINALE -> placed == setOf(0, 1, 2) && joined == ChapterDiscoveryGame.networkEdges.indices.toSet() && selected == null
    }
    val reflectionReady: Boolean get() = scene == DiscoveryScene.SIGNAL_POST &&
        reflected.size < ChapterDiscoveryGame.reflectionTargets.size &&
        abs(focus - ChapterDiscoveryGame.reflectionTargets[reflected.size]) <= .085f
}

sealed interface DiscoveryAction {
    data object OpenDome : DiscoveryAction
    data object RemoveCover : DiscoveryAction
    data class Clean(val x: Float, val y: Float) : DiscoveryAction
    data class Illuminate(val fragment: Int) : DiscoveryAction
    data object FlipEmblem : DiscoveryAction
    data object MatchEmblem : DiscoveryAction
    data class Aim(val value: Float) : DiscoveryAction
    data object CaptureReflection : DiscoveryAction
    data class SelectFinding(val index: Int) : DiscoveryAction
    data class PlaceFinding(val slot: Int) : DiscoveryAction
    data class SelectPoint(val index: Int) : DiscoveryAction
}

object ChapterDiscoveryGame {
    const val SAVE_VERSION = 1
    val dust = listOf(.3f, .5f, .7f).flatMap { y -> listOf(.28f, .5f, .72f).map { x -> DiscoveryPoint(x, y) } }
    val reflectionTargets = listOf(.28f, .72f, .48f)
    val networkPoints = listOf(DiscoveryPoint(.16f, .68f), DiscoveryPoint(.38f, .48f),
        DiscoveryPoint(.60f, .66f), DiscoveryPoint(.78f, .30f), DiscoveryPoint(.35f, .17f))
    val networkEdges = listOf(0 to 1, 1 to 2, 1 to 4, 2 to 3)

    fun reduce(state: ChapterDiscovery, action: DiscoveryAction): ChapterDiscovery {
        if (!valid(state) || state.complete) return state
        return when (action) {
            DiscoveryAction.OpenDome -> if (state.scene == DiscoveryScene.OBSERVATORY && state.stage == 0) state.copy(stage = 1) else state
            DiscoveryAction.RemoveCover -> if (state.scene == DiscoveryScene.OBSERVATORY && state.stage == 1) state.copy(stage = 2) else state
            is DiscoveryAction.Clean -> clean(state, action.x, action.y)
            is DiscoveryAction.Illuminate -> if (state.scene == DiscoveryScene.INSCRIPTION && action.fragment in 0..2)
                state.copy(revealed = state.revealed + action.fragment) else state
            DiscoveryAction.FlipEmblem -> if (state.scene == DiscoveryScene.EMBLEM && state.stage == 0) state.copy(stage = 1) else state
            DiscoveryAction.MatchEmblem -> if (state.scene == DiscoveryScene.EMBLEM && state.stage == 1) state.copy(stage = 2) else state
            is DiscoveryAction.Aim -> if (state.scene == DiscoveryScene.SIGNAL_POST && action.value.isFinite())
                state.copy(focus = action.value.coerceIn(0f, 1f)) else state
            DiscoveryAction.CaptureReflection -> if (state.reflectionReady)
                state.copy(reflected = state.reflected + state.reflected.size, focus = 0f) else state
            is DiscoveryAction.SelectFinding -> if (state.scene == DiscoveryScene.FINALE && state.placed.size < 3 &&
                action.index in 0..2 && action.index !in state.placed) state.copy(selected = action.index) else state
            is DiscoveryAction.PlaceFinding -> if (state.scene == DiscoveryScene.FINALE && state.placed.size < 3 &&
                action.slot in 0..2 && action.slot == state.selected && action.slot !in state.placed)
                state.copy(placed = state.placed + action.slot, selected = null) else state
            is DiscoveryAction.SelectPoint -> connect(state, action.index)
        }
    }

    private fun clean(state: ChapterDiscovery, x: Float, y: Float): ChapterDiscovery {
        if (state.scene !in setOf(DiscoveryScene.PLATE_HAND, DiscoveryScene.PLATE_SOLUTION) ||
            !x.isFinite() || !y.isFinite() || x !in 0f..1f || y !in 0f..1f) return state
        val radius = if (state.scene == DiscoveryScene.PLATE_SOLUTION) .27f else .145f
        val touched = dust.indices.filter { index ->
            val point = dust[index]
            (x - point.x) * (x - point.x) + (y - point.y) * (y - point.y) <= radius * radius
        }
        return state.copy(revealed = state.revealed + touched)
    }

    private fun connect(state: ChapterDiscovery, point: Int): ChapterDiscovery {
        if (state.scene != DiscoveryScene.FINALE || state.placed != setOf(0, 1, 2) || point !in networkPoints.indices) return state
        val previous = state.selected ?: return state.copy(selected = point)
        if (previous == point) return state
        val edge = networkEdges.indexOfFirst { (a, b) -> (a == previous && b == point) || (b == previous && a == point) }
        if (edge < 0) return state.copy(selected = point)
        val joined = state.joined + edge
        return state.copy(joined = joined, selected = if (joined.size == networkEdges.size) null else point)
    }

    /** Reject unrelated fields and impossible graph progress before accepting any completion. */
    fun valid(state: ChapterDiscovery): Boolean {
        if (!state.focus.isFinite() || state.focus !in 0f..1f) return false
        val noGraph = state.placed.isEmpty() && state.joined.isEmpty() && state.selected == null
        val noPost = state.focus == 0f && state.reflected.isEmpty()
        return when (state.scene) {
            DiscoveryScene.OBSERVATORY, DiscoveryScene.EMBLEM -> state.stage in 0..2 && state.revealed.isEmpty() && noGraph && noPost
            DiscoveryScene.PLATE_HAND, DiscoveryScene.PLATE_SOLUTION -> state.stage == 0 && state.revealed.all { it in dust.indices } && noGraph && noPost
            DiscoveryScene.INSCRIPTION -> state.stage == 0 && state.revealed.all { it in 0..2 } && noGraph && noPost
            DiscoveryScene.SIGNAL_POST -> state.stage == 0 && state.revealed.isEmpty() && noGraph &&
                state.reflected.size <= 3 && state.reflected == listOf(0, 1, 2).take(state.reflected.size) &&
                (state.reflected.size < 3 || state.focus == 0f)
            DiscoveryScene.FINALE -> state.stage == 0 && state.revealed.isEmpty() && noPost &&
                state.placed.all { it in 0..2 } && state.joined.all { it in networkEdges.indices } &&
                if (state.placed.size < 3) state.joined.isEmpty() && (state.selected == null || state.selected in (0..2).filterNot { it in state.placed })
                else (state.selected == null || state.selected in networkPoints.indices) && (state.joined.size < networkEdges.size || state.selected == null)
        }
    }

    fun restore(scene: DiscoveryScene, stage: Int = 0, revealed: List<Int> = emptyList(),
        placed: List<Int> = emptyList(), selected: Int? = null, joined: List<Int> = emptyList(),
        focus: Float = 0f, reflected: List<Int> = emptyList(), version: Int = SAVE_VERSION): ChapterDiscovery {
        if (version != SAVE_VERSION || revealed.distinct().size != revealed.size ||
            placed.distinct().size != placed.size || joined.distinct().size != joined.size) return ChapterDiscovery(scene)
        val state = ChapterDiscovery(scene, stage, revealed.toSet(), placed.toSet(), selected, joined.toSet(), focus, reflected.toList())
        return state.takeIf(::valid) ?: ChapterDiscovery(scene)
    }
}
