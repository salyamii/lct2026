package ru.nksk.lctapp.core.ui.components.tour

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned

data class SpotlightHighlight(val bounds: Rect, val cornerRadius: Float)

internal data class SpotlightStyle(val cornerRadius: Float, val gap: Float)

/** A screen registers geometry only; the caller owns the sequence and its persistence. */
class SpotlightTargets {
    val bounds = mutableStateMapOf<String, Rect>()
    internal val styles = mutableStateMapOf<String, SpotlightStyle>()

    fun highlight(id: String): SpotlightHighlight? {
        val rect = bounds[id] ?: return null
        val style = styles[id] ?: return null
        // Offset both the edge and its corner by the same amount: a uniform outer contour.
        val radius = minOf(style.cornerRadius, rect.width / 2, rect.height / 2)
        return SpotlightHighlight(rect.inflate(style.gap), radius + style.gap)
    }
    val scrollHandlers = mutableStateMapOf<String, (Float) -> Unit>()
    var focusedTarget: String? by mutableStateOf(null)
    var blocking: Boolean by mutableStateOf(false)
    var expandedTarget: String? by mutableStateOf(null)
}

val LocalSpotlightTargets = staticCompositionLocalOf<SpotlightTargets?> { null }

fun Modifier.spotlightTarget(id: String, cornerRadius: Dp = 16.dp, gap: Dp = 0.dp): Modifier = composed {
    val targets = LocalSpotlightTargets.current
    if (targets == null) this else {
        val style = with(LocalDensity.current) { SpotlightStyle(cornerRadius.toPx(), gap.toPx()) }
        SideEffect { targets.styles[id] = style }
        val requester = remember { BringIntoViewRequester() }
        LaunchedEffect(targets.focusedTarget) { if (targets.focusedTarget == id) requester.bringIntoView() }
        DisposableEffect(targets, id) { onDispose { targets.bounds.remove(id); targets.styles.remove(id) } }
        bringIntoViewRequester(requester).onGloballyPositioned { coordinates ->
            val rect = coordinates.boundsInWindow()
            if (rect.width > 0 && rect.height > 0) targets.bounds[id] = rect
            else targets.bounds.remove(id)
        }
    }
}

/** Only inert, explicitly registered tutorial previews can receive forwarded scrolling. */
fun Modifier.spotlightScrollable(id: String, scroll: ScrollState): Modifier = composed {
    val targets = LocalSpotlightTargets.current
    DisposableEffect(targets, id, scroll) {
        targets?.scrollHandlers?.set(id) { delta -> scroll.dispatchRawDelta(-delta) }
        onDispose { targets?.scrollHandlers?.remove(id) }
    }
    this
}
