package ru.nksk.lctapp.feature.menu.ui

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Horizontal motion is a fraction of the full canvas; the feet remain on the ground. */
internal data class PetMotionPose(
    val x: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotation: Float = 0f,
)

/** Four slow breaths and a small weight shift; the loop closes without displacement. */
internal fun petIdlePose(phase: Float, intensity: Float = 1f): PetMotionPose {
    val angle = phase * 2f * PI.toFloat()
    val breath = (1f - cos(angle * 4f)) * .5f
    val sway = sin(angle)
    return PetMotionPose(
        x = sway * .003f * intensity,
        scaleX = 1f - breath * .004f * intensity,
        scaleY = 1f + breath * .012f * intensity,
        rotation = sway * .65f * intensity,
    )
}
