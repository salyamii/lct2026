package ru.nksk.lctapp.domain.engine

import ru.nksk.lctapp.domain.pet.PetVisualState

/** Required authored parameters: there is deliberately no production default for unresolved values. */
data class EngineRules(
    val id: String,
    val fullEnergy: Int,
    val hungerBlocksAtStep: Int,
    val shortDeedMaxEnergy: Int,
) {
    init {
        require(id.isNotBlank())
        require(fullEnergy > 0 && hungerBlocksAtStep > 0)
        require(shortDeedMaxEnergy in 1..3)
    }
}

/** Supplement to the v1 catalog. Values must come from authored content, never a screen callback. */
data class EventPolicy(
    val energyCost: Int,
    val requiredItemIds: Set<String> = emptySet(),
    val previousLoreEventId: String? = null,
    val startEffectsTiming: EffectTiming? = null,
    val chapterEntryDayId: String? = null,
) {
    init { require(energyCost in 0..3) }
}

enum class EffectTiming { OPEN, COMPLETE }

data class MealDefinition(
    val id: String,
    val price: Long,
    val visualStateAfter: PetVisualState?,
    val nextMorningEnergy: Int? = null,
) {
    init {
        require(id.isNotBlank() && price >= 0)
        require(nextMorningEnergy == null || nextMorningEnergy >= 0)
        require(nextMorningEnergy == null || price == 0L)
    }
}
