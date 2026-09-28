package ru.nksk.lctapp.domain.engine

import ru.nksk.lctapp.domain.pet.PetVisualState
import ru.nksk.lctapp.domain.minigame.DeedGameKind
import ru.nksk.lctapp.domain.location.GameLocation

/** Required authored parameters: there is deliberately no production default for unresolved values. */
data class EngineRules(
    val id: String,
    val fullEnergy: Int,
    val hungerBlocksAtStep: Int,
    val shortDeedMaxEnergy: Int,
    val weeklyIncome: Long = 0,
) {
    init {
        require(id.isNotBlank())
        require(fullEnergy > 0 && hungerBlocksAtStep > 0)
        require(shortDeedMaxEnergy in 1..3)
        require(weeklyIncome >= 0)
    }
}

/** Supplement to the v1 catalog. Values must come from authored content, never a screen callback. */
data class EventPolicy(
    val energyCost: Int,
    val requiredItemIds: Set<String> = emptySet(),
    val previousLoreEventId: String? = null,
    val startEffectsTiming: EffectTiming? = null,
    val chapterEntryDayId: String? = null,
    val discardOfferOnDismiss: Boolean = false,
    val deedGameKind: DeedGameKind? = null,
    val choiceEnergyCosts: Map<String, Int> = emptyMap(),
    val goalId: String? = null,
    val condition: StoryCondition = StoryCondition.Always,
    val factsByChoiceId: Map<String, Set<String>> = emptyMap(),
    val storyActId: String? = null,
    val finishesStoryAct: Boolean = false,
    val scheduling: EventSchedulingPolicy = EventSchedulingPolicy(),
    /** Authored food purchases satisfy the daily meal, without restoring energy. */
    val feedsPetChoiceIds: Set<String> = emptySet(),
    /** Practical STORY/RANDOM actions complete only after this choice's mini-game. */
    val choiceGameKinds: Map<String, DeedGameKind> = emptyMap(),
    /** Retired actions remain in immutable content for saved decisions, but cannot be chosen again. */
    val disabledChoiceIds: Set<String> = emptySet(),
    /** This opened event needs care until its choice resolves it; it adds no financial or energy effect. */
    val requiresPetHelp: Boolean = false,
    /** Confirmed travel changes the saved menu location, never inferred from presentation. */
    val choiceDestinations: Map<String, GameLocation> = emptyMap(),
) {
    init {
        require(energyCost in 0..3)
        require(choiceEnergyCosts.values.all { it in 0..3 })
    }

    fun energyFor(choiceId: String): Int = choiceEnergyCosts[choiceId] ?: energyCost
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
