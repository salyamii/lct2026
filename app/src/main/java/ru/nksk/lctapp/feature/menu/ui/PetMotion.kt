package ru.nksk.lctapp.feature.menu.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import ru.nksk.lctapp.core.ui.components.GameArtwork

@Composable
internal fun MovingPet(
    artwork: Int,
    description: String,
    intensity: Float,
    modifier: Modifier = Modifier,
) {
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val active = lifecycle.isAtLeast(Lifecycle.State.RESUMED) && !LocalInspectionMode.current
    // Removing the transition stops the frame clock when this entry is no longer foreground.
    val phase = if (active) {
        rememberInfiniteTransition(label = "pet-idle").animateFloat(
            0f, 1f, infiniteRepeatable(tween(16_800, easing = LinearEasing)), label = "pet-breath")
    } else rememberUpdatedState(0f)

    // Keep measurement and the map anchor outside the animated layer.
    Box(modifier.testTag("menu_pet")) {
        GameArtwork(artwork, description, Modifier.fillMaxSize().graphicsLayer {
            val pose = if (active) petIdlePose(phase.value, intensity) else PetMotionPose()
            transformOrigin = TransformOrigin(.5f, .91f)
            translationX = size.width * pose.x
            scaleX = pose.scaleX
            scaleY = pose.scaleY
            rotationZ = pose.rotation
        })
    }
}
