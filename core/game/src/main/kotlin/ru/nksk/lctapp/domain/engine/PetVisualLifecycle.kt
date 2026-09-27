package ru.nksk.lctapp.domain.engine

import ru.nksk.lctapp.domain.content.EventChoiceDefinition
import ru.nksk.lctapp.domain.content.EventType
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.pet.PetVisualState

/**
 * One visual state, advanced only by a successful gameplay transition. Needs are read from
 * the current day; there is no stored reaction to restore after hunger, work or navigation.
 * This also runs in deterministic replay, before the outcome is journalled or persisted.
 */
internal object PetVisualLifecycle {
    fun afterTransition(before: GameState, after: GameState, command: EngineCommand,
        factory: EventFactory, rules: EngineRules): GameState {
        val day = after.engine ?: return after
        val newDay = before.engine?.day != day.day
        val activation = newlyActivated(before, after)
        val choice = completedChoice(before, command, factory)
        val feeding = command is EngineCommand.Feed || choice?.let {
            it.id in factory.policy(it.eventId).feedsPetChoiceIds
        } == true
        val step = !newDay && day.steps > (before.engine?.steps ?: 0)
        val dismissedProposal = command is EngineCommand.DismissDeedProposal
        val needsHelp = PetEventCondition.needsHelp(after, factory)
        val helpResolved = !needsHelp && PetEventCondition.needsHelp(before, factory)

        // Cosmetic changes, balances, practice screens, pause/resume and result dismissal
        // do not turn an old reaction into a new one. Rejected commands never reach here.
        if (!newDay && activation == null && choice == null && !feeding && !step && !dismissedProposal &&
            !needsHelp && !helpResolved) return after

        val authoredResult = choice?.petStateAfter ?: choice?.let {
            factory.event(it.eventId).petStateOnStart.takeIf { _ ->
                factory.policy(it.eventId).startEffectsTiming == EffectTiming.COMPLETE
            }
        }
        val authoredStart = activation?.takeIf { it.origin == EventOrigin.SCHEDULE }?.let {
            factory.event(it.eventId).petStateOnStart.takeIf { _ ->
                factory.policy(it.eventId).startEffectsTiming == EffectTiming.OPEN
            }
        }
        val authored = authoredResult ?: authoredStart
        val mealReaction = (command as? EngineCommand.Feed)?.let { factory.meal(it.mealId).visualStateAfter }
        val previous = before.pet.visualState
        var visual = when {
            needsHelp -> PetVisualState.NEEDS_HELP
            helpResolved -> authored ?: PetVisualState.NORMAL
            authored != null -> authored
            activation != null -> if (awaitsDecision(activation, factory)) PetVisualState.THINKING else PetVisualState.NORMAL
            mealReaction != null -> mealReaction
            newDay && previous != PetVisualState.HUNGRY -> PetVisualState.NORMAL
            choice != null || dismissedProposal -> PetVisualState.NORMAL
            feeding && previous == PetVisualState.HUNGRY -> PetVisualState.NORMAL
            else -> after.pet.visualState
        }

        // Unresolved authored needs survive generic event activation. Only an authored
        // event update can resolve a story injury; an ordinary meal cannot heal it.
        if (previous == PetVisualState.NEEDS_HELP && authored == null && !helpResolved) visual = PetVisualState.NEEDS_HELP
        else if (authored == null && !needsHelp && !helpResolved) {
            if (previous == PetVisualState.HUNGRY && !feeding) visual = PetVisualState.HUNGRY
            if (previous == PetVisualState.TIRED && !newDay) visual = PetVisualState.TIRED
        }

        // Existing gameplay thresholds only. The current need replaces a reaction;
        // once food/rest resolves it, no hidden HAPPY/THINKING state is resurrected.
        if (visual != PetVisualState.NEEDS_HELP) {
            if (!day.ateToday && day.steps >= rules.hungerBlocksAtStep) visual = PetVisualState.HUNGRY
            else if ((day.energy == 0 || newDay && before.engine?.nextMorningEnergy?.let { it < rules.fullEnergy } == true) &&
                visual != PetVisualState.HUNGRY) visual = PetVisualState.TIRED
        }
        return if (visual == after.pet.visualState) after else after.copy(pet = after.pet.transitionTo(visual))
    }

    private fun newlyActivated(before: GameState, after: GameState): EventOccurrence? {
        val current = after.engine?.currentEvent ?: return null
        val old = before.engine?.events?.find { it.id == current.id }
        // CARRIED was still unopened yesterday. CARRIED_ACTIVE and PAUSED already had
        // their entry; reopening them must not replay the start or clear a reaction.
        return current.takeIf { old == null || old.status == EventStatus.PENDING || old.status == EventStatus.CARRIED }
    }

    private fun awaitsDecision(occurrence: EventOccurrence, factory: EventFactory): Boolean {
        // An accepted deed is already being played, while its scheduled proposal asks
        // whether to take the work. A neutral single-action lore card is not a dilemma.
        if (occurrence.origin == EventOrigin.DEED) return false
        val event = factory.event(occurrence.eventId)
        val choices = factory.choices(event.id)
        return event.type == EventType.EARNING || choices.size > 1 ||
            event.moneyDeltaOnStart != 0L || choices.any { it.moneyDelta != 0L }
    }

    private fun completedChoice(before: GameState, command: EngineCommand,
        factory: EventFactory): EventChoiceDefinition? {
        val choiceId = when (command) {
            is EngineCommand.Choose -> command.choiceId
            is EngineCommand.CompleteEvent -> command.choiceId
            is EngineCommand.CompleteStoryGame -> command.choiceId
            is EngineCommand.CompleteDeed -> before.engine?.currentEvent?.eventId?.let { factory.choices(it).singleOrNull()?.id }
            else -> null
        }
        return choiceId?.let { id -> factory.content.choices.find { it.id == id } }
    }
}
