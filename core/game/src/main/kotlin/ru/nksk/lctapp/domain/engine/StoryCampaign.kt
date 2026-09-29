package ru.nksk.lctapp.domain.engine

import ru.nksk.lctapp.domain.content.StoryContent
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.pet.PetAge

/** Data-only requirements shared by the planner, command guards and presentation variants. */
sealed interface StoryCondition {
    data object Always : StoryCondition
    data class All(val conditions: List<StoryCondition>) : StoryCondition
    data class Any(val conditions: List<StoryCondition>) : StoryCondition
    data class Not(val condition: StoryCondition) : StoryCondition
    data class Fact(val id: String) : StoryCondition
    data class EventCompleted(val eventId: String) : StoryCondition
    data class EventCompletions(val eventIds: Set<String>, val minimum: Int) : StoryCondition
    data class OwnsItem(val itemId: String) : StoryCondition
    data class OwnsAnyItem(val itemIds: Set<String>) : StoryCondition
    data class CurrentLocation(val locationId: String) : StoryCondition
    data class EquippedLook(val lookId: String) : StoryCondition
    data class GoalCollected(val goalId: String) : StoryCondition
    data class FactsAtLeast(val factIds: Set<String>, val minimum: Int) : StoryCondition
    data class DayStepsAtLeast(val minimum: Int) : StoryCondition
    data class SelectedGoal(val goalId: String) : StoryCondition
    data object SelectedGoalCollected : StoryCondition
}

data class StoryAct(
    val id: String,
    val title: String,
    val dayId: String,
    val eventIds: List<String>,
    val finaleId: String,
    val petAge: PetAge? = null,
    val goalId: String? = null,
)
data class StoryDeedHint(val condition: StoryCondition, val eventId: String)

/** Each authored chapter can bind its own kit; players choose purchase targets within it. */
data class StoryCampaign(
    val acts: List<StoryAct>,
    val completionAliases: Map<String, Set<String>> = emptyMap(),
    val deedHints: List<StoryDeedHint> = emptyList(),
) {
    init {
        require(acts.isNotEmpty() && acts.map { it.id }.distinct().size == acts.size)
        require(acts.flatMap { it.eventIds }.distinct().size == acts.sumOf { it.eventIds.size })
        require(acts.all { it.eventIds.isNotEmpty() && it.eventIds.last() == it.finaleId })
    }
}

/** Knowledge is projected from immutable choice definitions and committed decisions, never copied into UI flags. */
class StoryProgress(
    private val content: StoryContent,
    private val policies: Map<String, EventPolicy>,
    private val goals: List<GoalCampaign>,
    private val campaign: StoryCampaign?,
    private val state: GameState,
) {
    private val families by lazy(LazyThreadSafetyMode.PUBLICATION) { EventFamilyIndex(policies) }
    private val choiceIds = state.story.decisions.map { it.choiceId }.toSet()
    private val completedEvents = content.choices.filter { it.id in choiceIds }.map { it.eventId }.toSet()
    val facts: Set<String> = policies.values.flatMap { policy ->
        policy.factsByChoiceId.filterKeys { it in choiceIds }.values.flatten()
    }.toSet()

    fun completed(eventId: String): Boolean = eventId in completedEvents ||
        campaign?.completionAliases?.get(eventId).orEmpty().any { it in choiceIds }

    val currentAct: StoryAct? get() = campaign?.acts?.firstOrNull { !completed(it.finaleId) }
    val campaignComplete: Boolean get() = campaign != null && currentAct == null

    val requiredGoal: GoalCampaign? get() = currentAct?.goalId?.let { id -> goals.first { it.goalId == id } }

    fun goalAvailable(goal: GoalCampaign): Boolean = if (campaign?.acts?.any { it.goalId != null } == true)
        requiredGoal?.goalId == goal.goalId else goal.isAvailable(state)

    fun goalCompleted(goal: GoalCampaign): Boolean = campaign?.acts?.firstOrNull { it.goalId == goal.goalId }
        ?.let { completed(it.finaleId) } ?: state.completedGoalProjects.any { it.goalId == goal.goalId }

    /** A completed campaign retains its final stage; catalogs without an age rule leave it alone. */
    val petAge: PetAge? get() = (currentAct ?: campaign?.acts?.last())?.petAge

    fun meets(condition: StoryCondition): Boolean = when (condition) {
        StoryCondition.Always -> true
        is StoryCondition.All -> condition.conditions.all(::meets)
        is StoryCondition.Any -> condition.conditions.any(::meets)
        is StoryCondition.Not -> !meets(condition.condition)
        is StoryCondition.Fact -> condition.id in facts
        is StoryCondition.EventCompleted -> completed(condition.eventId)
        is StoryCondition.EventCompletions -> state.story.decisions.count { decision ->
            content.choices.any { it.id == decision.choiceId && it.eventId in condition.eventIds }
        } >= condition.minimum
        is StoryCondition.OwnsItem -> state.ownedItems.any { it.itemId == condition.itemId }
        is StoryCondition.OwnsAnyItem -> state.ownedItems.any { it.itemId in condition.itemIds }
        is StoryCondition.CurrentLocation -> state.locationScene.location.code == condition.locationId
        is StoryCondition.EquippedLook -> state.pet.selectedLookId == condition.lookId
        is StoryCondition.GoalCollected -> goals.firstOrNull { it.goalId == condition.goalId }?.progress(state, content)?.isCollected == true
        is StoryCondition.FactsAtLeast -> condition.factIds.count { it in facts } >= condition.minimum
        is StoryCondition.DayStepsAtLeast -> (state.engine?.steps ?: 0) >= condition.minimum
        is StoryCondition.SelectedGoal -> goals.selectedGoal(state)?.goalId == condition.goalId
        StoryCondition.SelectedGoalCollected -> goals.selectedGoal(state)?.progress(state, content)?.isCollected == true
    }

    fun eligible(eventId: String): Boolean {
        val policy = policies.getValue(eventId)
        val act = policy.storyActId
        // Item/location conditions decide whether a new problem can be offered. Once shown,
        // changing clothes or leaving its background does not erase the unresolved situation.
        if (act == null && wasPresented(eventId)) return true
        if (act != null && state.engine?.events.orEmpty().any { occurrence ->
                occurrence.status != EventStatus.COMPLETED && policies[occurrence.eventId]?.scheduling?.blocksStoryUntilResolved == true &&
                    wasPresented(occurrence.eventId)
            }) return false
        val selected = goals.selectedGoal(state)
        return (act == null || (selected != null && goalAvailable(selected) && currentAct?.id == act && !completed(eventId))) &&
            meets(policy.condition) && (!policy.finishesStoryAct || meets(StoryCondition.SelectedGoalCollected))
    }

    fun wasPresented(eventId: String): Boolean = state.engine?.events.orEmpty().any {
        it.eventId == eventId && it.status in setOf(EventStatus.ACTIVE, EventStatus.RESULT, EventStatus.PAUSED, EventStatus.CARRIED_ACTIVE)
    }

    /** An existing occurrence also excludes its explicitly declared newer scheduling version. */
    fun nextEvent(excluded: Set<String> = emptySet()): String? {
        val excludedFamilies = excluded.map(families::family).toSet()
        return currentAct?.eventIds?.firstOrNull {
            families.family(it) !in excludedFamilies && !completed(it) && eligible(it)
        }
    }
}

data class EventCardVariant(val condition: StoryCondition, val body: String, val scene: String? = null, val character: String? = null)
