package ru.nksk.lctapp.core.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import ru.nksk.lctapp.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** A quiet breath and weight shift around the feet, preserving the original transparent canvas. */
@Composable
internal fun MovingNpcArtwork(
    @DrawableRes artwork: Int,
    description: String?,
    modifier: Modifier = Modifier,
) {
    val contact = npcArtworkContact(artwork)
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val active = contact != null && lifecycle.isAtLeast(Lifecycle.State.RESUMED) && !LocalInspectionMode.current
    // Compose's transition clock also respects the system animator duration scale.
    val phase = if (active) {
        rememberInfiniteTransition(label = "npc-idle").animateFloat(
            0f, 1f, infiniteRepeatable(tween(16_800, easing = LinearEasing)), label = "npc-breath")
    } else rememberUpdatedState(0f)

    GameArtwork(artwork, description, modifier.graphicsLayer {
        val pose = if (active) npcIdlePose(phase.value) else NpcMotionPose()
        transformOrigin = contact?.fittedPivot(size.width, size.height) ?: TransformOrigin.Center
        scaleX = pose.scaleX
        scaleY = pose.scaleY
        rotationZ = pose.rotation
    }, contentScale = ContentScale.Fit)
}

internal data class NpcMotionPose(
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotation: Float = 0f,
)

internal fun npcIdlePose(phase: Float): NpcMotionPose {
    val angle = phase * 2f * PI.toFloat()
    val breath = (1f - cos(angle * 4f)) * .5f
    return NpcMotionPose(
        scaleX = 1f - breath * .002f,
        scaleY = 1f + breath * .008f,
        rotation = sin(angle) * .3f,
    )
}

internal data class NpcArtworkContact(
    val sourceWidth: Float,
    val sourceHeight: Float,
    val contactX: Float,
    val contactY: Float,
) {
    /** ContentScale.Fit adds letterboxing; the feet do not necessarily meet the container bottom. */
    fun fittedPivot(width: Float, height: Float): TransformOrigin {
        if (width <= 0f || height <= 0f) return TransformOrigin.Center
        val fit = minOf(width / sourceWidth, height / sourceHeight)
        return TransformOrigin(
            pivotFractionX = ((width - sourceWidth * fit) / 2f + contactX * fit) / width,
            pivotFractionY = ((height - sourceHeight * fit) / 2f + contactY * fit) / height,
        )
    }
}

/**
 * Measured on the bundled canvases (alpha > 200): midpoint of the lowest 7% opaque band,
 * with the last opaque row as the floor. Retain margins rather than crop or stretch the art.
 * Unknown art stays still until its contact point is verified.
 */
private fun npcArtworkContact(@DrawableRes artwork: Int): NpcArtworkContact? = when (artwork) {
    R.drawable.npc_caretaker_explaining -> NpcArtworkContact(1086f, 1448f, 540.5f, 1420f)
    R.drawable.npc_caretaker_cleaning_lens -> NpcArtworkContact(1086f, 1448f, 545.5f, 1424f)
    R.drawable.npc_carpenter_explaining -> NpcArtworkContact(1254f, 1254f, 645f, 1196f)
    R.drawable.npc_tiko_body -> NpcArtworkContact(1024f, 1024f, 539.5f, 903f)
    R.drawable.npc_luna_body -> NpcArtworkContact(1024f, 1024f, 534f, 910f)
    R.drawable.npc_butcher_tray -> NpcArtworkContact(1083f, 1452f, 551f, 1430f)
    R.drawable.npc_butcher_tying -> NpcArtworkContact(1086f, 1448f, 583f, 1428f)
    R.drawable.npc_butcher_pointing -> NpcArtworkContact(1086f, 1448f, 493f, 1416f)
    R.drawable.npc_butcher_orders -> NpcArtworkContact(1086f, 1448f, 572.5f, 1431f)
    R.drawable.npc_butcher_scales -> NpcArtworkContact(1086f, 1448f, 542.5f, 1416f)
    else -> null
}
