package ru.nksk.lctapp.feature.tasks.ui

/** Normalized game positions describe the marker's center along its available travel. */
internal class TargetStopTrackGeometry(width: Float, markerSize: Float) {
    private val radius = markerSize / 2f
    private val travel = (width - markerSize).coerceAtLeast(0f)

    fun centerAt(position: Float): Float = radius + travel * position
}
