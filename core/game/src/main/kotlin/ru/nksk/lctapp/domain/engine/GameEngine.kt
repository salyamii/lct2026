package ru.nksk.lctapp.domain.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.nksk.lctapp.domain.economy.*
import ru.nksk.lctapp.domain.content.EventType
import ru.nksk.lctapp.domain.content.ItemOperation
import ru.nksk.lctapp.domain.game.GameRepository
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.game.OwnedItem
import ru.nksk.lctapp.domain.story.StoryDecision
import ru.nksk.lctapp.domain.minigame.DeedGameScore
import ru.nksk.lctapp.domain.pet.isValidPetName
import ru.nksk.lctapp.domain.finance.FinancialPeriods
import ru.nksk.lctapp.domain.finance.FinancialTraining
import ru.nksk.lctapp.domain.finance.FinancialBudgetProjection
import ru.nksk.lctapp.domain.finance.FinancialQuestion
import ru.nksk.lctapp.domain.finance.FinancialQuestionKind
import ru.nksk.lctapp.domain.finance.FinancialMilestone
import ru.nksk.lctapp.domain.finance.PeriodBudgetReport

data class EventSpendingPreview(val quote: SpendingQuote, val kind: SpendingKind)

/** A single domain entry point. Room supplies the latest aggregate inside its write transaction. */
class GameEngine(
    private val games: GameRepository,
    private val factory: EventFactory,
    private val rules: EngineRules,
    private val contentVersion: String = rules.id,
    private val onApplied: (AppliedGameCommand) -> Unit = {},
    private val previewDemoMode: () -> Boolean = { false },
) {
    private val analytics = EngineAnalytics(factory, rules, contentVersion,
        { state, command -> previewWithMode(state, command, demoMode = false) },
        { state, occurrence, choice -> previewEventChoiceWithMode(state, occurrence, choice, demoMode = false) })
    suspend fun dispatch(request: EngineRequest): EngineResult = try {
        var applied: AppliedGameCommand? = null
        val practiceRequest = request.command as? EngineCommand.RequestFinancialPractice
        val practice = practiceRequest?.takeIf { it.kind == FinancialQuestionKind.PLAN_REVIEW }?.let {
            preparePractice(it)
        }
        val saved = games.commit(request, request.context,
            contentFingerprint = contentVersion,
            facts = { before, after, runId, sequence ->
                if (request.demoMode) emptyList() else analytics.facts(request, before, after, runId, sequence)
            }
        ) { current ->
            // Before the first day there is no engine revision. Bind a real transfer to the
            // balances and target the player confirmed, using the latest transactional save.
            // This is a write-concurrency guard, not a gameplay rule for counterfactual replay.
            val transferExpectation = when (val command = request.command) {
                is EngineCommand.DepositSavings -> command.expected
                is EngineCommand.WithdrawSavings -> command.expected
                else -> null
            }
            transferExpectation?.let { expected ->
                ensure(expected.availableBalance == current.economy.availableBalance &&
                    expected.savingsBalance == current.economy.savingsBalance &&
                    expected.selectedSavingItemId == current.selectedSavingItemId &&
                    (expected.allocation == null || expected.allocation == current.economy.plan),
                    BlockReason.StaleRevision)
            }
            // Reports are built from repository receipts, never from UI-supplied totals. A concurrent
            // change invalidates this read rather than combining historical facts with a newer save.
            if (practice != null) {
                ensure(practice.source == current, BlockReason.StaleRevision)
            } else if (practiceRequest?.kind == FinancialQuestionKind.PLAN_REVIEW) {
                // A resumed/standalone question could become a new review while its read was in flight.
                ensure(!needsPracticeReport(current, practiceRequest), BlockReason.StaleRevision)
            }
            transitionInternal(current, request, preparedPractice = practice).also { next ->
                applied = AppliedGameCommand(request, current, next)
            }
        }
        // A deduplicated request does not execute the transform. Failed/uncertain commits do not
        // reach this point, and a transient observer must never turn a saved action into an error.
        applied?.let { command ->
            try { onApplied(command.copy(after = saved)) } catch (_: Exception) { /* Presentation is best effort. */ }
        }
        EngineResult.Applied(saved)
    } catch (blocked: Rejected) {
        if (!request.demoMode) games.recordRejected(request, blocked.reason::class.simpleName ?: "Blocked", contentVersion)
        EngineResult.Blocked(blocked.reason)
    }

    private data class PreparedPractice(val source: GameState, val report: PeriodBudgetReport?)

    /** History decode and report projection complete before entering the aggregate write transaction. */
    private suspend fun preparePractice(command: EngineCommand.RequestFinancialPractice): PreparedPractice? =
        withContext(Dispatchers.Default) {
            val source = games.read() ?: return@withContext null
            if (!needsPracticeReport(source, command)) return@withContext null
            val history = games.readHistory()
            if (history.isNotEmpty()) ensure(
                history.lastOrNull { it.after != null }?.after == source, BlockReason.StaleRevision)
            val current = FinancialPeriods.adopt(source)
            PreparedPractice(source, FinancialBudgetProjection.reportPeriod(
                current, current.financial.currentPeriodId, history, factory.content))
        }

    private fun resumesPractice(previous: FinancialQuestion?, command: EngineCommand.RequestFinancialPractice): Boolean =
        if (command.series) previous != null && previous.kind == command.kind &&
            (!previous.correct || previous.series?.isLast == false) else previous != null && !previous.correct

    private fun needsPracticeReport(state: GameState, command: EngineCommand.RequestFinancialPractice): Boolean {
        if (command.kind != FinancialQuestionKind.PLAN_REVIEW) return false
        val current = FinancialPeriods.adopt(state)
        return current.financial.currentPeriod != null && !resumesPractice(current.financial.practice, command)
    }

    /** Adopt chapter bindings and age for older saves, using the latest aggregate in the transaction. */
    internal suspend fun synchronizeStoryAge() {
        val saved = games.read() ?: return
        if (campaignReconciliation(saved).applyTo(saved) == saved) return
        games.reconcileCampaign(::campaignReconciliation)
    }

    private fun withStoryAge(state: GameState): GameState {
        val age = factory.storyProgress(state).petAge ?: return state
        return if (state.pet.age == age) state else state.copy(pet = state.pet.copy(age = age))
    }

    /** Preserve old purchases, decisions and the running financial cycle when adopting chapter order. */
    private fun campaignReconciliation(state: GameState): CampaignReconciliation {
        val aged = withStoryAge(state)
        val unchanged = CampaignReconciliation(aged.pet.age, aged.selectedGoalId, aged.selectedSavingItemId)
        if (factory.campaign?.acts?.none { it.goalId != null } != false) return unchanged
        val required = factory.storyProgress(aged).requiredGoal
        val selected = factory.goals.selectedGoal(aged)
        if (aged.selectedGoalId != null && selected == null) return unchanged
        val goalId = if (selected != null) required?.goalId else null
        val target = aged.selectedSavingItemId?.takeIf { item ->
            required != null && required.goalId == goalId && item in required.itemIds && aged.ownedItems.none { it.itemId == item }
        }
        return CampaignReconciliation(aged.pet.age, goalId, target, rebindCurrentPeriod = true)
    }

    /** Deterministic transition; does not read clocks, generate IDs, observe flows or perform I/O. */
    internal fun transition(saved: GameState, request: EngineRequest,
        history: List<ru.nksk.lctapp.domain.history.AuditEntry> = emptyList()): GameState =
        transitionInternal(saved, request, history)

    /** Only isolated educational previews call this; dispatch has no way to supply the assumption. */
    internal fun simulateCompletedWork(saved: GameState, request: EngineRequest): GameState {
        require(request.command is EngineCommand.CompleteEvent)
        return transitionInternal(saved, request, assumeCompletedWork = true)
    }

    private fun transitionInternal(saved: GameState, request: EngineRequest,
        history: List<ru.nksk.lctapp.domain.history.AuditEntry> = emptyList(),
        assumeCompletedWork: Boolean = false, preparedPractice: PreparedPractice? = null): GameState {
        ensure(saved.engine?.revision == request.expectedRevision, BlockReason.StaleRevision)
        val current = FinancialPeriods.adopt(if (request.demoMode) withDemoEnergy(saved) else saved)
        return try {
            ensure(current.economy.planning == null && current.economy.unallocated == 0L ||
                request.command is EngineCommand.RenamePet || request.command is EngineCommand.SetPetColor || request.command is EngineCommand.SetPetLook ||
                request.command is EngineCommand.StartBudgetAllocation ||
                request.command is EngineCommand.ChangeBudgetAllocation ||
                request.command is EngineCommand.ConfirmBudget,
                BlockReason.BudgetPlanningRequired)
            require(current.engine == null || current.engine.rulesId == rules.id) { "Saved engine uses a different rules version" }
            val next = when (val command = request.command) {
                is EngineCommand.StartBudgetAllocation -> current.copy(economy =
                    EconomyOperations.startAllocation(current.economy, command.sessionId, command.revision))
                is EngineCommand.ChangeBudgetAllocation -> {
                    val economy = if (command.startManual && current.economy.planning == null)
                        EconomyOperations.beginManual(current.economy, command.sessionId) else current.economy
                    val revision = if (command.increase != null && economy.planning?.id == command.sessionId)
                        economy.planning.revision else command.revision
                    val knownNeeds = factory.mealPolicy.foodRequirement(current)
                    current.copy(economy = if (command.amount != null)
                        EconomyOperations.setAllocation(economy, command.sessionId, revision, command.section, command.amount, knownNeeds)
                    else EconomyOperations.adjustAllocation(economy, command.sessionId, revision, command.section, checkNotNull(command.increase), knownNeeds))
                }
                is EngineCommand.ConfirmBudget -> {
                    val knownNeeds = factory.mealPolicy.foodRequirement(current)
                    val confirmed = current.copy(economy = EconomyOperations.confirm(current.economy, command.sessionId, command.revision, knownNeeds))
                    FinancialPeriods.confirmed(current, confirmed, request, command, knownNeeds)
                }
                is EngineCommand.DepositSavings -> {
                    val deposited = EconomyOperations.deposit(current.economy, command.amount)
                    val needed = factory.mealPolicy.foodRequirement(current)
                    ensure(command.acceptFoodRisk || deposited.availableBalance >= needed,
                        BlockReason.FoodBudgetWarning(deposited.availableBalance, needed))
                    current.copy(economy = deposited)
                }
                is EngineCommand.WithdrawSavings -> current.copy(economy =
                    EconomyOperations.withdraw(current.economy, command.amount, command.confirmed))
                is EngineCommand.RequestFinancialPractice -> {
                    val previous = current.financial.practice
                    val purchase = factory.content.choices.firstOrNull {
                        it.moneyDelta < 0 && factory.event(it.eventId).type == EventType.WANT
                    }
                    val resume = resumesPractice(previous, command)
                    if (resume) current else if (command.series && current.financial.currentPeriod == null) {
                        current.copy(financial = current.financial.copy(practice = FinancialTraining.standalone(command.kind, request.id)))
                    } else {
                        val first = FinancialPeriods.question(current, "${request.id}:question", command.kind,
                            knownNeeds = factory.mealPolicy.foodRequirement(current),
                            purchasePrice = purchase?.let { Math.negateExact(it.moneyDelta) }
                                ?: 7L.takeIf { command.series },
                            purchaseTitle = purchase?.let { factory.event(it.eventId).title },
                            budgetReport = if (command.kind != FinancialQuestionKind.PLAN_REVIEW) null
                                else if (preparedPractice != null) preparedPractice.report
                                else FinancialBudgetProjection.reportPeriod(current,
                                    current.financial.currentPeriodId, history, factory.content))
                        current.copy(financial = current.financial.copy(practice =
                            if (command.series) FinancialTraining.start(first, request.id) else first))
                    }
                }
                is EngineCommand.AnswerFinancialQuestion -> {
                    val question = current.financial.practice
                    ensure(question != null && question.id == command.questionId && !question.correct &&
                        question.options.any { it.id == command.answerId }, BlockReason.InvalidEventAction)
                    current.copy(financial = current.financial.copy(practice = checkNotNull(question).copy(
                        answeredOptionId = command.answerId, usedHint = question.usedHint || command.usedHint,
                        attempts = Math.addExact(question.attempts, 1))))
                }
                is EngineCommand.AdvanceFinancialPractice -> {
                    val question = current.financial.practice
                    ensure(question != null && question.id == command.questionId && question.correct &&
                        question.series?.isLast == false, BlockReason.InvalidEventAction)
                    current.copy(financial = current.financial.copy(practice = FinancialTraining.advance(checkNotNull(question))))
                }
                EngineCommand.CloseFinancialPractice -> if (current.financial.practice?.series != null) current
                    else current.copy(financial = current.financial.copy(practice = null))
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
                is EngineCommand.SetPetLook -> {
                    ensure(current.pet.selectedLookId == command.expectedLookId, BlockReason.StaleRevision)
                    ensure(ru.nksk.lctapp.domain.pet.PetCosmetics.canEquip(current, command.lookId), BlockReason.InvalidEventAction)
                    current.copy(pet = current.pet.copy(selectedLookId = command.lookId))
                }
                is EngineCommand.BeginDay -> beginDay(current, command, request.id).let {
                    if (command.openFirst && it.economy.planning == null) openNext(it, request.id, request.demoMode) else it
                }
                EngineCommand.OpenNextEvent -> openNext(current, request.id, request.demoMode)
                is EngineCommand.SelectGoal -> selectGoal(current, command, request.id)
                is EngineCommand.SelectSavingGoal -> selectSavingGoal(current, command, request.id)
                is EngineCommand.BuyGoalItem -> buyGoalItem(current, command, request.id, request.demoMode)
                is EngineCommand.Choose -> choose(current, command, demoMode = request.demoMode)
                is EngineCommand.CompleteEvent -> acknowledge(
                    choose(current, EngineCommand.Choose(command.occurrenceId, command.choiceId),
                        assumeCompletedWork = assumeCompletedWork, demoMode = request.demoMode),
                    command.occurrenceId,
                )
                is EngineCommand.AcknowledgeResult -> acknowledge(current, command.occurrenceId)
                is EngineCommand.StartDeed -> startDeed(current, command.offerId, request.id, request.demoMode)
                is EngineCommand.AcceptDeedProposal -> acceptProposal(current, command, request.id, request.demoMode)
                is EngineCommand.CompleteDeed -> completeDeed(current, command, request.demoMode)
                is EngineCommand.StartStoryGame -> startStoryGame(current, command, request.demoMode)
                is EngineCommand.CompleteStoryGame -> completeStoryGame(current, command, request.demoMode)
                is EngineCommand.SkipMiniGame -> {
                    ensure(request.demoMode, BlockReason.InvalidEventAction)
                    skipMiniGame(current, command)
                }
                is EngineCommand.DismissDeedProposal -> dismissProposal(current, command.occurrenceId)
                is EngineCommand.PauseEvent -> pause(current, command.occurrenceId)
                is EngineCommand.Feed -> feed(current, command.mealId, request.demoMode)
                is EngineCommand.FinishDayFromEvent -> finishDayFromEvent(current, command.occurrenceId, request.demoMode)
                EngineCommand.FinishDay -> finishDay(current, request.demoMode)
            }
            val outcome = if (request.demoMode) withDemoEnergy(next) else next
            val visualOutcome = PetVisualLifecycle.afterTransition(current, outcome, request.command, factory, rules)
            val recorded = FinancialPeriods.record(current,
                FinancialPeriods.adopt(recordDayChanges(current, visualOutcome, request, factory), imported = false), request)
            EventScheduling.record(current, recorded, factory).copy(engine = recorded.engine?.copy(
                revision = Math.addExact(current.engine?.revision ?: -1L, 1L),
            ))
        } catch (invalid: EconomyViolation) {
            reject(when (invalid.reason) {
                EconomyFailure.PLANNING_REQUIRED -> BlockReason.BudgetPlanningRequired
                EconomyFailure.INSUFFICIENT_MONEY -> BlockReason.InsufficientMoney(invalid.missing)
                EconomyFailure.STALE_SESSION -> BlockReason.StaleRevision
                EconomyFailure.CONFIRMATION_REQUIRED -> BlockReason.SavingsWithdrawalConfirmationRequired
                else -> BlockReason.InvalidContent(invalid.message ?: "Invalid economy operation")
            })
        } catch (invalid: IllegalArgumentException) {
            reject(BlockReason.InvalidContent(invalid.message ?: "Invalid authored content"))
        } catch (_: ArithmeticException) {
            reject(BlockReason.InvalidContent("Numeric overflow"))
        }
    }

    /** The UI can explain a guard without changing the save. Dispatch checks it again atomically. */
    fun blockReason(state: GameState, command: EngineCommand, demoMode: Boolean = previewDemoMode()): BlockReason? = try {
        transition(state, EngineRequest("preview:${state.engine?.revision}", state.engine?.revision, command, demoMode = demoMode))
        null
    } catch (blocked: Rejected) { blocked.reason }

    /** Side-effect-free result for explaining a real alternative before it is chosen. */
    fun preview(state: GameState, command: EngineCommand): EngineResult =
        previewWithMode(state, command, previewDemoMode())

    private fun previewWithMode(state: GameState, command: EngineCommand, demoMode: Boolean): EngineResult = try {
        EngineResult.Applied(transition(state, EngineRequest("preview:${state.engine?.revision}", state.engine?.revision, command,
            demoMode = demoMode)))
    } catch (blocked: Rejected) { EngineResult.Blocked(blocked.reason) }

    /** Hypothetical fixed cost after finishing manual work, never a played result or a write. */
    fun previewEventChoice(state: GameState, occurrenceId: String, choiceId: String): EngineResult =
        previewEventChoiceWithMode(state, occurrenceId, choiceId, previewDemoMode())

    fun previewEventChoice(state: GameState, occurrenceId: String, choiceId: String,
        demoMode: Boolean): EngineResult =
        previewEventChoiceWithMode(state, occurrenceId, choiceId, demoMode)

    private fun previewEventChoiceWithMode(state: GameState, occurrenceId: String, choiceId: String,
        demoMode: Boolean): EngineResult = try {
        EngineResult.Applied(simulateCompletedWork(state, EngineRequest("preview-work:${state.engine?.revision}", state.engine?.revision,
            EngineCommand.CompleteEvent(occurrenceId, choiceId), demoMode = demoMode)))
    } catch (blocked: Rejected) { EngineResult.Blocked(blocked.reason) }

    fun availableDeeds(state: GameState): List<DeedOffer> = state.engine?.let { engine ->
        if (engine.phase == DayPhase.FINISHED) emptyList() else engine.deeds.filter { it.isAvailable(engine.day) }
    }.orEmpty()

    fun daySummary(state: GameState): DaySummary? = state.engine?.takeIf { it.phase == DayPhase.FINISHED }?.let { day ->
        val completedDecisionIds = day.events.filter { it.status == EventStatus.COMPLETED }
            .map { "${it.id}:decision" }.toSet()
        DaySummary(day.day, day.openingBalance, state.economy.balance, day.events.filter {
            it.status == EventStatus.COMPLETED && factory.event(it.eventId).type == EventType.STORY
        }.map { it.eventId }, day.steps, day.openingEnergy, day.energy, day.journal,
            state.story.decisions.filter { it.id in completedDecisionIds }, day.balanceAdjustment)
    }

    private fun beginDay(state: GameState, command: EngineCommand.BeginDay, requestId: String): GameState {
        val previous = state.engine
        ensure(previous == null || previous.phase == DayPhase.FINISHED, BlockReason.UnfinishedEvents)
        ensure(state.story.activeEventId == null, BlockReason.EventInProgress)
        require(previous != null || (state.story.currentDayId == null && state.story.decisions.isEmpty())) {
            "Existing story progress needs an explicit engine adoption policy"
        }
        val focusedStory = factory.storyProgress(state).goalReadyForStory
        require(command.eventIds.size in (if (focusedStory) 0..5 else 4..5)) {
            "Provide an authored day plan; only a ready chapter goal permits a short story plan"
        }
        val nextStoryDay = requireNotNull(factory.content.days.find { it.id == command.storyDayId })
        state.story.currentDayId?.let { currentDayId ->
            val currentDay = requireNotNull(factory.content.days.find { it.id == currentDayId })
            require(currentDay.chapterId == nextStoryDay.chapterId) { "Chapter changes require a completed final event" }
        }
        val carried = previous?.events?.filter { (it.status == EventStatus.CARRIED || it.status == EventStatus.CARRIED_ACTIVE) &&
            EventScheduling.retainOccurrence(state, it, factory) }?.toMutableList() ?: mutableListOf()
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
        val funded = if (previous != null && (dayNumber - 1) % 7 == 0) state.copy(economy = EconomyOperations.weekly(state.economy, "week:$dayNumber", rules.weeklyIncome)) else state
        return funded.copy(
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

    private fun openNext(current: GameState, requestId: String, demoMode: Boolean = false): GameState {
        val state = prepareIntroduction(current, requestId)
        val day = running(state)
        ensure(day.currentEvent == null, BlockReason.EventInProgress)
        val occurrence = nextOccurrenceOrNull(state) ?: run {
            // A story prerequisite may already be in Deeds. Resume its original offer, without
            // offering it twice, spending another proposal step, or granting its facts for free.
            val offer = nextGoalDeedOffer(state) ?: reject(BlockReason.NoNextEvent)
            return startDeed(state, offer.id, requestId, demoMode)
        }
        val event = factory.event(occurrence.eventId)
        entryGuard(state, occurrence.eventId, demoMode)
        if (event.type == EventType.EARNING) {
            foodGuard(day)
            val cost = factory.policy(event.id).energyCost
            val offer = DeedOffer("${occurrence.id}:offer", event.id, Math.addExact(day.day, cost - 1))
            return replaceEvent(state, occurrence.copy(status = EventStatus.RESULT)).let { next ->
                next.copy(engine = next.engine!!.copy(steps = Math.addExact(day.steps, 1), deeds = day.deeds + offer))
            }
        }
        val canEatHere = factory.policy(event.id).feedsPetChoiceIds.any { choiceId ->
            factory.choices(event.id).any { it.id == choiceId &&
                state.economy.availableBalance >= -choiceMoneyDelta(it.id, demoMode) }
        }
        if (!canEatHere) actionGuard(day, minimumEnergy(state, occurrence, demoMode))
        val next = replaceEvent(state, occurrence.copy(status = EventStatus.ACTIVE))
        return if (occurrence.status != EventStatus.PAUSED && factory.policy(event.id).startEffectsTiming == EffectTiming.OPEN) {
            eventEffects(next, occurrence, demoMode)
        } else next
    }

    private fun nextOccurrence(state: GameState): EventOccurrence = nextOccurrenceOrNull(state)
        ?: reject(BlockReason.NoNextEvent)

    private fun nextOccurrenceOrNull(state: GameState): EventOccurrence? {
        val day = running(state)
        val introduction = factory.goals.selectedGoal(state)?.introductionEventId
        val progress = factory.storyProgress(state)
        if (progress.goalReadyForStory) {
            val waiting = day.events.filter { it.origin == EventOrigin.SCHEDULE &&
                it.status in setOf(EventStatus.PENDING, EventStatus.PAUSED) && progress.eligible(it.eventId) }
            return waiting.firstOrNull { factory.policy(it.eventId).storyActId == progress.currentAct?.id }
                ?: waiting.firstOrNull { it.status == EventStatus.PAUSED }
                ?: waiting.firstOrNull { progress.isGoalDeed(it.eventId) }
        }
        val paused = day.events.firstOrNull { it.status == EventStatus.PAUSED &&
            it.origin == EventOrigin.SCHEDULE && progress.eligible(it.eventId) }
        return day.events.firstOrNull { it.eventId == introduction && progress.eligible(it.eventId) &&
            (it.status == EventStatus.PENDING || it.status == EventStatus.PAUSED) }
            ?: paused?.takeIf { progress.eligible(it.eventId) }
            ?: day.events.firstOrNull { it.status == EventStatus.PENDING && progress.eligible(it.eventId) }
    }

    private fun nextGoalDeedOffer(state: GameState): DeedOffer? {
        val progress = factory.storyProgress(state)
        val day = state.engine ?: return null
        return day.deeds.firstOrNull { progress.isGoalDeed(it.eventId) && it.isAvailable(day.day) }
    }

    /** Cost of opening the next scheduled card, before Continue applies its entry effects. */
    fun nextEventSpending(state: GameState): SpendingQuote? = nextEventSpendingPreview(state)?.quote

    /** The same authored purchase/food classification drives payment, admission and displayed quotes. */
    fun choiceMoneyDelta(choiceId: String, demoMode: Boolean = previewDemoMode()): Long =
        factory.choiceMoneyDelta(choiceId, demoMode)

    fun nextEventSpendingPreview(state: GameState, demoMode: Boolean = previewDemoMode()): EventSpendingPreview? = try {
        if (state.economy.planning != null || state.engine == null || state.engine.phase == DayPhase.FINISHED ||
            state.engine.currentEvent != null) null
        else {
            val prepared = prepareIntroduction(state, "quote:${state.engine.revision}")
            val occurrence = nextOccurrence(prepared)
            val event = factory.event(occurrence.eventId)
            if (occurrence.status == EventStatus.PAUSED || factory.policy(event.id).startEffectsTiming != EffectTiming.OPEN ||
                event.moneyDeltaOnStart >= 0) null
            else {
                val kind = SpendingKind.forEvent(event.type)
                EventSpendingPreview(EconomyOperations.quote(state.economy,
                    Math.negateExact(factory.eventMoneyDelta(event.id, demoMode)), kind), kind)
            }
        }
    } catch (_: Rejected) { null }

    /** Simulate only day preparation, never its first event or any repository write. */
    fun advanceSpending(state: GameState, command: EngineCommand?, demoMode: Boolean = previewDemoMode()): EventSpendingPreview? = try {
        if (state.economy.planning != null || state.economy.unallocated != 0L) null
        else when (command) {
            EngineCommand.OpenNextEvent -> nextEventSpendingPreview(state, demoMode)
            is EngineCommand.BeginDay -> if (command.openFirst)
                nextEventSpendingPreview(beginDay(state, command.copy(openFirst = false), "quote:day"), demoMode) else null
            else -> null
        }
    } catch (_: Rejected) { null }

    private fun selectGoal(state: GameState, command: EngineCommand.SelectGoal, requestId: String): GameState {
        ensure(factory.goals.any { it.goalId == command.goalId && factory.storyProgress(state).goalAvailable(it) }, BlockReason.GoalUnavailable)
        ensure(state.selectedGoalId == null && factory.goals.selectedGoal(state) == null, BlockReason.GoalAlreadySelected)
        val started = if (state.engine == null) {
            val firstDay = command.firstDay ?: reject(BlockReason.DayNotStarted)
            require(!firstDay.openFirst)
            beginDay(state, firstDay, requestId)
        } else state
        // Selection spends neither coins nor a step, and does not open/replace the current event.
        return started.copy(selectedGoalId = command.goalId, economy =
            EconomyOperations.beginManual(started.economy, "$requestId:period-plan"))
    }

    private fun selectSavingGoal(state: GameState, command: EngineCommand.SelectSavingGoal, requestId: String): GameState {
        val goal = factory.goals.firstOrNull { it.goalId == command.goalId } ?: reject(BlockReason.GoalUnavailable)
        ensure(factory.storyProgress(state).goalAvailable(goal) && command.itemId in goal.itemIds, BlockReason.GoalUnavailable)
        ensure(state.ownedItems.none { it.itemId == command.itemId }, BlockReason.ItemAlreadyOwned)
        val started = if (factory.goals.selectedGoal(state) == null)
            selectGoal(state, EngineCommand.SelectGoal(command.goalId, command.firstDay), requestId) else state
        ensure(factory.goals.selectedGoal(started)?.goalId == command.goalId, BlockReason.GoalUnavailable)
        return started.copy(selectedSavingItemId = command.itemId)
    }

    private fun buyGoalItem(state: GameState, command: EngineCommand.BuyGoalItem, requestId: String,
        demoMode: Boolean = false): GameState {
        val goal = factory.goals.selectedGoal(state)
        ensure(goal?.goalId == command.goalId && command.itemId in goal.itemIds, BlockReason.GoalUnavailable)
        ensure(goal != null && factory.storyProgress(state).goalAvailable(goal), BlockReason.GoalUnavailable)
        val day = running(state)
        ensure(state.ownedItems.none { it.itemId == command.itemId }, BlockReason.ItemAlreadyOwned)
        // Old catalogs without chapter bindings retain their historical purchase behavior.
        ensure(factory.storyProgress(state).requiredGoal == null || state.selectedSavingItemId == command.itemId,
            BlockReason.GoalUnavailable)
        foodGuard(day)
        val definition = factory.content.items.first { it.id == command.itemId }
        val paid = state.copy(economy = EconomyOperations.purchaseGoal(state.economy,
            if (demoMode) 0L else checkNotNull(definition.priceCoins)))
        val food = factory.mealPolicy.foodRequirement(state)
        ensure(demoMode || command.acceptFoodRisk || paid.economy.availableBalance >= food,
            BlockReason.FoodBudgetWarning(paid.economy.availableBalance, food))
        return paid.copy(
            selectedGoalId = command.goalId,
            selectedSavingItemId = null,
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
        if (factory.storyProgress(state).goalReadyForStory) return prepareGoalStory(state, requestId)
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

    private fun prepareGoalStory(state: GameState, requestId: String): GameState {
        val day = running(state)
        val progress = factory.storyProgress(state)
        val events = day.events.filter { EventScheduling.retainOccurrence(it, factory.event(it.eventId).type,
            factory.policy(it.eventId), progress) }.toMutableList()
        val waiting = events.filter { it.status in setOf(EventStatus.PENDING, EventStatus.PAUSED) }
        val hasStory = waiting.any { factory.policy(it.eventId).storyActId == progress.currentAct?.id && progress.eligible(it.eventId) }
        if (!hasStory) {
            val next = progress.nextEvent(events.map { it.eventId }.toSet())
                ?: progress.goalDeedIds().firstOrNull { id ->
                    events.none { it.eventId == id && it.status != EventStatus.COMPLETED } &&
                        nextGoalDeedOffer(state) == null
                }
            if (next != null) events += factory.create(next, "$requestId:goal-story")
        }
        // A drained ordinary plan is not the end of a ready goal's story. Keep the day open,
        // including when an existing prerequisite offer must be resumed; normal care guards apply.
        return state.copy(engine = day.copy(phase = DayPhase.RUNNING, events = events))
    }

    private fun choose(current: GameState, command: EngineCommand.Choose, score: DeedGameScore? = null,
        assumeCompletedWork: Boolean = false, demoMode: Boolean = false): GameState {
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
            (occurrence.origin == EventOrigin.DEED && (score?.kind == policy.deedGameKind || assumeCompletedWork)),
            BlockReason.InvalidEventAction)
        val choice = factory.choices(event.id).find { it.id == command.choiceId } ?: reject(BlockReason.InvalidEventAction)
        policy.choiceGameKinds[choice.id]?.let { kind ->
            ensure(event.type in setOf(EventType.STORY, EventType.RANDOM, EventType.WANT) && occurrence.origin == EventOrigin.SCHEDULE &&
                (score?.kind == kind || assumeCompletedWork),
                BlockReason.InvalidEventAction)
        }
        entryGuard(state, event.id, demoMode)
        val energyCost = policy.energyFor(choice.id)
        val energyRestore = policy.choiceEnergyRestores[choice.id] ?: 0
        val remainingEnergy = day.energy - energyCost
        val nextEnergy = if (energyRestore == 0) remainingEnergy else
            (remainingEnergy.toLong() + energyRestore).coerceAtMost(rules.fullEnergy.toLong()).toInt()
        if (choice.id in policy.feedsPetChoiceIds) ensure(day.energy > 0 || energyCost == 0, BlockReason.MustSleep)
        else actionGuard(day, energyCost)
        var next = if (policy.startEffectsTiming == EffectTiming.COMPLETE) eventEffects(state, occurrence, demoMode) else state
        val delta = choiceMoneyDelta(choice.id, demoMode)
        val reward = if (event.type == EventType.EARNING) score?.reward(delta) ?: delta else delta
        next = money(next, reward, SpendingKind.forEvent(event.type))
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
            locationScene = policy.choiceDestinations[choice.id]?.let { next.locationScene.copy(location = it) }
                ?: next.locationScene,
            engine = day.copy(
                energy = nextEnergy,
                ateToday = day.ateToday || choice.id in policy.feedsPetChoiceIds,
                steps = Math.addExact(day.steps, 1),
                deeds = day.deeds.map { if (it.id == occurrence.deedOfferId) it.copy(completed = true) else it },
            ),
        )
        if (policy.finishesStoryAct) {
            val selected = factory.goals.selectedGoal(state) ?: reject(BlockReason.GoalUnavailable)
            ensure(factory.storyProgress(state).goalAvailable(selected) && selected.progress(next, factory.content).isCollected, BlockReason.ChapterGoalIncomplete)
            next = next.copy(selectedGoalId = null, selectedSavingItemId = null, completedGoalProjects = next.completedGoalProjects +
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

    private fun acceptProposal(state: GameState, command: EngineCommand.AcceptDeedProposal, requestId: String,
        demoMode: Boolean = false): GameState {
        val occurrenceId = command.occurrenceId
        val occurrence = running(state).currentEvent
        ensure(occurrence?.id == occurrenceId && occurrence.origin == EventOrigin.SCHEDULE &&
            factory.event(occurrence.eventId).type == EventType.EARNING, BlockReason.InvalidEventAction)
        return startDeed(acknowledge(state, occurrenceId), "$occurrenceId:offer", requestId, demoMode)
    }

    private fun completeDeed(state: GameState, command: EngineCommand.CompleteDeed, demoMode: Boolean = false): GameState {
        val occurrence = running(state).currentEvent
        ensure(occurrence?.id == command.occurrenceId && occurrence.origin == EventOrigin.DEED,
            BlockReason.InvalidEventAction)
        val eventId = checkNotNull(occurrence).eventId
        ensure(factory.policy(eventId).deedGameKind == command.score.kind, BlockReason.InvalidEventAction)
        val choice = factory.choices(eventId).singleOrNull() ?: reject(BlockReason.InvalidEventAction)
        return acknowledge(choose(state, EngineCommand.Choose(occurrence.id, choice.id), command.score,
            demoMode = demoMode), occurrence.id)
    }

    private fun startStoryGame(state: GameState, command: EngineCommand.StartStoryGame, demoMode: Boolean = false): GameState {
        val day = running(state)
        val occurrence = day.currentEvent
        ensure(occurrence?.id == command.occurrenceId && occurrence.status == EventStatus.ACTIVE &&
            occurrence.origin == EventOrigin.SCHEDULE, BlockReason.InvalidEventAction)
        val event = factory.event(checkNotNull(occurrence).eventId)
        val policy = factory.policy(event.id)
        ensure(event.type in setOf(EventType.STORY, EventType.RANDOM, EventType.WANT) && command.choiceId in policy.choiceGameKinds, BlockReason.InvalidEventAction)
        val choice = factory.choices(event.id).find { it.id == command.choiceId } ?: reject(BlockReason.InvalidEventAction)
        entryGuard(state, event.id, demoMode)
        actionGuard(day, policy.energyFor(choice.id))
        // Quote admission using pure transformations; opening the board commits no effects.
        val beforeChoice = if (policy.startEffectsTiming == EffectTiming.COMPLETE) eventEffects(state, occurrence, demoMode) else state
        money(beforeChoice, choiceMoneyDelta(choice.id, demoMode), SpendingKind.forEvent(event.type))
        return state
    }

    private fun completeStoryGame(state: GameState, command: EngineCommand.CompleteStoryGame, demoMode: Boolean = false): GameState {
        startStoryGame(state, EngineCommand.StartStoryGame(command.occurrenceId, command.choiceId), demoMode)
        val eventId = checkNotNull(state.engine?.currentEvent).eventId
        ensure(factory.policy(eventId).choiceGameKinds[command.choiceId] == command.score.kind, BlockReason.InvalidEventAction)
        return acknowledge(choose(state, EngineCommand.Choose(command.occurrenceId, command.choiceId), command.score,
            demoMode = demoMode), command.occurrenceId)
    }

    private fun skipMiniGame(state: GameState, command: EngineCommand.SkipMiniGame): GameState {
        val occurrence = running(state).currentEvent
        ensure(occurrence?.id == command.occurrenceId && occurrence.status == EventStatus.ACTIVE,
            BlockReason.InvalidEventAction)
        val eventId = checkNotNull(occurrence).eventId
        val choiceId = command.choiceId ?: run {
            ensure(occurrence.origin == EventOrigin.DEED && factory.policy(eventId).deedGameKind != null,
                BlockReason.InvalidEventAction)
            factory.choices(eventId).singleOrNull()?.id ?: reject(BlockReason.InvalidEventAction)
        }
        if (command.choiceId != null) {
            startStoryGame(state, EngineCommand.StartStoryGame(command.occurrenceId, choiceId), demoMode = true)
        }
        // Apply authored effects directly. A demonstration has no played score or answer evidence.
        return acknowledge(choose(state, EngineCommand.Choose(occurrence.id, choiceId),
            assumeCompletedWork = true, demoMode = true), occurrence.id)
    }

    private fun pause(state: GameState, occurrenceId: String): GameState {
        val occurrence = running(state).currentEvent
        ensure(occurrence?.id == occurrenceId && occurrence.status == EventStatus.ACTIVE, BlockReason.InvalidEventAction)
        checkNotNull(occurrence)
        // An unexpected bill can wait until tomorrow while the child earns or replans today.
        // It remains unresolved and keeps its story guard; deferring never spends or completes it.
        val tomorrow = factory.policy(occurrence.eventId).scheduling.kind == EverydayEventKind.UNEXPECTED
        return replaceEvent(state, occurrence.copy(status = if (tomorrow) EventStatus.CARRIED_ACTIVE else EventStatus.PAUSED))
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

    private fun startDeed(current: GameState, offerId: String, requestId: String, demoMode: Boolean = false): GameState {
        val proposal = running(current).currentEvent?.takeIf {
            it.origin == EventOrigin.SCHEDULE && it.status == EventStatus.RESULT &&
                factory.event(it.eventId).type == EventType.EARNING
        }
        // Choosing work from Deeds also closes an already shown offer card. Keep
        // that offer and its deadline: acknowledging a proposal does not do the
        // work or decline it. Admission and this acknowledgement commit together.
        val state = if (proposal != null) acknowledge(current, proposal.id) else current
        val day = running(state)
        val active = day.currentEvent
        ensure(active == null || (active.origin == EventOrigin.DEED && active.deedOfferId == offerId &&
            active.status == EventStatus.ACTIVE), BlockReason.EventInProgress)
        val offer = day.deeds.find { it.id == offerId && it.isAvailable(day.day) } ?: reject(BlockReason.DeedUnavailable)
        val policy = factory.policy(offer.eventId)
        ensure(day.phase != DayPhase.READY_TO_END || policy.energyCost <= rules.shortDeedMaxEnergy, BlockReason.OnlyShortDeedsAfterSchedule)
        entryGuard(state, offer.eventId, demoMode)
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

    private fun feed(state: GameState, mealId: String, demoMode: Boolean): GameState {
        running(state)
        return factory.mealPolicy.apply(state, mealId, rules.fullEnergy, demoMode)
    }

    private fun finishDayFromEvent(state: GameState, occurrenceId: String, demoMode: Boolean): GameState {
        val day = running(state)
        val occurrence = day.currentEvent
        ensure(occurrence?.id == occurrenceId, BlockReason.InvalidEventAction)
        checkNotNull(occurrence)
        val proposal = occurrence.status == EventStatus.RESULT && occurrence.origin == EventOrigin.SCHEDULE &&
            factory.event(occurrence.eventId).type == EventType.EARNING
        ensure(occurrence.status == EventStatus.ACTIVE || proposal, BlockReason.InvalidEventAction)
        // Check energy before food: an unfed pet with a viable choice does not need early sleep.
        ensure(day.energy == 0 || minimumEnergy(state, occurrence, demoMode) > day.energy, BlockReason.UnfinishedEvents)
        ensure(day.ateToday, BlockReason.MustEat)
        // A proposal already spent its step. Keep its offer/deadline; do not offer it again tomorrow.
        val deferred = if (proposal) acknowledge(state, occurrenceId) else pause(state, occurrenceId)
        return carryRemainingPlan(deferred)
    }

    private fun finishDay(current: GameState, demoMode: Boolean): GameState {
        val state = prepareIntroduction(current, "finish:${current.engine?.revision}")
        val day = running(state)
        ensure(day.currentEvent == null, BlockReason.EventInProgress)
        ensure(day.ateToday, BlockReason.MustEat)
        if (factory.storyProgress(state).goalReadyForStory) {
            // Rest uses the same priority as Continue. A paused optional card must not prevent
            // sleeping when the next story action is too tiring.
            val next = nextOccurrenceOrNull(state)
            val cost = next?.let { minimumEnergy(state, it, demoMode) }
                ?: nextGoalDeedOffer(state)?.let { factory.policy(it.eventId).energyCost }
            ensure(cost == null || day.energy == 0 || cost > day.energy, BlockReason.UnfinishedEvents)
            return carryRemainingPlan(state)
        }
        val pending = day.events.filter { it.status == EventStatus.PENDING ||
            (it.status == EventStatus.PAUSED && it.origin == EventOrigin.SCHEDULE) }
        val eligible = pending.filter { factory.storyProgress(state).eligible(it.eventId) }
        if (eligible.isNotEmpty()) {
            val next = eligible.firstOrNull { it.status == EventStatus.PAUSED } ?: eligible.first()
            ensure(day.energy == 0 || minimumEnergy(state, next, demoMode) > day.energy, BlockReason.UnfinishedEvents)
        }
        return carryRemainingPlan(state)
    }

    private fun carryRemainingPlan(state: GameState): GameState {
        val day = running(state)
        return state.copy(engine = day.copy(
            phase = DayPhase.FINISHED,
            events = day.events.filter { EventScheduling.retainOccurrence(state, it, factory) }.map { occurrence -> when {
                occurrence.status == EventStatus.PENDING -> occurrence.copy(status = EventStatus.CARRIED)
                occurrence.status == EventStatus.PAUSED && occurrence.origin == EventOrigin.SCHEDULE ->
                    occurrence.copy(status = EventStatus.CARRIED_ACTIVE)
                else -> occurrence
            } },
        ))
    }

    /** The paid option cannot prevent rest when only the work alternative is affordable. */
    private fun minimumEnergy(state: GameState, occurrence: EventOccurrence, demoMode: Boolean = false): Int {
        val policy = factory.policy(occurrence.eventId)
        if (policy.choiceEnergyCosts.isEmpty()) return policy.energyCost
        val event = factory.event(occurrence.eventId)
        val choices = factory.choices(event.id)
        val affordable = choices.filter { choice ->
            try {
                val priceOnStart = if (policy.startEffectsTiming == EffectTiming.COMPLETE ||
                    (policy.startEffectsTiming == EffectTiming.OPEN && occurrence.status == EventStatus.PENDING)) {
                    factory.eventMoneyDelta(event.id, demoMode)
                } else 0L
                money(money(state, priceOnStart, SpendingKind.forEvent(event.type)),
                    choiceMoneyDelta(choice.id, demoMode), SpendingKind.forEvent(event.type))
                true
            } catch (blocked: EconomyViolation) {
                if (blocked.reason != EconomyFailure.INSUFFICIENT_MONEY) throw blocked
                false
            }
        }
        return affordable.ifEmpty { choices }.minOf { policy.energyFor(it.id) }
    }

    private fun entryGuard(state: GameState, eventId: String, demoMode: Boolean = false) {
        val event = factory.event(eventId)
        require(event.type != EventType.EARNING || (event.moneyDeltaOnStart >= 0 && factory.choices(eventId).all { it.moneyDelta >= 0 })) {
            "EARNING cannot require money"
        }
        val policy = factory.policy(eventId)
        ensure(factory.storyProgress(state).eligible(eventId),
            if (policy.finishesStoryAct && factory.goals.selectedGoal(state)?.progress(state, factory.content)?.isCollected != true)
                BlockReason.ChapterGoalIncomplete else BlockReason.StoryConditionsNotMet)
        if (policy.finishesStoryAct) {
            val period = state.financial.currentPeriod
            // Imported saves lack the old observations; preserve their existing story progress.
            if (period != null && !period.imported) {
                val missing = period.missingMilestones.filterNot { demoMode && it in setOf(
                    FinancialMilestone.SAVE_FOR_GOAL, FinancialMilestone.REVIEW_PLAN) }
                val reason = if (missing.singleOrNull() == FinancialMilestone.PROVIDE_NEEDS)
                    BlockReason.MustEat else BlockReason.FinancialPracticeRequired(missing)
                ensure(missing.isEmpty(), reason)
            }
        }
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

    private fun eventEffects(state: GameState, occurrence: EventOccurrence, demoMode: Boolean): GameState {
        val event = factory.event(occurrence.eventId)
        var next = money(state, factory.eventMoneyDelta(event.id, demoMode), SpendingKind.forEvent(event.type))
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

    private fun money(state: GameState, delta: Long, kind: SpendingKind): GameState = state.copy(
        economy = if (delta < 0) EconomyOperations.spend(state.economy, Math.negateExact(delta), kind)
            else EconomyOperations.earn(state.economy, delta),
    )

    private fun replaceEvent(state: GameState, event: EventOccurrence): GameState = state.copy(
        story = state.story.copy(activeEventId = if (event.status == EventStatus.ACTIVE || event.status == EventStatus.RESULT) event.eventId else null),
        engine = state.engine!!.copy(events = state.engine.events.map { if (it.id == event.id) event else it }),
    )

    private fun owns(state: GameState, ids: Set<String>) = state.ownedItems.map { it.itemId }.toSet().containsAll(ids)
    private fun running(state: GameState): EngineState = (state.engine ?: reject(BlockReason.DayNotStarted)).also {
        ensure(it.phase != DayPhase.FINISHED, BlockReason.DayFinished)
    }
    private fun withDemoEnergy(state: GameState): GameState {
        val day = state.engine ?: return state
        val pet = if (state.pet.visualState == ru.nksk.lctapp.domain.pet.PetVisualState.TIRED)
            state.pet.transitionTo(ru.nksk.lctapp.domain.pet.PetVisualState.NORMAL) else state.pet
        return state.copy(pet = pet, engine = day.copy(energy = rules.fullEnergy, nextMorningEnergy = null))
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
