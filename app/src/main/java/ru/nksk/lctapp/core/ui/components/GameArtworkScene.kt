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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import kotlinx.coroutines.flow.first

private val LocalGameArtworkScene = staticCompositionLocalOf<ArtworkSceneLoad?> { null }

/**
 * Loads the initial artwork as one group without replacing the screen with a loading page.
 * Backgrounds and controls remain visible; foreground objects and characters reveal together.
 * Children always compose and measure, so Coil can resolve display sizes asynchronously.
 * Use an event/scene identity as [sceneKey], never an animation frame, pet pose or saved revision.
 * Keep navigation and other controls outside this decorative scene when possible.
 */
@Composable
internal fun GameArtworkScene(
    sceneKey: Any?,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    if (LocalInspectionMode.current) {
        Box(modifier, content = content)
        return
    }
    val scene = remember(sceneKey) { ArtworkSceneLoad() }
    LaunchedEffect(scene) {
        snapshotFlow { scene.ready }.first { it }
        scene.reveal()
    }
    Box(modifier.onGloballyPositioned { scene.layoutCommitted() }, propagateMinConstraints = true) {
        CompositionLocalProvider(LocalGameArtworkScene provides scene) {
            content()
        }
    }
    // BoxWithConstraints can register more artwork during measurement, after root SideEffects.
    // The layout callback is the first point where that initial group is fully registered.
}

/** Apply to foreground artwork only, never the screen's surface, background or actions. */
@Composable
internal fun gameArtworkVisibility(): Modifier {
    val scene = LocalGameArtworkScene.current ?: return Modifier
    return Modifier.drawWithContent { if (scene.revealed) drawContent() }
        .then(if (scene.revealed) Modifier else Modifier.clearAndSetSemantics {})
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
    fun layoutCommitted() { committed = true }
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
