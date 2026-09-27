package ru.nksk.lctapp.core.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import ru.nksk.lctapp.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The same grounded breathing cycle is shared by the menu and adventure advisers. */
@Composable
internal fun MovingPetArtwork(
    @DrawableRes artwork: Int,
    description: String?,
    intensity: Float = 1f,
    modifier: Modifier = Modifier,
) {
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val active = lifecycle.isAtLeast(Lifecycle.State.RESUMED) && !LocalInspectionMode.current
    val phase = if (active) {
        rememberInfiniteTransition(label = "pet-idle").animateFloat(
            0f, 1f, infiniteRepeatable(tween(16_800, easing = LinearEasing)), label = "pet-breath")
    } else rememberUpdatedState(0f)

    // A rectangular scene used to center the sprite above a shadow at the bottom.
    // Keep both inside the same square, including on the narrow comparison cards.
    BoxWithConstraints(modifier, contentAlignment = Alignment.BottomCenter) {
        val canvasSize = minOf(maxWidth, maxHeight)
        val floor = .935f
        Crossfade(targetState = artwork, modifier = Modifier.size(canvasSize),
            animationSpec = tween(600), label = "pet-artwork") { frameArtwork ->
            val currentFrame = frameArtwork == artwork
            var retainedMetadata by remember(frameArtwork) { mutableStateOf(PetFrameMetadata(description, intensity)) }
            val metadata = if (currentFrame) PetFrameMetadata(description, intensity) else retainedMetadata
            SideEffect { if (currentFrame) retainedMetadata = metadata }
            val grounding = petArtworkGrounding(frameArtwork)
            // Crossfade the full grounded frame: its character and both shadows stay together.
            // Only the incoming frame is exposed to accessibility during the overlap.
            Box(Modifier.fillMaxSize().then(if (currentFrame) Modifier else Modifier.clearAndSetSemantics {})) {
                grounding?.let { contact ->
                    GameArtwork(R.drawable.menu_ground_shadow, null,
                        Modifier.offset(
                            x = canvasSize * (contact.centerX - contact.shadowWidth / 2f),
                            y = canvasSize * (floor - contact.shadowHeight / 2f),
                        ).size(canvasSize * contact.shadowWidth, canvasSize * contact.shadowHeight),
                        contentScale = ContentScale.FillBounds)
                    // A tighter contact shadow sits beneath the soles. The wider layer
                    // stays soft, while feet remain readable on both dark and light floors.
                    GameArtwork(R.drawable.menu_ground_shadow, null,
                        Modifier.offset(
                            x = canvasSize * (contact.centerX - contact.shadowWidth * .32f),
                            y = canvasSize * (floor - contact.shadowHeight * .15f),
                        ).size(canvasSize * contact.shadowWidth * .64f, canvasSize * contact.shadowHeight * .30f),
                        contentScale = ContentScale.FillBounds)
                }
                GameArtwork(frameArtwork, if (currentFrame) metadata.description else null, Modifier.fillMaxSize().graphicsLayer {
                    transformOrigin = TransformOrigin(grounding?.centerX ?: .5f, grounding?.contactY ?: .91f)
                    translationY = grounding?.let { size.height * (floor - it.contactY) } ?: 0f
                    // Read animation state in the layer, with no per-frame pose object.
                    withPetIdlePose(if (active) phase.value else 0f, metadata.intensity) { x, sx, sy, rotation ->
                        translationX = size.width * x
                        scaleX = sx
                        scaleY = sy
                        rotationZ = rotation
                    }
                })
            }
        }
    }
}

private data class PetFrameMetadata(val description: String?, val intensity: Float)

/** Horizontal motion is a fraction of the full canvas; the feet remain on the ground. */
internal data class PetMotionPose(
    val x: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotation: Float = 0f,
)

/** Four slow breaths and a small weight shift; the loop closes without displacement. */
internal fun petIdlePose(phase: Float, intensity: Float = 1f): PetMotionPose {
    return withPetIdlePose(phase, intensity, ::PetMotionPose)
}

private inline fun <T> withPetIdlePose(phase: Float, intensity: Float,
    block: (x: Float, scaleX: Float, scaleY: Float, rotation: Float) -> T): T {
    val angle = phase * 2f * PI.toFloat()
    val breath = (1f - cos(angle * 4f)) * .5f
    val sway = sin(angle)
    return block(
        sway * .003f * intensity,
        1f - breath * .004f * intensity,
        1f + breath * .012f * intensity,
        sway * .65f * intensity,
    )
}
