package ru.nksk.lctapp.domain.engine

import ru.nksk.lctapp.domain.content.EventType
import ru.nksk.lctapp.domain.content.ItemOperation
import ru.nksk.lctapp.domain.game.GameRepository
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.game.OwnedItem
import ru.nksk.lctapp.domain.story.StoryDecision
import ru.nksk.lctapp.domain.pet.PetVisualState

/** A single domain entry point. Room supplies the latest aggregate inside its write transaction. */
class GameEngine(
    private val games: GameRepository,
    private val factory: EventFactory,
    private val rules: EngineRules,
) {
    suspend fun dispatch(request: EngineRequest): EngineResult = try {
        EngineResult.Applied(games.update { current -> transition(current, request) })
    } catch (blocked: Rejected) {
        EngineResult.Blocked(blocked.reason)
    }

    /** Deterministic transition; does not read clocks, generate IDs, observe flows or perform I/O. */
    internal fun transition(current: GameState, request: EngineRequest): GameState {
        ensure(current.engine?.revision == request.expectedRevision, BlockReason.StaleRevision)
        return try {
            require(current.engine == null || current.engine.rulesId == rules.id) { "Saved engine uses a different rules version" }
            val next = when (val command = request.command) {
                is EngineCommand.BeginDay -> beginDay(current, command, request.id)
                EngineCommand.OpenNextEvent -> openNext(current, request.id)
                is EngineCommand.Choose -> choose(current, command)
                is EngineCommand.AcknowledgeResult -> acknowledge(current, command.occurrenceId)
                is EngineCommand.StartDeed -> startDeed(current, command.offerId, request.id)
                is EngineCommand.Feed -> feed(current, command.mealId)
                EngineCommand.FinishDay -> finishDay(current)
            }
            next.copy(engine = checkNotNull(next.engine).copy(
                revision = Math.addExact(current.engine?.revision ?: -1L, 1L),
            ))
        } catch (invalid: IllegalArgumentException) {
            reject(BlockReason.InvalidContent(invalid.message ?: "Invalid authored content"))
        } catch (_: ArithmeticException) {
            reject(BlockReason.InvalidContent("Numeric overflow"))
        }
    }

    fun availableDeeds(state: GameState): List<DeedOffer> = state.engine?.let { engine ->
        if (engine.phase == DayPhase.FINISHED) emptyList() else engine.deeds.filter { it.isAvailable(engine.day) }
    }.orEmpty()

    fun daySummary(state: GameState): DaySummary? = state.engine?.takeIf { it.phase == DayPhase.FINISHED }?.let { day ->
        DaySummary(day.day, day.openingBalance, state.economy.balance, day.events.filter {
            it.status == EventStatus.COMPLETED && factory.event(it.eventId).type == EventType.STORY
        }.map { it.eventId }, day.steps)
    }

    private fun beginDay(state: GameState, command: EngineCommand.BeginDay, requestId: String): GameState {
        val previous = state.engine
        ensure(previous == null || previous.phase == DayPhase.FINISHED, BlockReason.UnfinishedEvents)
        ensure(state.story.activeEventId == null, BlockReason.EventInProgress)
        require(previous != null || (state.story.currentDayId == null && state.story.decisions.isEmpty())) {
            "Existing story progress needs an explicit engine adoption policy"
        }
        require(command.eventIds.size in 4..5) { "Provide an authored plan of four or five events" }
        val nextStoryDay = requireNotNull(factory.content.days.find { it.id == command.storyDayId })
        state.story.currentDayId?.let { currentDayId ->
            val currentDay = requireNotNull(factory.content.days.find { it.id == currentDayId })
            require(currentDay.chapterId == nextStoryDay.chapterId) { "Chapter changes require a completed final event" }
        }
        val carried = previous?.events?.filter { it.status == EventStatus.CARRIED }?.toMutableList() ?: mutableListOf()
        val occurrences = command.eventIds.mapIndexed { position, eventId ->
            val existing = carried.firstOrNull { it.eventId == eventId }
            if (existing != null) {
                carried.remove(existing)
                existing.copy(status = EventStatus.PENDING)
            } else factory.create(eventId, "$requestId:event:$position")
        }
        ensure(carried.isEmpty(), BlockReason.MissingCarriedLore)
        return state.copy(
            pet = if (previous != null && state.pet.visualState == PetVisualState.TIRED) state.pet.transitionTo(PetVisualState.NORMAL) else state.pet,
            story = state.story.copy(currentDayId = command.storyDayId, nextScriptPosition = null),
            engine = EngineState(
                rulesId = rules.id,
                revision = previous?.revision ?: 0,
                day = Math.addExact(previous?.day ?: 0, 1),
                phase = DayPhase.RUNNING,
                steps = 0,
                energy = previous?.nextMorningEnergy ?: rules.fullEnergy,
                ateToday = false,
                nextMorningEnergy = null,
                openingBalance = state.economy.balance,
                events = occurrences,
                deeds = previous?.deeds.orEmpty(),
            ),
        )
    }

    private fun openNext(state: GameState, requestId: String): GameState {
        val day = running(state)
        ensure(day.currentEvent == null, BlockReason.EventInProgress)
        val occurrence = day.events.firstOrNull { it.status == EventStatus.PENDING } ?: reject(BlockReason.NoNextEvent)
        val event = factory.event(occurrence.eventId)
        entryGuard(state, occurrence.eventId)
        if (event.type == EventType.EARNING) {
            foodGuard(day)
            val cost = factory.policy(event.id).energyCost
            val offer = DeedOffer("$requestId:offer", event.id, Math.addExact(day.day, cost - 1))
            return replaceEvent(state, occurrence.copy(status = EventStatus.RESULT)).let { next ->
                next.copy(engine = next.engine!!.copy(steps = Math.addExact(day.steps, 1), deeds = day.deeds + offer))
            }
        }
        actionGuard(day, factory.policy(event.id).energyCost)
        val next = replaceEvent(state, occurrence.copy(status = EventStatus.ACTIVE))
        return if (factory.policy(event.id).startEffectsTiming == EffectTiming.OPEN) {
            eventEffects(next, occurrence)
        } else next
    }

    private fun choose(state: GameState, command: EngineCommand.Choose): GameState {
        val day = running(state)
        val occurrence = day.currentEvent
        ensure(occurrence?.id == command.occurrenceId && occurrence.status == EventStatus.ACTIVE, BlockReason.InvalidEventAction)
        checkNotNull(occurrence)
        val event = factory.event(occurrence.eventId)
        val policy = factory.policy(event.id)
        val choice = factory.choices(event.id).find { it.id == command.choiceId } ?: reject(BlockReason.InvalidEventAction)
        entryGuard(state, event.id)
        actionGuard(day, policy.energyCost)
        var next = if (policy.startEffectsTiming == EffectTiming.COMPLETE) eventEffects(state, occurrence) else state
        next = money(next, choice.moneyDelta)
        choice.petStateAfter?.let { next = next.copy(pet = next.pet.transitionTo(it)) }
        factory.content.choiceItemEffects.filter { it.choiceId == choice.id }.sortedBy { it.position }.forEach {
            next = item(next, it.itemId, it.operation, "${occurrence.id}:choice-item:${it.id}")
        }
        if (event.nextChapterId != null) {
            ensure(owns(next, factory.goalItemsForDay(state.story.currentDayId)), BlockReason.ChapterGoalIncomplete)
            next = next.copy(story = next.story.copy(currentDayId = checkNotNull(policy.chapterEntryDayId), nextScriptPosition = null))
        }
        next = next.copy(
            story = next.story.copy(decisions = next.story.decisions + StoryDecision("${occurrence.id}:decision", choice.id)),
            engine = day.copy(
                energy = day.energy - policy.energyCost,
                steps = Math.addExact(day.steps, 1),
                deeds = day.deeds.map { if (it.id == occurrence.deedOfferId) it.copy(completed = true) else it },
            ),
        )
        return replaceEvent(next, occurrence.copy(status = EventStatus.RESULT))
    }

    private fun acknowledge(state: GameState, id: String): GameState {
        val day = running(state)
        val event = day.currentEvent
        ensure(event?.id == id && event.status == EventStatus.RESULT, BlockReason.InvalidEventAction)
        val next = replaceEvent(state, checkNotNull(event).copy(status = EventStatus.COMPLETED))
        return next.copy(engine = next.engine!!.copy(phase = if (next.engine.events.any { it.status == EventStatus.PENDING }) {
            DayPhase.RUNNING
        } else DayPhase.READY_TO_END))
    }

    private fun startDeed(state: GameState, offerId: String, requestId: String): GameState {
        val day = running(state)
        ensure(day.currentEvent == null, BlockReason.EventInProgress)
        val offer = day.deeds.find { it.id == offerId && it.isAvailable(day.day) } ?: reject(BlockReason.DeedUnavailable)
        val policy = factory.policy(offer.eventId)
        ensure(day.phase != DayPhase.READY_TO_END || policy.energyCost <= rules.shortDeedMaxEnergy, BlockReason.OnlyShortDeedsAfterSchedule)
        entryGuard(state, offer.eventId)
        actionGuard(day, policy.energyCost)
        val event = factory.create(offer.eventId, "$requestId:deed", EventOrigin.DEED)
            .copy(status = EventStatus.ACTIVE, deedOfferId = offer.id)
        return state.copy(
            story = state.story.copy(activeEventId = event.eventId),
            engine = day.copy(events = day.events + event),
        )
    }

    private fun feed(state: GameState, mealId: String): GameState {
        val day = running(state)
        val meal = factory.meal(mealId)
        require(meal.nextMorningEnergy == null || meal.nextMorningEnergy <= rules.fullEnergy)
        val paid = money(state, -meal.price)
        return paid.copy(
            pet = meal.visualStateAfter?.let { paid.pet.transitionTo(it) } ?: paid.pet,
            engine = day.copy(ateToday = true, nextMorningEnergy = meal.nextMorningEnergy ?: day.nextMorningEnergy),
        )
    }

    private fun finishDay(state: GameState): GameState {
        val day = running(state)
        ensure(day.currentEvent == null, BlockReason.EventInProgress)
        ensure(day.ateToday, BlockReason.MustEat)
        val pending = day.events.filter { it.status == EventStatus.PENDING }
        if (pending.isNotEmpty()) {
            ensure(pending.all { factory.event(it.eventId).type == EventType.STORY }, BlockReason.UnfinishedEvents)
            ensure(day.energy == 0 || factory.policy(pending.first().eventId).energyCost > day.energy, BlockReason.UnfinishedEvents)
        }
        return state.copy(engine = day.copy(
            phase = DayPhase.FINISHED,
            events = day.events.map { if (it.status == EventStatus.PENDING) it.copy(status = EventStatus.CARRIED) else it },
        ))
    }

    private fun entryGuard(state: GameState, eventId: String) {
        val event = factory.event(eventId)
        val policy = factory.policy(eventId)
        val missing = policy.requiredItemIds - state.ownedItems.map { it.itemId }.toSet()
        ensure(missing.isEmpty(), BlockReason.MissingItems(missing))
        policy.previousLoreEventId?.let { previous ->
            val completed = state.story.decisions.any { decision ->
                factory.content.choices.any { it.id == decision.choiceId && it.eventId == previous }
            }
            ensure(completed, BlockReason.PreviousLoreIncomplete)
        }
        if (event.nextChapterId != null) ensure(owns(state, factory.goalItemsForDay(state.story.currentDayId)), BlockReason.ChapterGoalIncomplete)
    }

    private fun eventEffects(state: GameState, occurrence: EventOccurrence): GameState {
        val event = factory.event(occurrence.eventId)
        var next = money(state, event.moneyDeltaOnStart)
        event.petStateOnStart?.let { next = next.copy(pet = next.pet.transitionTo(it)) }
        factory.content.eventItemEffects.filter { it.eventId == event.id }.sortedBy { it.position }.forEach {
            next = item(next, it.itemId, it.operation, "${occurrence.id}:event-item:${it.id}")
        }
        return next
    }

    private fun item(state: GameState, itemId: String, operation: ItemOperation, occurrenceId: String): GameState {
        // Which copy/equipped item to remove is an open rule; never silently choose one.
        ensure(operation == ItemOperation.ADD, BlockReason.InvalidContent("Item removal needs an explicit ownership policy"))
        require(factory.content.items.any { it.id == itemId }) { "Unknown item: $itemId" }
        return state.copy(ownedItems = state.ownedItems + OwnedItem(occurrenceId, itemId))
    }

    private fun money(state: GameState, delta: Long): GameState {
        val balance = Math.addExact(state.economy.balance, delta)
        if (delta < 0 && balance < 0) reject(BlockReason.InsufficientMoney(Math.negateExact(balance)))
        return state.copy(economy = state.economy.copy(balance = balance))
    }

    private fun replaceEvent(state: GameState, event: EventOccurrence): GameState = state.copy(
        story = state.story.copy(activeEventId = if (event.status == EventStatus.ACTIVE || event.status == EventStatus.RESULT) event.eventId else null),
        engine = state.engine!!.copy(events = state.engine.events.map { if (it.id == event.id) event else it }),
    )

    private fun owns(state: GameState, ids: Set<String>) = state.ownedItems.map { it.itemId }.toSet().containsAll(ids)
    private fun running(state: GameState): EngineState = (state.engine ?: reject(BlockReason.DayNotStarted)).also {
        ensure(it.phase != DayPhase.FINISHED, BlockReason.DayFinished)
    }
    private fun foodGuard(day: EngineState) = ensure(day.ateToday || day.steps < rules.hungerBlocksAtStep, BlockReason.MustEat)
    private fun actionGuard(day: EngineState, energy: Int) {
        foodGuard(day)
        if ((day.energy < energy || day.energy == 0) && !day.ateToday) reject(BlockReason.MustEat)
        ensure(day.energy >= energy && day.energy > 0, BlockReason.MustSleep)
    }

    private fun ensure(condition: Boolean, reason: BlockReason) { if (!condition) reject(reason) }
    private fun reject(reason: BlockReason): Nothing = throw Rejected(reason)
    private class Rejected(val reason: BlockReason) : RuntimeException(null, null, false, false)
}
