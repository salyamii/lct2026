package ru.nksk.lctapp.domain.engine

import kotlinx.serialization.Serializable
import ru.nksk.lctapp.domain.content.EventType
import ru.nksk.lctapp.domain.game.GameState

/** Days between real presentations, not between drafting or re-reading a plan. */
data class EventSchedulingPolicy(
    val cooldownDays: Int = 0,
    val family: String? = null,
    val kind: EverydayEventKind = EverydayEventKind.OTHER,
    val mandatoryUnexpected: Boolean = false,
    val blocksStoryUntilResolved: Boolean = false,
    val previousEventIds: Set<String> = emptySet(),
    val earliestDay: Int = 1,
) {
    init { require(cooldownDays >= 0 && earliestDay > 0 && (family == null || family.isNotBlank()) && previousEventIds.all { it.isNotBlank() }) }
}

enum class EverydayEventKind { OTHER, WANT, UNEXPECTED, DISCOVERY }

/** Small persisted scheduling projection; full occurrences and outcomes remain in the audit. */
@Serializable
data class EventExposure(
    val eventId: String,
    val lastOfferedDay: Int? = null,
    val lastCompletedDay: Int? = null,
    val offerCount: Int = 0,
    val completionCount: Int = 0,
) {
    init {
        require(eventId.isNotBlank() && offerCount >= 0 && completionCount >= 0)
        require(lastOfferedDay == null || lastOfferedDay > 0)
        require(lastCompletedDay == null || lastCompletedDay > 0)
    }
}

object EventScheduling {
    /** Only an unshown everyday possibility may disappear when its original premise no longer exists. */
    internal fun retainOccurrence(occurrence: EventOccurrence, type: EventType, policy: EventPolicy,
        progress: StoryProgress): Boolean = occurrence.origin != EventOrigin.SCHEDULE || type == EventType.STORY ||
        occurrence.status !in setOf(EventStatus.PENDING, EventStatus.CARRIED) || progress.meets(policy.condition)

    internal fun retainOccurrence(state: GameState, occurrence: EventOccurrence, factory: EventFactory): Boolean =
        retainOccurrence(occurrence, factory.event(occurrence.eventId).type, factory.policy(occurrence.eventId),
            factory.storyProgress(state))

    /** Called only inside the successful aggregate transition, before its atomic write. */
    internal fun record(before: GameState, after: GameState, factory: EventFactory): GameState {
        val day = after.engine?.day ?: return after
        val entries = after.eventHistory.associateByTo(linkedMapOf()) { it.eventId }
        val oldEvents = before.engine?.events.orEmpty().associateBy { it.id }
        after.engine.events.filter { next ->
            next.origin == EventOrigin.SCHEDULE && next.status in setOf(EventStatus.ACTIVE, EventStatus.RESULT) &&
                (oldEvents[next.id] == null || oldEvents[next.id]?.status == EventStatus.PENDING)
        }.forEach { event ->
            val old = entries[event.eventId] ?: EventExposure(event.eventId)
            entries[event.eventId] = old.copy(lastOfferedDay = day, offerCount = Math.addExact(old.offerCount, 1))
        }
        val oldDecisions = before.story.decisions.map { it.id }.toSet()
        val choices = factory.content.choices.associateBy { it.id }
        after.story.decisions.filter { it.id !in oldDecisions }.forEach { decision ->
            val eventId = checkNotNull(choices[decision.choiceId]).eventId
            val old = entries[eventId] ?: EventExposure(eventId)
            entries[eventId] = old.copy(lastCompletedDay = day, completionCount = Math.addExact(old.completionCount, 1))
        }
        return if (entries.values.toList() == after.eventHistory) after else after.copy(eventHistory = entries.values.toList())
    }
}

