package ru.nksk.lctapp.feature.day.ui

import ru.nksk.lctapp.core.ui.components.PetIndicatorEffectUiState

internal data class DayChoicesPresentation(
    val commonEffects: List<PetIndicatorEffectUiState>,
    val options: List<DayOption>,
)

/** Food-blocked choices use the separate feeding action; compare only choices shown here. */
internal fun dayChoicesPresentation(options: List<DayOption>): DayChoicesPresentation {
    val visible = options.filterNot { it.needsFood }
    val common = if (visible.size < 2) emptyList() else visible.first().petEffects.distinct()
        .filter { effect -> visible.drop(1).all { effect in it.petEffects } }
    return DayChoicesPresentation(common, visible.map { option ->
        option.copy(petEffects = option.petEffects.filterNot { it in common })
    })
}
