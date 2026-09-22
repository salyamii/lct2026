package ru.nksk.lctapp.data.game

import ru.nksk.lctapp.domain.content.EventType
import ru.nksk.lctapp.domain.content.ItemOperation
import ru.nksk.lctapp.domain.content.StoryContent

/** Append-only definition identity protects the meaning of historical decision JOINs. */
internal fun StoryContent.newDefinitionsComparedTo(old: StoryContent): StoryContent {
    val added = StoryContent(
        chapters = newRows(chapters, old.chapters) { it.id },
        days = newRows(days, old.days) { it.id },
        schedule = newRows(schedule, old.schedule) { it.id },
        events = newRows(events, old.events) { it.id },
        choices = newRows(choices, old.choices) { it.id },
        items = newRows(items, old.items) { it.id },
        goals = newRows(goals, old.goals) { it.id },
        requiredItems = newRows(requiredItems, old.requiredItems) { it.goalId to it.itemId },
        eventItemEffects = newRows(eventItemEffects, old.eventItemEffects) { it.id },
        choiceItemEffects = newRows(choiceItemEffects, old.choiceItemEffects) { it.id },
    )
    val oldEvents = old.events.map { it.id }.toSet()
    val oldChoices = old.choices.map { it.id }.toSet()
    val oldGoals = old.goals.map { it.id }.toSet()
    val oldDays = old.days.map { it.id }.toSet()
    require(added.choices.none { it.eventId in oldEvents } &&
        added.eventItemEffects.none { it.eventId in oldEvents } &&
        added.choiceItemEffects.none { it.choiceId in oldChoices } &&
        added.requiredItems.none { it.goalId in oldGoals } &&
        added.schedule.none { it.dayId in oldDays }
    ) { "Changing the children of an existing definition requires a new parent ID" }
    validateEarningCosts(old, added)
    return added
}

private fun <T, K> newRows(incoming: List<T>, existing: List<T>, key: (T) -> K): List<T> {
    require(incoming.map(key).distinct().size == incoming.size) { "Duplicate definition identity in content batch" }
    val byKey = existing.associateBy(key)
    return incoming.filter { row ->
        val previous = byKey[key(row)]
        require(previous == null || previous == row) { "Cannot change an existing definition: ${key(row)}" }
        previous == null
    }
}

/** Validate the combined catalog, including new requirements that could invalidate old rewards. */
internal fun validateStoryItemRewards(old: StoryContent, added: StoryContent) {
    val goalItems = (old.requiredItems + added.requiredItems).map { it.itemId }.toSet()
    val storyEvents = (old.events + added.events).filter { it.type == EventType.STORY }.map { it.id }.toSet()
    val storyChoices = (old.choices + added.choices).filter { it.eventId in storyEvents }.map { it.id }.toSet()
    require((old.eventItemEffects + added.eventItemEffects).none {
        it.eventId in storyEvents && it.itemId in goalItems && it.operation == ItemOperation.ADD
    } && (old.choiceItemEffects + added.choiceItemEffects).none {
        it.choiceId in storyChoices && it.itemId in goalItems && it.operation == ItemOperation.ADD
    }) { "STORY events cannot grant goal-required items (D-038)" }
}

/** An earning reward cannot hide a charge at entry or in any response. */
private fun validateEarningCosts(old: StoryContent, added: StoryContent) {
    val earnings = (old.events + added.events).filter { it.type == EventType.EARNING }
    val ids = earnings.map { it.id }.toSet()
    require(earnings.none { it.moneyDeltaOnStart < 0 } &&
        (old.choices + added.choices).none { it.eventId in ids && it.moneyDelta < 0 }
    ) { "EARNING events cannot require money at entry or in a choice" }
}
