package ru.nksk.lctapp.domain.engine

import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.pet.PetState
import ru.nksk.lctapp.domain.pet.PetVisualState

/** The opened occurrence owns the need for care; an unseen scheduled card does not make the pet ill. */
object PetEventCondition {
    /** Read-only compatibility for a card already open before its visual policy was supplied. */
    fun forPresentation(state: GameState, policies: Map<String, EventPolicy>): PetState =
        if (needsHelp(state) { policies[it]?.requiresPetHelp == true })
            state.pet.transitionTo(PetVisualState.NEEDS_HELP) else state.pet

    internal fun needsHelp(state: GameState, factory: EventFactory): Boolean =
        needsHelp(state) { factory.policy(it).requiresPetHelp }

    private fun needsHelp(state: GameState, requiresHelp: (String) -> Boolean): Boolean =
        state.engine?.events.orEmpty().any { occurrence ->
            occurrence.status in setOf(EventStatus.ACTIVE, EventStatus.PAUSED, EventStatus.CARRIED_ACTIVE) &&
                requiresHelp(occurrence.eventId)
        }
}