/** Stable least-recently-offered rotation. No unseeded randomness or storage reads in planning. */
internal object EventScheduler {
    fun plan(catalog: GameCatalog, state: GameState): List<String> {
        val progress = catalog.storyProgress(state)
        val eventTypes = catalog.content.events.associate { it.id to it.type }
        val carried = state.engine?.events.orEmpty().filter {
            (it.status == EventStatus.CARRIED || it.status == EventStatus.CARRIED_ACTIVE) &&
                EventScheduling.retainOccurrence(it, eventTypes.getValue(it.eventId), catalog.policies.getValue(it.eventId), progress)
        }.map { it.eventId }.take(5)
        val selected = carried.toMutableList()
        val loreCount = carried.count { catalog.policies[it]?.storyActId != null }
        val introductionCompleted = progress.completed(catalog.introductionId)
        val story = if (catalog.storyCampaign != null) progress.nextEvent(carried.toSet())
            else catalog.introductionId.takeUnless {
                introductionCompleted || it in carried || (catalog.goals.isNotEmpty() && catalog.goals.selectedGoal(state) == null)
            }
        if (selected.size < 5 && loreCount < 2 && story != null) selected += story
        val target = maxOf(4, selected.size)
        val nextDay = (state.engine?.day ?: 0) + 1
        val exposures = state.eventHistory.associateBy { it.eventId }
        fun family(id: String) = catalog.policies[id]?.scheduling?.family ?: id
        val lastMandatory = state.eventHistory.filter {
            catalog.policies[it.eventId]?.scheduling?.mandatoryUnexpected == true
        }.mapNotNull { it.lastOfferedDay }.maxOrNull()
        val completedChoices = state.story.decisions.map { it.choiceId }.toSet()
        fun allowed(id: String): Boolean {
            if (id in selected || !progress.eligible(id)) return false
            if (!state.ownedItems.map { it.itemId }.toSet().containsAll(catalog.policies.getValue(id).requiredItemIds)) return false
            if (id in catalog.oneTimeEventIds && catalog.content.choices.any { it.eventId == id && it.id in completedChoices }) return false
            val scheduling = catalog.policies.getValue(id).scheduling
            if (nextDay < scheduling.earliestDay) return false
            if (scheduling.mandatoryUnexpected && lastMandatory != null && nextDay - lastMandatory < 2) return false
            val last = state.eventHistory.filter { it.eventId == id || it.eventId in scheduling.previousEventIds }
                .flatMap { listOfNotNull(it.lastOfferedDay, it.lastCompletedDay) }.maxOrNull()
            val cooldown = maxOf(1, catalog.policies.getValue(id).scheduling.cooldownDays)
            return last == null || nextDay - last >= cooldown
        }
        fun ranked(ids: List<String>): List<String> = ids.distinct().sortedWith(
            compareBy<String> { exposures[it]?.offerCount ?: 0 }
                .thenBy { exposures[it]?.lastOfferedDay ?: 0 }
                .thenBy { ids.indexOf(it) })
        fun deedAllowed(id: String): Boolean = allowed(id) && state.engine?.deeds.orEmpty().none {
            family(it.eventId) == family(id) && !it.completed && it.expiresDay >= nextDay
        }
        val hinted = catalog.storyCampaign?.deedHints?.firstOrNull {
            catalog.goals.selectedGoal(state) != null && progress.meets(it.condition) && deedAllowed(it.eventId)
        }?.eventId
        val alreadyHasDeed = selected.any { id -> catalog.content.events.any { it.id == id && it.type == EventType.EARNING } }
        if (selected.size < target && !alreadyHasDeed)
            (hinted ?: ranked(catalog.deedPool).firstOrNull(::deedAllowed))?.let(selected::add)

        while (selected.size < target) {
            val families = selected.mapNotNull { catalog.policies[it]?.scheduling?.family }.toSet()
            val kinds = selected.mapNotNull { catalog.policies[it]?.scheduling?.kind }.toSet()
            val hasMandatory = selected.any { catalog.policies[it]?.scheduling?.mandatoryUnexpected == true }
            val hasUnexpected = selected.any { catalog.policies[it]?.scheduling?.kind == EverydayEventKind.UNEXPECTED }
            val candidates = ranked(catalog.dailyEventPool).filter { id -> allowed(id) &&
                !(hasMandatory && catalog.policies.getValue(id).scheduling.mandatoryUnexpected) &&
                !(hasUnexpected && catalog.policies.getValue(id).scheduling.kind == EverydayEventKind.UNEXPECTED) }
            val distinctFamily = candidates.filter { catalog.policies.getValue(it).scheduling.family !in families }
            val chosen = distinctFamily.firstOrNull { catalog.policies.getValue(it).scheduling.kind !in kinds }
                ?: distinctFamily.firstOrNull() ?: candidates.firstOrNull()
            if (chosen == null) break
            selected += chosen
        }
        // Compatibility with small/legacy catalogs: fill missing slots with distinct available jobs,
        // preserving all guards and cooldowns. Rich everyday catalogs normally need only one job.
        for (id in ranked(catalog.deedPool)) {
            if (selected.size >= target) break
            if (deedAllowed(id)) selected += id
        }
        require(selected.size in 4..5) { "Not enough eligible content for day $nextDay; add everyday cards, do not bypass guards" }
        return selected
    }
}
