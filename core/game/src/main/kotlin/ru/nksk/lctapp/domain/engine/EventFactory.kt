package ru.nksk.lctapp.domain.engine

import ru.nksk.lctapp.domain.content.*
import ru.nksk.lctapp.domain.game.GameState

/** Compiles validated, immutable authored content; creating an occurrence never grants an outcome. */
class EventFactory(
    internal val content: StoryContent,
    policies: Map<String, EventPolicy>,
    meals: List<MealDefinition>,
    internal val goals: List<GoalCampaign> = emptyList(),
    internal val campaign: StoryCampaign? = null,
) {
    private val events = content.events.associateBy { it.id }
    private val policies = policies.toMap()
    internal val mealPolicy = MealPolicy(meals)

    init {
        require(goals.map { it.goalId }.distinct().size == goals.size)
        goals.forEach { goal ->
            require(goal.availableAfterProjects >= 0)
            require(goal.goalId !in goal.requiredCompletedGoalIds && goal.requiredCompletedGoalIds.all { required -> goals.any { it.goalId == required } })
            require(content.goals.any { it.id == goal.goalId })
            require(goal.itemIds.isNotEmpty() && goal.itemIds.distinct().size == goal.itemIds.size)
            require(goal.itemIds.toSet() == content.requiredItems.filter { it.goalId == goal.goalId }.map { it.itemId }.toSet())
            require(goal.itemIds.all { id -> content.items.any { it.id == id && it.priceCoins != null && it.priceCoins > 0 } })
            require(content.events.any { it.id == goal.introductionEventId && it.type == EventType.STORY })
        }
        require(content.events.filter { it.type == EventType.EARNING }.all { event ->
            event.moneyDeltaOnStart >= 0 && content.choices.filter { it.eventId == event.id }.all { it.moneyDelta >= 0 }
        }) { "EARNING cannot require money at entry or in any answer" }
        require(events.size == content.events.size) { "Duplicate event identity" }
        require(content.choices.map { it.id }.distinct().size == content.choices.size)
        require(content.choices.all { it.eventId in events })
        val goalItems = content.requiredItems.map { it.itemId }.toSet()
        val knownFacts = policies.values.flatMap { it.factsByChoiceId.values.flatten() }.toSet()
        fun validate(condition: StoryCondition) {
            when (condition) {
                StoryCondition.Always, StoryCondition.SelectedGoalCollected -> Unit
                is StoryCondition.All -> { require(condition.conditions.isNotEmpty()); condition.conditions.forEach(::validate) }
                is StoryCondition.Any -> { require(condition.conditions.isNotEmpty()); condition.conditions.forEach(::validate) }
                is StoryCondition.Not -> validate(condition.condition)
                is StoryCondition.Fact -> require(condition.id in knownFacts) { "No producer for story fact: ${condition.id}" }
                is StoryCondition.EventCompleted -> require(condition.eventId in events)
                is StoryCondition.EventCompletions -> require(condition.minimum > 0 && condition.eventIds.isNotEmpty() && condition.eventIds.all { it in events })
                is StoryCondition.OwnsItem -> require(content.items.any { it.id == condition.itemId })
                is StoryCondition.OwnsAnyItem -> require(condition.itemIds.isNotEmpty() && condition.itemIds.all { id -> content.items.any { it.id == id } })
                is StoryCondition.CurrentLocation -> require(ru.nksk.lctapp.domain.location.GameLocation.entries.any { it.code == condition.locationId })
                is StoryCondition.EquippedLook -> require(condition.lookId.isNotBlank())
                is StoryCondition.GoalCollected -> require(goals.any { it.goalId == condition.goalId })
                is StoryCondition.FactsAtLeast -> require(condition.minimum in 1..condition.factIds.size && condition.factIds.all { it in knownFacts })
                is StoryCondition.DayStepsAtLeast -> require(condition.minimum >= 0)
                is StoryCondition.SelectedGoal -> require(goals.any { it.goalId == condition.goalId })
            }
        }
        campaign?.let { story ->
            story.deedHints.forEach {
                validate(it.condition)
                require(events[it.eventId]?.type == EventType.EARNING)
            }
            story.acts.forEach { act ->
                require(content.days.any { it.id == act.dayId })
                require(act.eventIds.all { it in events && policies[it]?.storyActId == act.id })
                require(policies[act.finaleId]?.finishesStoryAct == true)
                require(act.goalId == null || goals.any { it.goalId == act.goalId }) { "Unknown chapter kit" }
                require(act.eventIds.count { policies[it]?.finishesStoryAct == true } == 1)
            }
            require(story.completionAliases.keys.all { it in events })
            require(story.completionAliases.values.flatten().all { id -> content.choices.any { it.id == id } })
        }
        for ((id, policy) in this.policies) {
            val definition = requireNotNull(events[id]) { "Unknown event policy: $id" }
            require(policy.goalId == null || content.goals.any { it.id == policy.goalId })
            val eventChoices = content.choices.filter { it.eventId == id }
            require(policy.disabledChoiceIds.all { choice -> eventChoices.any { it.id == choice } }) {
                "A retired choice must belong to its event: $id"
            }
            require(eventChoices.isEmpty() || eventChoices.any { it.id !in policy.disabledChoiceIds }) {
                "An event must retain an available action: $id"
            }
            require(policy.feedsPetChoiceIds.all { choice -> eventChoices.any { it.id == choice } })
            require(policy.feedsPetChoiceIds.all { policy.energyFor(it) == 0 }) { "Food cannot consume energy" }
            validate(policy.condition)
            require(policy.factsByChoiceId.keys.all { choice -> eventChoices.any { it.id == choice } })
            require(policy.factsByChoiceId.values.flatten().all { it.isNotBlank() })
            require(policy.choiceDestinations.keys.all { choice -> eventChoices.any { it.id == choice } }) {
                "A destination must belong to a choice of this event: $id"
            }
            require(policy.storyActId == null || campaign?.acts?.any { act ->
                act.id == policy.storyActId && (id in act.eventIds || act.eventIds.any { currentId ->
                    val current = policies.getValue(currentId).scheduling
                    // Historical cards keep their policy and choices after an authored revision.
                    // Only a declared predecessor of a current card in this same act is accepted.
                    id in current.previousEventIds &&
                        (current.family ?: currentId) == (policy.scheduling.family ?: id)
                })
            } == true)
            require(!policy.finishesStoryAct || campaign?.acts?.any { it.finaleId == id } == true)
            require(policy.choiceEnergyCosts.keys.all { choice -> eventChoices.any { it.id == choice } }) {
                "Energy override must belong to this event: $id"
            }
            require(definition.minSatiety == null && definition.maxFatigue == null) {
                "Translate legacy satiety/fatigue thresholds to the selected rules before activating $id"
            }
            require(policy.requiredItemIds.all { item -> content.items.any { it.id == item } })
            require(policy.previousLoreEventId == null || events[policy.previousLoreEventId]?.type == EventType.STORY)
            if (policy.deedGameKind != null) {
                require(definition.type == EventType.EARNING)
                val reward = content.choices.singleOrNull { it.eventId == id }
                require(reward != null && reward.moneyDelta >= 0) { "A mini-game needs one maximum reward: $id" }
            }
            if (policy.choiceGameKinds.isNotEmpty()) {
                require(definition.type in setOf(EventType.STORY, EventType.RANDOM) && policy.deedGameKind == null)
                require(policy.choiceGameKinds.keys.all { choice -> eventChoices.any { it.id == choice } }) {
                    "A story mini-game must name an existing choice: $id"
                }
            }
            val startItems = content.eventItemEffects.filter { it.eventId == id }
            val hasStartEffects = definition.moneyDeltaOnStart != 0L || definition.petStateOnStart != null || startItems.isNotEmpty()
            require(!hasStartEffects || policy.startEffectsTiming != null) { "Specify timing of event effects: $id" }
            if (definition.type == EventType.EARNING) {
                require(policy.choiceEnergyCosts.isEmpty()) { "A deed's fixed effort also defines its deadline" }
                require(policy.energyCost in 1..3) { "A deed needs a cost of 1, 2 or 3" }
                require(!hasStartEffects || policy.startEffectsTiming == EffectTiming.COMPLETE) {
                    "Offering a deed must not apply its earnings or other effects"
                }
            }
            if (definition.nextChapterId != null) {
                require(definition.type == EventType.STORY)
                require(content.days.any { it.id == policy.chapterEntryDayId && it.chapterId == definition.nextChapterId }) {
                    "Explicit chapter entry day required for $id"
                }
            }
            if (definition.type == EventType.STORY) {
                val choiceIds = content.choices.filter { it.eventId == id }.map { it.id }.toSet()
                require(startItems.none { it.operation == ItemOperation.ADD && it.itemId in goalItems })
                require(content.choiceItemEffects.none {
                    it.choiceId in choiceIds && it.operation == ItemOperation.ADD && it.itemId in goalItems
                }) { "Story events cannot award goal items" }
            }
        }
    }

    fun create(eventId: String, occurrenceId: String, origin: EventOrigin = EventOrigin.SCHEDULE): EventOccurrence {
        require(occurrenceId.isNotBlank())
        event(eventId)
        policy(eventId)
        return EventOccurrence(occurrenceId, eventId, origin, EventStatus.PENDING)
    }

    internal fun event(id: String) = requireNotNull(events[id]) { "Unknown event: $id" }
    internal fun storyProgress(state: GameState) = StoryProgress(content, policies, goals, campaign, state)
    internal fun policy(id: String) = requireNotNull(policies[id]) { "Missing authored event policy: $id" }
    internal fun meal(id: String) = mealPolicy.meal(id)
    internal fun choices(id: String) = content.choices.filter {
        it.eventId == id && it.id !in policy(id).disabledChoiceIds
    }.sortedBy { it.position }
    internal fun goalItemsForDay(dayId: String?): Set<String> {
        val day = requireNotNull(content.days.find { it.id == dayId }) { "Unknown story day: $dayId" }
        val chapter = requireNotNull(content.chapters.find { it.id == day.chapterId }) { "Unknown chapter" }
        return content.requiredItems.filter { it.goalId == chapter.goalId }.map { it.itemId }.toSet()
    }
}
