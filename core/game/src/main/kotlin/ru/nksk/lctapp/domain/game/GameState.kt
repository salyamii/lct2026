package ru.nksk.lctapp.domain.game

import ru.nksk.lctapp.domain.location.LocationScene
import ru.nksk.lctapp.domain.economy.EconomyState
import ru.nksk.lctapp.domain.pet.PetState
import ru.nksk.lctapp.domain.story.StoryState
import ru.nksk.lctapp.domain.engine.EngineState
import ru.nksk.lctapp.domain.engine.CompletedGoalProject

/** Immutable aggregate. Producers must not mutate backing lists after publishing a snapshot. */
@kotlinx.serialization.Serializable
data class GameState(
    val pet: PetState,
    val economy: EconomyState,
    val story: StoryState,
    /** Legacy content value preserved for compatibility; active-day hunger uses engine.ateToday/steps. */
    val satiety: Int,
    /** Legacy content value preserved for compatibility; available effort uses engine.energy. */
    val fatigue: Int,
    val ownedItems: List<OwnedItem>,
    val engine: EngineState? = null,
    val completedMiniGames: Set<String> = emptySet(),
    val selectedGoalId: String? = null,
    val completedGoalProjects: List<CompletedGoalProject> = emptyList(),
    val locationScene: LocationScene = LocationScene(),
    val financial: ru.nksk.lctapp.domain.finance.FinancialProgress = ru.nksk.lctapp.domain.finance.FinancialProgress(),
    val eventHistory: List<ru.nksk.lctapp.domain.engine.EventExposure> = emptyList(),
    /** One chosen purchase target; savings remain a single shared balance. */
    val selectedSavingItemId: String? = null,
) {
    init {
        require(eventHistory.map { it.eventId }.distinct().size == eventHistory.size)
        require(completedGoalProjects.map { it.decisionId }.distinct().size == completedGoalProjects.size)
        require(completedGoalProjects.all { project -> story.decisions.any { it.id == project.decisionId } })
        require(completedGoalProjects.map { it.decisionId } == story.decisions.map { it.id }
            .filter { id -> completedGoalProjects.any { it.decisionId == id } }) {
            "Completed projects follow the chronological order of their decisions"
        }
        require(engine == null || engine.currentEvent?.eventId == story.activeEventId) {
            "Engine occurrence and story active event must agree"
        }
    }
}

/** One ownership occurrence; duplicate item IDs are allowed and list order is significant. */
@kotlinx.serialization.Serializable
data class OwnedItem(val id: String, val itemId: String)
