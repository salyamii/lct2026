package ru.nksk.lctapp.feature.menu.ui

/** Compatibility for the menu's existing pose checks; all screens use the shared cycle. */
internal typealias PetMotionPose = ru.nksk.lctapp.core.ui.components.PetMotionPose

internal fun petIdlePose(phase: Float, intensity: Float = 1f): PetMotionPose =
    ru.nksk.lctapp.core.ui.components.petIdlePose(phase, intensity)
