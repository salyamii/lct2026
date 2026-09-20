package ru.nksk.lctapp.domain.engine

import ru.nksk.lctapp.domain.content.EventType
import ru.nksk.lctapp.domain.content.ItemOperation
import ru.nksk.lctapp.domain.game.GameRepository
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.game.OwnedItem
import ru.nksk.lctapp.domain.story.StoryDecision
import ru.nksk.lctapp.domain.pet.PetVisualState
import ru.nksk.lctapp.domain.minigame.DeedGameScore
import ru.nksk.lctapp.domain.pet.isValidPetName

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

    /** Adopt the authored age rule for older saves, using the latest aggregate in the transaction. */
    internal suspend fun synchronizeStoryAge() {
        val saved = games.read() ?: return
        if (withStoryAge(saved) == saved) return
        games.update { current ->
            val next = withStoryAge(current)
            if (next == current) current else next.copy(engine = next.engine?.copy(
                revision = Math.addExact(next.engine.revision, 1L),
            ))
        }
    }

    private fun withStoryAge(state: GameState): GameState {
        val age = factory.storyProgress(state).petAge ?: return state
        return if (state.pet.age == age) state else state.copy(pet = state.pet.copy(age = age))
    }

    /** Deterministic transition; does not read clocks, generate IDs, observe flows or perform I/O. */
    internal fun transition(current: GameState, request: EngineRequest): GameState {
        ensure(current.engine?.revision == request.expectedRevision, BlockReason.StaleRevision)
        return try {
            require(current.engine == null || current.engine.rulesId == rules.id) { "Saved engine uses a different rules version" }
            val next = when (val command = request.command) {
                is EngineCommand.RenamePet -> {
                    ensure(current.pet.name == command.expectedName, BlockReason.StaleRevision)
                    val name = command.name.trim()
                    ensure(isValidPetName(name), BlockReason.InvalidPetName)
                    current.copy(pet = current.pet.copy(name = name))
                }
                is EngineCommand.SetPetColor -> {
                    ensure(current.pet.color == command.expectedColor, BlockReason.StaleRevision)
                    current.copy(pet = current.pet.copy(color = command.color))
                }
                is EngineCommand.BeginDay -> beginDay(current, command, request.id).let {
                    if (command.openFirst) openNext(it, request.id) else it
                }
                EngineCommand.OpenNextEvent -> openNext(current, request.id)
                is EngineCommand.SelectGoal -> selectGoal(current, command, request.id)
                is EngineCommand.BuyGoalItem -> buyGoalItem(current, command, request.id)
                is EngineCommand.Choose -> choose(current, command)
                is EngineCommand.CompleteEvent -> acknowledge(
                    choose(current, EngineCommand.Choose(command.occurrenceId, command.choiceId)),
                    command.occurrenceId,
                )
                is EngineCommand.AcknowledgeResult -> acknowledge(current, command.occurrenceId)
                is EngineCommand.StartDeed -> startDeed(current, command.offerId, request.id)
                is EngineCommand.AcceptDeedProposal -> acceptProposal(current, command, request.id)
                is EngineCommand.CompleteDeed -> completeDeed(current, command)
                is EngineCommand.DismissDeedProposal -> dismissProposal(current, command.occurrenceId)
                is EngineCommand.PauseEvent -> pause(current, command.occurrenceId)
                is EngineCommand.Feed -> feed(current, command.mealId)
                is EngineCommand.FinishDayFromEvent -> finishDayFromEvent(current, command.occurrenceId)
                EngineCommand.FinishDay -> finishDay(current)
            }
            val recorded = recordDayChanges(current, next, request, factory)
            recorded.copy(engine = recorded.engine?.copy(
                revision = Math.addExact(current.engine?.revision ?: -1L, 1L),
            ))
        } catch (invalid: IllegalArgumentException) {
            reject(BlockReason.InvalidContent(invalid.message ?: "Invalid authored content"))
        } catch (_: ArithmeticException) {
            reject(BlockReason.InvalidContent("Numeric overflow"))
        }
    }

    /** The UI can explain a guard without changing the save. Dispatch checks it again atomically. */
    fun blockReason(state: GameState, command: EngineCommand): BlockReason? = try {
        transition(state, EngineRequest("preview:${state.engine?.revision}", state.engine?.revision, command))
        null
    } catch (blocked: Rejected) { blocked.reason }

    fun availableDeeds(state: GameState): List<DeedOffer> = state.engine?.let { engine ->
        if (engine.phase == DayPhase.FINISHED) emptyList() else engine.deeds.filter { it.isAvailable(engine.day) }
    }.orEmpty()

    fun daySummary(state: GameState): DaySummary? = state.engine?.takeIf { it.phase == DayPhase.FINISHED }?.let { day ->
        val completedDecisionIds = day.events.filter { it.status == EventStatus.COMPLETED }
            .map { "${it.id}:decision" }.toSet()
        DaySummary(day.day, day.openingBalance, state.economy.balance, day.events.filter {
            it.status == EventStatus.COMPLETED && factory.event(it.eventId).type == EventType.STORY
        }.map { it.eventId }, day.steps, day.openingEnergy, day.energy, day.journal,
            state.story.decisions.filter { it.id in completedDecisionIds })
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
        val carried = previous?.events?.filter { it.status == EventStatus.CARRIED || it.status == EventStatus.CARRIED_ACTIVE }?.toMutableList() ?: mutableListOf()
        val carriedToday = carried.take(5)
        ensure(command.eventIds.take(carriedToday.size) == carriedToday.map { it.eventId }, BlockReason.MissingCarriedLore)
        val occurrences = command.eventIds.mapIndexed { position, eventId ->
            val existing = carried.firstOrNull { it.eventId == eventId }
            if (existing != null) {
                carried.remove(existing)
                existing.copy(status = if (existing.status == EventStatus.CARRIED_ACTIVE) EventStatus.PAUSED else EventStatus.PENDING)
            } else factory.create(eventId, "$requestId:event:$position")
        }
        ensure(carried.isEmpty() || occurrences.size == 5, BlockReason.MissingCarriedLore)
        val dayNumber = Math.addExact(previous?.day ?: 0, 1)
        // The first week's money already belongs to the new-save initializer; never re-grant it.
        val funded = if (previous != null && (dayNumber - 1) % 7 == 0) money(state, rules.weeklyIncome) else state
        return funded.copy(
            pet = if (previous != null && state.pet.visualState == PetVisualState.TIRED) state.pet.transitionTo(PetVisualState.NORMAL) else state.pet,
            story = state.story.copy(currentDayId = command.storyDayId, nextScriptPosition = null),
            engine = EngineState(
                rulesId = rules.id,
                revision = previous?.revision ?: 0,
                day = dayNumber,
                phase = DayPhase.RUNNING,
                steps = 0,
                energy = previous?.nextMorningEnergy ?: rules.fullEnergy,
                ateToday = false,
                nextMorningEnergy = null,
                openingBalance = state.economy.balance,
                // An explicit goal introduction can defer a sixth occurrence. Its identity waits intact.
                events = occurrences + carried,
                deeds = previous?.deeds.orEmpty(),
                openingEnergy = previous?.nextMorningEnergy ?: rules.fullEnergy,
            ),
        )
    }

    private fun openNext(current: GameState, requestId: String): GameState {
        val state = prepareIntroduction(current, requestId)
        val day = running(state)
        ensure(day.currentEvent == null, BlockReason.EventInProgress)
        val introduction = factory.goals.selectedGoal(state)?.introductionEventId
        val progress = factory.storyProgress(state)
        val paused = day.events.firstOrNull { it.status == EventStatus.PAUSED &&
            it.origin == EventOrigin.SCHEDULE && progress.eligible(it.eventId) }
        val occurrence = day.events.firstOrNull { it.eventId == introduction && progress.eligible(it.eventId) &&
            (it.status == EventStatus.PENDING || it.status == EventStatus.PAUSED) }
            ?: paused?.takeIf { progress.eligible(it.eventId) }
            ?: day.events.firstOrNull { it.status == EventStatus.PENDING && progress.eligible(it.eventId) }
            ?: reject(BlockReason.NoNextEvent)
        val event = factory.event(occurrence.eventId)
        entryGuard(state, occurrence.eventId)
        if (event.type == EventType.EARNING) {
            foodGuard(day)
            val cost = factory.policy(event.id).energyCost
            val offer = DeedOffer("${occurrence.id}:offer", event.id, Math.addExact(day.day, cost - 1))
            return replaceEvent(state, occurrence.copy(status = EventStatus.RESULT)).let { next ->
                next.copy(engine = next.engine!!.copy(steps = Math.addExact(day.steps, 1), deeds = day.deeds + offer))
            }
        }
        actionGuard(day, minimumEnergy(state, occurrence))
        val next = replaceEvent(state, occurrence.copy(status = EventStatus.ACTIVE))
        return if (occurrence.status != EventStatus.PAUSED && factory.policy(event.id).startEffectsTiming == EffectTiming.OPEN) {
            eventEffects(next, occurrence)
        } else next
    }

    private fun selectGoal(state: GameState, command: EngineCommand.SelectGoal, requestId: String): GameState {
        ensure(factory.goals.any { it.goalId == command.goalId && it.isAvailable(state) }, BlockReason.GoalUnavailable)
        ensure(state.selectedGoalId == null && factory.goals.selectedGoal(state) == null, BlockReason.GoalAlreadySelected)
        val started = if (state.engine == null) {
            val firstDay = command.firstDay ?: reject(BlockReason.DayNotStarted)
            require(!firstDay.openFirst)
            beginDay(state, firstDay, requestId)
        } else state
        // Selection spends neither coins nor a step, and does not open/replace the current event.
        return started.copy(selectedGoalId = command.goalId)
    }

    private fun buyGoalItem(state: GameState, command: EngineCommand.BuyGoalItem, requestId: String): GameState {
        val goal = factory.goals.selectedGoal(state)
        ensure(goal?.goalId == command.goalId && command.itemId in goal.itemIds, BlockReason.GoalUnavailable)
        val day = running(state)
        ensure(state.ownedItems.none { it.itemId == command.itemId }, BlockReason.ItemAlreadyOwned)
        foodGuard(day)
        val definition = factory.content.items.first { it.id == command.itemId }
        val paid = money(state, -checkNotNull(definition.priceCoins))
        val food = foodCostUntilWeekEnd(state, factory.basicMealPrice())
        ensure(command.acceptFoodRisk || paid.economy.balance >= food,
            BlockReason.FoodBudgetWarning(paid.economy.balance, food))
        return paid.copy(
            selectedGoalId = command.goalId,
            ownedItems = state.ownedItems + OwnedItem("$requestId:purchase", command.itemId),
            engine = day.copy(steps = Math.addExact(day.steps, 1)),
        )
    }

    /** Goal introduction is requested explicitly by selection; all everyday occurrences keep their order. */
    private fun prepareIntroduction(state: GameState, requestId: String): GameState {
        val day = running(state)
        if (day.currentEvent != null || factory.goals.isEmpty()) return state
        if (factory.campaign != null) {
            val selected = factory.goals.selectedGoal(state)
            val obsoleteInvitations = factory.goals.filter { it.legacyAcceptanceChoiceIds.isNotEmpty() &&
                it.goalId != selected?.goalId }.map { it.introductionEventId }
            // Earlier catalogs scheduled goal invitations before there was a separate chooser.
            // Retire only unopened invitations; keep every ordinary event and every committed result.
            val cleaned = state.copy(engine = day.copy(events = day.events.filterNot {
                it.eventId in obsoleteInvitations && it.status == EventStatus.PENDING
            }))
            return prepareStoryEvent(cleaned, requestId)
        }
        val goal = factory.goals.selectedGoal(state)
        if (goal == null) {
            // Only the old, never-opened goal invitation is obsolete. Other event types are untouched.
            val invitations = factory.goals.map { it.introductionEventId }.toSet()
            return state.copy(engine = day.copy(events = day.events.filterNot {
                it.eventId in invitations && it.status == EventStatus.PENDING
            }))
        }
        val seen = state.story.decisions.any { decision -> factory.content.choices.any {
            it.id == decision.choiceId && it.eventId == goal.introductionEventId
        } }
        if (seen || day.events.any { it.eventId == goal.introductionEventId }) return state
        if (day.phase == DayPhase.READY_TO_END) return state
        val introduction = factory.create(goal.introductionEventId, "$requestId:goal-introduction")
        val position = day.events.indexOfFirst { it.status == EventStatus.PENDING || it.status == EventStatus.PAUSED }
            .takeIf { it >= 0 } ?: day.events.size
        val events = day.events.toMutableList().apply { add(position, introduction) }
        val scheduledToday = events.count { it.origin == EventOrigin.SCHEDULE &&
            it.status != EventStatus.CARRIED && it.status != EventStatus.CARRIED_ACTIVE }
        if (scheduledToday > 5) {
            val postponed = events.indexOfLast { it.origin == EventOrigin.SCHEDULE &&
                it.status == EventStatus.PENDING && it.id != introduction.id }
            require(postponed >= 0) { "A full day needs an unopened occurrence to defer the introduction's extra slot" }
            events[postponed] = events[postponed].copy(status = EventStatus.CARRIED)
        }
        return state.copy(engine = day.copy(phase = DayPhase.RUNNING, events = events))
    }

    private fun prepareStoryEvent(state: GameState, requestId: String): GameState {
        val day = running(state)
        if (day.phase == DayPhase.READY_TO_END || factory.goals.selectedGoal(state) == null) return state
        // Preserve an old already scheduled introduction until its actual outcome is committed.
        val legacyIntroductions = factory.goals.filter { it.legacyAcceptanceChoiceIds.isNotEmpty() }.map { it.introductionEventId }
        val waiting = day.events.filter { it.status == EventStatus.PENDING || it.status == EventStatus.PAUSED }
        if (waiting.any { it.eventId in legacyIntroductions || factory.policy(it.eventId).storyActId != null }) return state
        val loreToday = day.events.count { factory.policy(it.eventId).storyActId != null &&
            it.status != EventStatus.CARRIED && it.status != EventStatus.CARRIED_ACTIVE }
        if (loreToday >= 2) return state
        val id = factory.storyProgress(state).nextEvent(day.events.map { it.eventId }.toSet()) ?: return state
        val event = factory.create(id, "$requestId:story")
        val position = day.events.indexOfFirst { it.status == EventStatus.PENDING }.takeIf { it >= 0 } ?: day.events.size
        val events = day.events.toMutableList().apply { add(position, event) }
        if (events.count { it.origin == EventOrigin.SCHEDULE && it.status != EventStatus.CARRIED && it.status != EventStatus.CARRIED_ACTIVE } > 5) {
            val postponed = events.indexOfLast { it.origin == EventOrigin.SCHEDULE && it.status == EventStatus.PENDING && it.id != event.id }
            require(postponed >= 0)
            events[postponed] = events[postponed].copy(status = EventStatus.CARRIED)
        }
        return state.copy(engine = day.copy(events = events))
    }

    private fun choose(current: GameState, command: EngineCommand.Choose, score: DeedGameScore? = null): GameState {
        // Compatibility: accepting an invitation already open in an older save remains an explicit choice.
        val legacyGoal = factory.goals.firstOrNull { goal ->
            current.engine?.currentEvent?.eventId == goal.introductionEventId &&
                command.choiceId in goal.legacyAcceptanceChoiceIds
        }
        val state = if (current.selectedGoalId == null && legacyGoal != null)
            current.copy(selectedGoalId = legacyGoal.goalId) else current
        val day = running(state)
        val occurrence = day.currentEvent
        ensure(occurrence?.id == command.occurrenceId && occurrence.status == EventStatus.ACTIVE, BlockReason.InvalidEventAction)
        checkNotNull(occurrence)
        val event = factory.event(occurrence.eventId)
        val policy = factory.policy(event.id)
        ensure(policy.deedGameKind == null ||
            (occurrence.origin == EventOrigin.DEED && score?.kind == policy.deedGameKind), BlockReason.InvalidEventAction)
        val choice = factory.choices(event.id).find { it.id == command.choiceId } ?: reject(BlockReason.InvalidEventAction)
        entryGuard(state, event.id)
        val energyCost = policy.energyFor(choice.id)
        actionGuard(day, energyCost)
        var next = if (policy.startEffectsTiming == EffectTiming.COMPLETE) eventEffects(state, occurrence) else state
        next = money(next, score?.reward(choice.moneyDelta) ?: choice.moneyDelta)
        choice.petStateAfter?.let { next = next.copy(pet = next.pet.transitionTo(it)) }
        factory.content.choiceItemEffects.filter { it.choiceId == choice.id }.sortedBy { it.position }.forEach {
            next = item(next, it.itemId, it.operation, "${occurrence.id}:choice-item:${it.id}")
        }
        if (event.nextChapterId != null) {
            ensure(owns(next, currentGoalItems(state)), BlockReason.ChapterGoalIncomplete)
            next = next.copy(story = next.story.copy(currentDayId = checkNotNull(policy.chapterEntryDayId), nextScriptPosition = null))
        }
        next = next.copy(
            story = next.story.copy(decisions = next.story.decisions + StoryDecision("${occurrence.id}:decision", choice.id)),
            engine = day.copy(
                energy = day.energy - energyCost,
                steps = Math.addExact(day.steps, 1),
                deeds = day.deeds.map { if (it.id == occurrence.deedOfferId) it.copy(completed = true) else it },
            ),
        )
        if (policy.finishesStoryAct) {
            val selected = factory.goals.selectedGoal(state) ?: reject(BlockReason.GoalUnavailable)
            ensure(selected.isAvailable(state) && selected.progress(next, factory.content).isCollected, BlockReason.ChapterGoalIncomplete)
            next = next.copy(selectedGoalId = null, completedGoalProjects = next.completedGoalProjects +
                CompletedGoalProject(selected.goalId, "${occurrence.id}:decision"))
            next = withStoryAge(next)
        }
        return replaceEvent(next, occurrence.copy(status = EventStatus.RESULT))
    }

    private fun acknowledge(state: GameState, id: String): GameState {
        val day = running(state)
        val event = day.currentEvent
        ensure(event?.id == id && event.status == EventStatus.RESULT, BlockReason.InvalidEventAction)
        val next = replaceEvent(state, checkNotNull(event).copy(status = EventStatus.COMPLETED))
        return next.copy(engine = next.engine!!.copy(phase = if (next.engine.events.any {
            it.status == EventStatus.PENDING || (it.status == EventStatus.PAUSED && it.origin == EventOrigin.SCHEDULE)
        }) {
            DayPhase.RUNNING
        } else DayPhase.READY_TO_END))
    }

    private fun acceptProposal(state: GameState, command: EngineCommand.AcceptDeedProposal, requestId: String): GameState {
        val occurrenceId = command.occurrenceId
        val occurrence = running(state).currentEvent
        ensure(occurrence?.id == occurrenceId && occurrence.origin == EventOrigin.SCHEDULE &&
            factory.event(occurrence.eventId).type == EventType.EARNING, BlockReason.InvalidEventAction)
        return startDeed(acknowledge(state, occurrenceId), "$occurrenceId:offer", requestId)
    }

    private fun completeDeed(state: GameState, command: EngineCommand.CompleteDeed): GameState {
        val occurrence = running(state).currentEvent
        ensure(occurrence?.id == command.occurrenceId && occurrence.origin == EventOrigin.DEED,
            BlockReason.InvalidEventAction)
        val eventId = checkNotNull(occurrence).eventId
        ensure(factory.policy(eventId).deedGameKind == command.score.kind, BlockReason.InvalidEventAction)
        val choice = factory.choices(eventId).singleOrNull() ?: reject(BlockReason.InvalidEventAction)
        return acknowledge(choose(state, EngineCommand.Choose(occurrence.id, choice.id), command.score), occurrence.id)
    }

    private fun pause(state: GameState, occurrenceId: String): GameState {
        val occurrence = running(state).currentEvent
        ensure(occurrence?.id == occurrenceId && occurrence.status == EventStatus.ACTIVE, BlockReason.InvalidEventAction)
        return replaceEvent(state, checkNotNull(occurrence).copy(status = EventStatus.PAUSED))
    }

    private fun dismissProposal(state: GameState, occurrenceId: String): GameState {
        val occurrence = running(state).currentEvent
        ensure(occurrence?.id == occurrenceId && occurrence.origin == EventOrigin.SCHEDULE &&
            factory.event(occurrence.eventId).type == EventType.EARNING, BlockReason.InvalidEventAction)
        val next = acknowledge(state, occurrenceId)
        return if (factory.policy(checkNotNull(occurrence).eventId).discardOfferOnDismiss) {
            next.copy(engine = next.engine!!.copy(deeds = next.engine.deeds.filterNot { it.id == "$occurrenceId:offer" }))
        } else next
    }

    private fun startDeed(state: GameState, offerId: String, requestId: String): GameState {
        val day = running(state)
        val active = day.currentEvent
        ensure(active == null || (active.origin == EventOrigin.DEED && active.deedOfferId == offerId &&
            active.status == EventStatus.ACTIVE), BlockReason.EventInProgress)
        val offer = day.deeds.find { it.id == offerId && it.isAvailable(day.day) } ?: reject(BlockReason.DeedUnavailable)
        val policy = factory.policy(offer.eventId)
        ensure(day.phase != DayPhase.READY_TO_END || policy.energyCost <= rules.shortDeedMaxEnergy, BlockReason.OnlyShortDeedsAfterSchedule)
        entryGuard(state, offer.eventId)
        actionGuard(day, policy.energyCost)
        if (active != null) return state
        day.events.find { it.deedOfferId == offer.id && it.status == EventStatus.PAUSED }?.let {
            return replaceEvent(state, it.copy(status = EventStatus.ACTIVE))
        }
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

    private fun finishDayFromEvent(state: GameState, occurrenceId: String): GameState {
        val day = running(state)
        val occurrence = day.currentEvent
        ensure(occurrence?.id == occurrenceId, BlockReason.InvalidEventAction)
        checkNotNull(occurrence)
        val proposal = occurrence.status == EventStatus.RESULT && occurrence.origin == EventOrigin.SCHEDULE &&
            factory.event(occurrence.eventId).type == EventType.EARNING
        ensure(occurrence.status == EventStatus.ACTIVE || proposal, BlockReason.InvalidEventAction)
        // Check energy before food: an unfed pet with a viable choice does not need early sleep.
        ensure(day.energy == 0 || minimumEnergy(state, occurrence) > day.energy, BlockReason.UnfinishedEvents)
        ensure(day.ateToday, BlockReason.MustEat)
        // A proposal already spent its step. Keep its offer/deadline; do not offer it again tomorrow.
        val deferred = if (proposal) acknowledge(state, occurrenceId) else pause(state, occurrenceId)
        return carryRemainingPlan(deferred)
    }

    private fun finishDay(current: GameState): GameState {
        val state = prepareIntroduction(current, "finish:${current.engine?.revision}")
        val day = running(state)
        ensure(day.currentEvent == null, BlockReason.EventInProgress)
        ensure(day.ateToday, BlockReason.MustEat)
        val pending = day.events.filter { it.status == EventStatus.PENDING ||
            (it.status == EventStatus.PAUSED && it.origin == EventOrigin.SCHEDULE) }
        val eligible = pending.filter { factory.storyProgress(state).eligible(it.eventId) }
        if (eligible.isNotEmpty()) {
            val next = eligible.firstOrNull { it.status == EventStatus.PAUSED } ?: eligible.first()
            ensure(day.energy == 0 || minimumEnergy(state, next) > day.energy, BlockReason.UnfinishedEvents)
        }
        return carryRemainingPlan(state)
    }

    private fun carryRemainingPlan(state: GameState): GameState {
        val day = running(state)
        return state.copy(engine = day.copy(
            phase = DayPhase.FINISHED,
            events = day.events.map { occurrence -> when {
                occurrence.status == EventStatus.PENDING -> occurrence.copy(status = EventStatus.CARRIED)
                occurrence.status == EventStatus.PAUSED && occurrence.origin == EventOrigin.SCHEDULE ->
                    occurrence.copy(status = EventStatus.CARRIED_ACTIVE)
                else -> occurrence
            } },
        ))
    }

    /** The paid option cannot prevent rest when only the work alternative is affordable. */
    private fun minimumEnergy(state: GameState, occurrence: EventOccurrence): Int {
        val policy = factory.policy(occurrence.eventId)
        if (policy.choiceEnergyCosts.isEmpty()) return policy.energyCost
        val event = factory.event(occurrence.eventId)
        val choices = factory.choices(event.id)
        val affordable = choices.filter { choice ->
            try {
                val priceOnStart = if (policy.startEffectsTiming == EffectTiming.COMPLETE ||
                    (policy.startEffectsTiming == EffectTiming.OPEN && occurrence.status == EventStatus.PENDING)) {
                    event.moneyDeltaOnStart
                } else 0L
                money(money(state, priceOnStart), choice.moneyDelta)
                true
            } catch (blocked: Rejected) {
                if (blocked.reason !is BlockReason.InsufficientMoney) throw blocked
                false
            }
        }
        return affordable.ifEmpty { choices }.minOf { policy.energyFor(it.id) }
    }

    private fun entryGuard(state: GameState, eventId: String) {
        val event = factory.event(eventId)
        val policy = factory.policy(eventId)
        ensure(factory.storyProgress(state).eligible(eventId),
            if (policy.finishesStoryAct && factory.goals.selectedGoal(state)?.progress(state, factory.content)?.isCollected != true)
                BlockReason.ChapterGoalIncomplete else BlockReason.StoryConditionsNotMet)
        policy.goalId?.let { requiredGoal ->
            ensure(state.selectedGoalId == requiredGoal || factory.goals.selectedGoal(state)?.goalId == requiredGoal,
                BlockReason.GoalUnavailable)
        }
        val missing = policy.requiredItemIds - state.ownedItems.map { it.itemId }.toSet()
        ensure(missing.isEmpty(), BlockReason.MissingItems(missing))
        policy.previousLoreEventId?.let { previous ->
            val completed = state.story.decisions.any { decision ->
                factory.content.choices.any { it.id == decision.choiceId && it.eventId == previous }
            }
            ensure(completed, BlockReason.PreviousLoreIncomplete)
        }
        if (event.nextChapterId != null) ensure(owns(state, currentGoalItems(state)), BlockReason.ChapterGoalIncomplete)
    }

    private fun currentGoalItems(state: GameState): Set<String> {
        if (factory.goals.isEmpty()) return factory.goalItemsForDay(state.story.currentDayId)
        val goal = factory.goals.selectedGoal(state) ?: reject(BlockReason.GoalUnavailable)
        return goal.itemIds.toSet()
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
