package ru.nksk.lctapp.domain.engine

import ru.nksk.lctapp.domain.content.*

/** Compiles validated, immutable authored content; creating an occurrence never grants an outcome. */
class EventFactory(
    internal val content: StoryContent,
    policies: Map<String, EventPolicy>,
    meals: List<MealDefinition>,
) {
    private val events = content.events.associateBy { it.id }
    private val policies = policies.toMap()
    private val meals = meals.associateBy { it.id }

    init {
        require(events.size == content.events.size) { "Duplicate event identity" }
        require(this.meals.size == meals.size) { "Duplicate meal identity" }
        require(content.choices.map { it.id }.distinct().size == content.choices.size)
        require(content.choices.all { it.eventId in events })
        val goalItems = content.requiredItems.map { it.itemId }.toSet()
        for ((id, policy) in this.policies) {
            val definition = requireNotNull(events[id]) { "Unknown event policy: $id" }
            require(definition.minSatiety == null && definition.maxFatigue == null) {
                "Translate legacy satiety/fatigue thresholds to the selected rules before activating $id"
            }
            require(policy.requiredItemIds.all { item -> content.items.any { it.id == item } })
            require(policy.previousLoreEventId == null || events[policy.previousLoreEventId]?.type == EventType.STORY)
            val startItems = content.eventItemEffects.filter { it.eventId == id }
            val hasStartEffects = definition.moneyDeltaOnStart != 0L || definition.petStateOnStart != null || startItems.isNotEmpty()
            require(!hasStartEffects || policy.startEffectsTiming != null) { "Specify timing of event effects: $id" }
            if (definition.type == EventType.EARNING) {
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
    internal fun policy(id: String) = requireNotNull(policies[id]) { "Missing authored event policy: $id" }
    internal fun meal(id: String) = requireNotNull(meals[id]) { "Unknown meal: $id" }
    internal fun choices(id: String) = content.choices.filter { it.eventId == id }.sortedBy { it.position }
    internal fun goalItemsForDay(dayId: String?): Set<String> {
        val day = requireNotNull(content.days.find { it.id == dayId }) { "Unknown story day: $dayId" }
        val chapter = requireNotNull(content.chapters.find { it.id == day.chapterId }) { "Unknown chapter" }
        return content.requiredItems.filter { it.goalId == chapter.goalId }.map { it.itemId }.toSet()
    }
}
