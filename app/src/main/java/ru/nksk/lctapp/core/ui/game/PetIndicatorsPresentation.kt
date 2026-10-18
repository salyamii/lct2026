package ru.nksk.lctapp.core.ui.game

import ru.nksk.lctapp.core.ui.components.PetIndicatorEffectUiState
import ru.nksk.lctapp.core.ui.components.PetIndicatorKind
import ru.nksk.lctapp.core.ui.components.PetIndicatorsUiState
import ru.nksk.lctapp.core.ui.components.PetMoodUiState
import ru.nksk.lctapp.core.ui.components.PetSatietyUiState
import ru.nksk.lctapp.domain.engine.EffectTiming
import ru.nksk.lctapp.domain.engine.EventStatus
import ru.nksk.lctapp.domain.engine.GameCatalog
import ru.nksk.lctapp.domain.engine.PetEventCondition
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.pet.PetVisualState

/** Active-day needs use engine fields. The legacy satiety/fatigue values are never repurposed. */
internal fun petIndicators(state: GameState, catalog: GameCatalog, demoMode: Boolean = false): PetIndicatorsUiState {
    val day = state.engine
    val energy = if (demoMode || day == null) catalog.rules.fullEnergy else day.energy
    val visual = PetEventCondition.forPresentation(state, catalog.policies).visualState
        .let { if (demoMode && it == PetVisualState.TIRED) PetVisualState.NORMAL else it }
    return PetIndicatorsUiState(
        energy = energy,
        maxEnergy = catalog.rules.fullEnergy,
        energyLabel = when {
            demoMode -> "Не тратится"
            else -> energyDescription(energy, catalog.rules.fullEnergy)
        },
        energyExplanation = when {
            day == null -> "Герой отдохнул и готов к первому дню."
            demoMode -> "Энергия не тратится, но герой всё ещё может проголодаться."
            else -> "Дела тратят энергию, сон восстанавливает."
        },
        satiety = when {
            day == null -> PetSatietyUiState.NOT_EATEN
            day.ateToday -> PetSatietyUiState.FED
            day.steps >= catalog.rules.hungerBlocksAtStep -> PetSatietyUiState.HUNGRY
            else -> PetSatietyUiState.NOT_EATEN
        },
        mood = if (day == null) PetMoodUiState.CALM else visual.toMood(),
        demoMode = demoMode,
    )
}

/** Effects describe completion, including practical choices that first open a mini-game. */
internal fun eventChoicePetEffects(state: GameState, catalog: GameCatalog, choiceId: String,
    demoMode: Boolean = false): List<PetIndicatorEffectUiState> {
    val choice = catalog.content.choices.first { it.id == choiceId }
    val event = catalog.content.events.first { it.id == choice.eventId }
    val policy = catalog.policies.getValue(event.id)
    val day = state.engine ?: return emptyList()
    val before = petIndicators(state, catalog, demoMode)
    val cost = if (demoMode) 0 else policy.energyFor(choice.id)
    val restore = if (demoMode) 0 else policy.choiceEnergyRestores[choice.id] ?: 0
    val currentEnergy = if (demoMode) catalog.rules.fullEnergy else day.energy
    val remainingEnergy = currentEnergy - cost
    val afterEnergy = if (restore == 0) remainingEnergy else
        (remainingEnergy.toLong() + restore).coerceAtMost(catalog.rules.fullEnergy.toLong()).toInt()
    val feeds = choice.id in policy.feedsPetChoiceIds
    val hungerAfter = !day.ateToday && !feeds && day.steps.toLong() + 1 >= catalog.rules.hungerBlocksAtStep
    val reaction = choice.petStateAfter ?: event.petStateOnStart.takeIf {
        policy.startEffectsTiming == EffectTiming.COMPLETE
    }
    return buildList {
        energyEffect(currentEnergy, afterEnergy, catalog.rules.fullEnergy, demoMode,
            hasRestoration = (policy.choiceEnergyRestores[choice.id] ?: 0) > 0)?.let(::add)
        when {
            feeds && before.satiety != PetSatietyUiState.FED ->
                add(PetIndicatorEffectUiState(PetIndicatorKind.SATIETY, "Утолит голод", "До следующего дня."))
            hungerAfter && before.satiety != PetSatietyUiState.HUNGRY ->
                add(PetIndicatorEffectUiState(PetIndicatorKind.SATIETY, "Проголодается", "Перед следующим делом нужно поесть."))
        }
        // Show only an authored reaction that cannot be replaced by an active need.
        // No inferred calm/happiness is assigned to choices without authored emotion.
        if (reaction != null && reaction != PetVisualState.NORMAL && reaction.toMood() != before.mood &&
            (reaction == PetVisualState.NEEDS_HELP || !hungerAfter && afterEnergy > 0) &&
            !needsCareAfter(state, catalog, completedEventId = event.id, reaction = reaction)) {
            add(moodEffect(reaction))
        }
    }
}

/** A deed invitation/start spends nothing; these are its effects after successful completion. */
internal fun deedPetEffects(state: GameState, catalog: GameCatalog, eventId: String,
    demoMode: Boolean = false): List<PetIndicatorEffectUiState> =
    catalog.content.choices.singleOrNull { it.eventId == eventId }?.let {
        eventChoicePetEffects(state, catalog, it.id, demoMode)
    }.orEmpty()

