package ru.nksk.lctapp.domain.game

import ru.nksk.lctapp.domain.economy.EconomyState
import ru.nksk.lctapp.domain.pet.PetState
import ru.nksk.lctapp.domain.story.StoryState
import ru.nksk.lctapp.domain.engine.EngineState

/** Immutable aggregate. Producers must not mutate backing lists after publishing a snapshot. */
data class GameState(
    val pet: PetState,
    val economy: EconomyState,
    val story: StoryState,
    val satiety: Int,
    val fatigue: Int,
    val ownedItems: List<OwnedItem>,
    val engine: EngineState? = null,
) {
    init {
        require(engine == null || engine.currentEvent?.eventId == story.activeEventId) {
            "Engine occurrence and story active event must agree"
        }
    }
}

/** One ownership occurrence; duplicate item IDs are allowed and list order is significant. */
data class OwnedItem(val id: String, val itemId: String)
