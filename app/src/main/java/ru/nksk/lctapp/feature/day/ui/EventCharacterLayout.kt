package ru.nksk.lctapp.feature.day.ui

import ru.nksk.lctapp.core.ui.components.ArtworkVisibleBounds
import ru.nksk.lctapp.core.ui.components.PetArtworkGrounding

internal data class EventArtworkSlot(val x: Float, val y: Float, val size: Float)
internal data class EventCharacterLayout(val companion: EventArtworkSlot, val pet: EventArtworkSlot)

/**
 * Lay out the silhouettes on one floor, not their different transparent margins.
 * All returned slots retain the complete square canvas. Only transparent canvas may extend
 * beyond the scene; the visible figures, their motion margin and the pet shadow remain inside.
 */
internal fun eventCharacterLayout(
    width: Float,
    height: Float,
    petWidthFraction: Float,
    petBounds: ArtworkVisibleBounds,
    companionBounds: ArtworkVisibleBounds,
    grounding: PetArtworkGrounding?,
): EventCharacterLayout {
    val floor = .935f
    val motionMargin = .02f
    val petShift = grounding?.let { floor - it.contactY } ?: 0f
    val petLeft = minOf(petBounds.left, grounding?.let { it.centerX - it.shadowWidth / 2f } ?: petBounds.left) - motionMargin
    val petRight = maxOf(petBounds.right, grounding?.let { it.centerX + it.shadowWidth / 2f } ?: petBounds.right) + motionMargin
    val petTop = petBounds.top + petShift - motionMargin
    val petBottom = maxOf(petBounds.bottom + petShift, grounding?.let { floor + it.shadowHeight / 2f } ?: floor) + motionMargin
    val companionLeft = companionBounds.left - motionMargin
    val companionRight = companionBounds.right + motionMargin
    val companionTop = companionBounds.top - motionMargin
    val companionFloor = companionBounds.bottom
    var petSize = width * petWidthFraction
    var companionSize = width * .67f
    val gap = width * .04f
    val visibleWidth = (petRight - petLeft) * petSize + (companionRight - companionLeft) * companionSize
    val aboveFloor = maxOf((floor - petTop) * petSize, (companionFloor - companionTop) * companionSize)
    val belowFloor = maxOf((petBottom - floor) * petSize, motionMargin * companionSize)
    val fit = minOf(1f, width * .90f / visibleWidth.coerceAtLeast(1f),
        height * .94f / (aboveFloor + belowFloor).coerceAtLeast(1f)).coerceAtLeast(0f)
    petSize *= fit
    companionSize *= fit
    val start = (width - visibleWidth * fit - gap) / 2f
    val baseline = height * .97f - belowFloor * fit
    return EventCharacterLayout(
        companion = EventArtworkSlot(start - companionLeft * companionSize,
            baseline - companionFloor * companionSize, companionSize),
        pet = EventArtworkSlot(start + (companionRight - companionLeft) * companionSize + gap - petLeft * petSize,
            baseline - floor * petSize, petSize),
    )
}