/** Meal offers keep their distinct benefits visible; actual restoration is capped by MealPolicy. */
internal fun mealPetEffects(state: GameState, catalog: GameCatalog, mealId: String,
    demoMode: Boolean = false): List<PetIndicatorEffectUiState> {
    val day = state.engine ?: return emptyList()
    val effects = catalog.mealPolicy.effects(mealId)
    val currentEnergy = if (demoMode) catalog.rules.fullEnergy else day.energy
    val afterEnergy = when {
        demoMode -> catalog.rules.fullEnergy
        effects.exhaustsCurrentEnergy -> 0
        effects.energyRestore == 0 -> currentEnergy
        else -> (currentEnergy.toLong() + effects.energyRestore).coerceAtMost(catalog.rules.fullEnergy.toLong()).toInt()
    }
    return buildList {
        add(PetIndicatorEffectUiState(PetIndicatorKind.SATIETY, "Утолит голод", "До следующего дня."))
        if (effects.exhaustsCurrentEnergy && !demoMode) {
            val tomorrow = effects.nextMorningEnergy
            add(PetIndicatorEffectUiState(PetIndicatorKind.ENERGY, "Нужен отдых",
                if (tomorrow != null) "Энергия: 0 сегодня, $tomorrow из ${catalog.rules.fullEnergy} утром." else "Сегодня энергия закончится."))
        } else if (effects.energyRestore > 0) {
            add(PetIndicatorEffectUiState(PetIndicatorKind.ENERGY,
                if (effects.energyRestore == 1) "Восстановит немного энергии" else "Восстановит больше энергии",
                when {
                    demoMode -> "В режиме бога энергия не тратится."
                    currentEnergy >= catalog.rules.fullEnergy -> "Энергия на максимуме."
                    else -> "До полного запаса."
                }))
        } else {
            add(energyEffect(currentEnergy, afterEnergy, catalog.rules.fullEnergy, demoMode, effects.energyRestore > 0)
                ?: PetIndicatorEffectUiState(PetIndicatorKind.ENERGY, "Энергия не изменится"))
        }
        effects.visualStateAfter?.takeUnless { it == PetVisualState.NORMAL }?.let { reaction ->
            if (afterEnergy > 0 && !needsCareAfter(state, catalog, completedEventId = null, reaction = reaction)) {
                add(moodEffect(reaction))
            }
        }
    }
}

private fun energyEffect(before: Int, after: Int, maximum: Int, demoMode: Boolean,
    hasRestoration: Boolean): PetIndicatorEffectUiState? = when {
    demoMode -> PetIndicatorEffectUiState(PetIndicatorKind.ENERGY, "Энергия не тратится", "Режим бога.")
    after < before -> PetIndicatorEffectUiState(PetIndicatorKind.ENERGY, "Энергия -${before - after}")
    after > before -> PetIndicatorEffectUiState(PetIndicatorKind.ENERGY, "Энергия +${after - before}")
    hasRestoration && after >= maximum -> PetIndicatorEffectUiState(PetIndicatorKind.ENERGY, "Энергия на максимуме")
    hasRestoration -> PetIndicatorEffectUiState(PetIndicatorKind.ENERGY, "Энергия не изменится")
    else -> null
}

private fun needsCareAfter(state: GameState, catalog: GameCatalog, completedEventId: String?, reaction: PetVisualState): Boolean {
    if (reaction == PetVisualState.NEEDS_HELP) return false
    val completedOccurrenceId = state.engine?.currentEvent?.takeIf { it.eventId == completedEventId }?.id
    val unresolved = state.engine?.events.orEmpty().any {
        it.id != completedOccurrenceId &&
            it.status in setOf(EventStatus.ACTIVE, EventStatus.PAUSED, EventStatus.CARRIED_ACTIVE) &&
            catalog.policies[it.eventId]?.requiresPetHelp == true
    }
    if (unresolved) return true
    // Feeding cannot resolve an old NEEDS_HELP state, even if no modern event policy owns it.
    return completedEventId == null && state.pet.visualState == PetVisualState.NEEDS_HELP
}

private fun moodEffect(state: PetVisualState) = PetIndicatorEffectUiState(PetIndicatorKind.MOOD,
    when (state) {
        PetVisualState.HAPPY -> "Радость"
        PetVisualState.UPSET -> "Огорчение"
        else -> state.toMood().label
    }, when (state) {
        PetVisualState.HAPPY, PetVisualState.UPSET -> null
        else -> state.toMood().explanation
    })

private fun PetVisualState.toMood(): PetMoodUiState = when (this) {
    PetVisualState.NORMAL -> PetMoodUiState.CALM
    PetVisualState.HAPPY -> PetMoodUiState.HAPPY
    PetVisualState.UPSET -> PetMoodUiState.UPSET
    PetVisualState.THINKING -> PetMoodUiState.THINKING
    PetVisualState.WORRIED -> PetMoodUiState.WORRIED
    PetVisualState.NEEDS_HELP -> PetMoodUiState.NEEDS_CARE
    PetVisualState.HUNGRY -> PetMoodUiState.HUNGRY
    PetVisualState.TIRED -> PetMoodUiState.TIRED
}
