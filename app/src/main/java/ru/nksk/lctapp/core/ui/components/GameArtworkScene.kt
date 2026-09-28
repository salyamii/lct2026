package ru.nksk.lctapp.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import kotlinx.coroutines.flow.first

private val LocalGameArtworkScene = staticCompositionLocalOf<ArtworkSceneLoad?> { null }

/**
 * Reveals the initial background, objects and characters together. Children always compose and
 * measure, so Coil can resolve display sizes and decode asynchronously while the group is hidden.
 * Use an event/scene identity as [sceneKey], never an animation frame, pet pose or saved revision.
 * Keep navigation and other controls outside this decorative scene when possible.
 */
@Composable
internal fun GameArtworkScene(
    sceneKey: Any?,
    modifier: Modifier = Modifier,
    loadingContent: @Composable BoxScope.() -> Unit = { GameLoadingIndicator() },
    content: @Composable BoxScope.() -> Unit,
) {
    if (LocalInspectionMode.current) {
        Box(modifier, content = content)
        return
    }
    val scene = remember(sceneKey) { ArtworkSceneLoad() }
    val visible = scene.revealed
    LaunchedEffect(scene) {
        snapshotFlow { scene.ready }.first { it }
        scene.reveal()
    }
    Box(modifier, propagateMinConstraints = true) {
        CompositionLocalProvider(LocalGameArtworkScene provides scene) {
            Box(Modifier.graphicsLayer { alpha = if (visible) 1f else 0f }.then(
                if (visible) Modifier else Modifier.clearAndSetSemantics {}.pointerInput(scene) {
                    awaitPointerEventScope {
                        while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                },
            ), content = content)
        }
        if (!visible) Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center,
            content = loadingContent)
    }
    // DisposableEffect registrations from this composition are in place before readiness is read.
    SideEffect { scene.compositionCommitted() }
}

/** Resource changes or extra images after the first reveal must not blank the existing scene. */
private class ArtworkSceneLoad {
    private val pending = mutableStateMapOf<Any, Boolean>()
    private var committed by mutableStateOf(false)
    var revealed by mutableStateOf(false)
        private set
    val ready: Boolean get() = committed && pending.values.all { it }

    fun register(token: Any) { pending[token] = false }
    fun report(token: Any, settled: Boolean) { if (token in pending) pending[token] = settled }
    fun remove(token: Any) { pending.remove(token) }
    fun compositionCommitted() { committed = true }
    fun reveal() { revealed = true }
}

/** A terminal decode error releases the barrier too; a broken local asset cannot freeze a screen. */
@Composable
internal fun ReportGameArtworkLoad(requestKey: Any, settled: Boolean) {
    val scene = LocalGameArtworkScene.current ?: return
    val token = remember(scene, requestKey) { Any() }
    DisposableEffect(scene, token) {
        scene.register(token)
        onDispose { scene.remove(token) }
    }
    SideEffect { scene.report(token, settled) }
}
