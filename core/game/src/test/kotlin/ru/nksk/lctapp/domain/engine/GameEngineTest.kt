package ru.nksk.lctapp.domain.engine

import java.io.IOException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.domain.content.*
import ru.nksk.lctapp.domain.economy.*
import ru.nksk.lctapp.domain.economy.BudgetPlan
import ru.nksk.lctapp.domain.economy.EconomyState
import ru.nksk.lctapp.domain.game.GameRepository
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.game.OwnedItem
import ru.nksk.lctapp.domain.pet.PetColor
import ru.nksk.lctapp.domain.pet.PetState
import ru.nksk.lctapp.domain.pet.PetVisualState
import ru.nksk.lctapp.domain.story.StoryState
import ru.nksk.lctapp.domain.minigame.DeedGameKind
import ru.nksk.lctapp.domain.minigame.DeedGameScore
import ru.nksk.lctapp.domain.minigame.PriceQuizState
import ru.nksk.lctapp.domain.minigame.TargetStopState
import ru.nksk.lctapp.domain.location.GameLocation
import ru.nksk.lctapp.domain.location.LocationLighting
import ru.nksk.lctapp.domain.location.LocationScene

class GameEngineTest {
    @Test fun travelIsCommittedWithTheChosenOutcomeAndKeepsLightingForAllCompletionPaths() = runTest {
        for (completion in listOf("choose", "complete", "game")) {
            val f = Fixture()
            val policy = f.policies.getValue("lore").copy(energyCost = 1,
                choiceDestinations = mapOf("lore-choice" to GameLocation.OBSERVATORY),
                choiceGameKinds = if (completion == "game") mapOf("lore-choice" to DeedGameKind.PRECISION) else emptyMap())
            val engine = GameEngine(f.repo, EventFactory(f.content, f.policies + ("lore" to policy), emptyList()), f.rules)
            f.repo.update { it.copy(locationScene = LocationScene(GameLocation.PIER, LocationLighting.EVENING)) }
            f.begin(listOf("lore", "quiet", "quiet", "quiet"))
            assertTrue(engine.dispatch(f.request(EngineCommand.OpenNextEvent)) is EngineResult.Applied)
            val occurrence = f.day.currentEvent!!.id
            assertEquals(GameLocation.PIER, f.state.locationScene.location)
            assertTrue(engine.dispatch(f.request(EngineCommand.PauseEvent(occurrence))) is EngineResult.Applied)
            assertEquals(GameLocation.PIER, f.state.locationScene.location)
            assertTrue(engine.dispatch(f.request(EngineCommand.OpenNextEvent)) is EngineResult.Applied)
            if (completion == "game") {
                assertTrue(engine.dispatch(f.request(EngineCommand.StartStoryGame(occurrence, "lore-choice"))) is EngineResult.Applied)
                assertEquals(GameLocation.PIER, f.state.locationScene.location)
            }
            val before = f.state
            val preview = engine.previewEventChoice(before, occurrence, "lore-choice") as EngineResult.Applied
            assertEquals(GameLocation.OBSERVATORY, preview.state.locationScene.location)
            assertEquals(before, f.state)
            val command = when (completion) {
                "choose" -> EngineCommand.Choose(occurrence, "lore-choice")
                "complete" -> EngineCommand.CompleteEvent(occurrence, "lore-choice")
                else -> EngineCommand.CompleteStoryGame(occurrence, "lore-choice",
                    checkNotNull(DeedGameScore.fromPrecision(TargetStopState(10, round = 5, hits = 5, lastHit = true))))
            }
            assertTrue(engine.dispatch(f.request(command)) is EngineResult.Applied)
            assertEquals(LocationScene(GameLocation.OBSERVATORY, LocationLighting.EVENING), f.state.locationScene)
            assertEquals(before.engine!!.energy - 1, f.day.energy)
            assertEquals(before.economy, f.state.economy)
            assertEquals("lore-choice", f.state.story.decisions.last().choiceId)
        }
    }

    @Test fun rejectedOrFailedTravelLeavesTheWholeWorldUntouchedAndNoDestinationKeepsTheLatestLocation() = runTest {
        val f = Fixture(balance = 2)
        val engine = GameEngine(f.repo, EventFactory(f.content, f.policies +
            ("expensive" to f.policies.getValue("expensive").copy(choiceDestinations = mapOf("expensive-choice" to GameLocation.OBSERVATORY))), emptyList()), f.rules)
        f.begin(listOf("expensive", "quiet", "quiet", "quiet"))
        f.apply(EngineCommand.OpenNextEvent)
        val command = EngineCommand.CompleteEvent(f.day.currentEvent!!.id, "expensive-choice")
        val before = f.state
        assertEquals(EngineResult.Blocked(BlockReason.InsufficientMoney(2)), engine.dispatch(f.request(command)))
        assertEquals(before, f.state)
        f.repo.update { it.copy(economy = EconomyState(BudgetPlan(0, 0, 0, 10), availableBalance = 10)) }
        val funded = f.state
        f.repo.failCommit = true
        try { engine.dispatch(f.request(command)); fail("Storage error must propagate") } catch (_: IOException) { }
        assertEquals(funded, f.state)
        f.repo.failCommit = false
        assertTrue(engine.dispatch(f.request(command)) is EngineResult.Applied)
        assertEquals(GameLocation.OBSERVATORY, f.state.locationScene.location)
        f.apply(EngineCommand.OpenNextEvent)
        f.repo.update { it.copy(locationScene = LocationScene(GameLocation.FAIR, LocationLighting.EVENING)) }
        f.choose()
        assertEquals(LocationScene(GameLocation.FAIR, LocationLighting.EVENING), f.state.locationScene)
    }

    @Test fun destinationCannotReferToAnotherEventsChoice() {
        val f = Fixture()
        assertThrows(IllegalArgumentException::class.java) {
            EventFactory(f.content, f.policies + ("lore" to f.policies.getValue("lore").copy(
                choiceDestinations = mapOf("quiet-choice" to GameLocation.OBSERVATORY))), emptyList())
        }
    }

    @Test fun retiredLoreChoiceRemainsReadableButCannotCompleteOrPreviewANewAction() = runTest {
        val f = Fixture()
        val oldSkip = f.content.choices.single { it.id == "lore-choice" }.copy(id = "lore-skip", position = 1)
        val content = f.content.copy(choices = f.content.choices + oldSkip)
        val policies = f.policies + ("lore" to f.policies.getValue("lore").copy(disabledChoiceIds = setOf(oldSkip.id)))
        val factory = EventFactory(content, policies, emptyList())
        val engine = GameEngine(f.repo, factory, f.rules)
        f.begin(listOf("lore", "quiet", "quiet", "quiet"))
        assertTrue(engine.dispatch(f.request(EngineCommand.OpenNextEvent)) is EngineResult.Applied)
        val before = f.state
        val occurrence = f.day.currentEvent!!
        for (command in listOf(EngineCommand.Choose(occurrence.id, oldSkip.id),
            EngineCommand.CompleteEvent(occurrence.id, oldSkip.id))) {
            assertEquals(EngineResult.Blocked(BlockReason.InvalidEventAction), engine.dispatch(f.request(command)))
            assertEquals(before, f.state)
        }
        assertEquals(EngineResult.Blocked(BlockReason.InvalidEventAction),
            engine.previewEventChoice(before, occurrence.id, oldSkip.id))
        val historical = before.copy(story = before.story.copy(decisions = listOf(
            ru.nksk.lctapp.domain.story.StoryDecision("old-skip", oldSkip.id))))
        assertTrue(factory.storyProgress(historical).completed("lore"))
        assertTrue(engine.dispatch(f.request(EngineCommand.PauseEvent(occurrence.id))) is EngineResult.Applied)
        assertTrue(engine.dispatch(f.request(EngineCommand.OpenNextEvent)) is EngineResult.Applied)
        assertEquals(occurrence.id, f.day.currentEvent!!.id)
        assertTrue(engine.dispatch(f.request(EngineCommand.CompleteEvent(occurrence.id, "lore-choice"))) is EngineResult.Applied)
        assertEquals("lore-choice", f.state.story.decisions.last().choiceId)
    }

    @Test fun retiredChoiceConfigurationCannotNameAnotherEventOrRemoveAllActions() {
        val f = Fixture()
        for (disabled in listOf(setOf("quiet-choice"), setOf("lore-choice"))) {
            assertThrows(IllegalArgumentException::class.java) {
                EventFactory(f.content, f.policies + ("lore" to f.policies.getValue("lore").copy(disabledChoiceIds = disabled)), emptyList())
            }
        }
    }

    @Test fun recapRecoversActualCompletedChoicesFromOldSavesAndExcludesOffersAndCarriedWork() = runTest {
        val f = Fixture(energy = 1)
        f.begin(listOf("large", "quiet", "small", "lore"))
        f.apply(EngineCommand.OpenNextEvent)
        f.apply(EngineCommand.DismissDeedProposal(f.day.currentEvent!!.id))
        f.completeOne()
        f.apply(EngineCommand.OpenNextEvent)
        val offered = f.day.deeds.last().id
        f.ack()
        f.apply(EngineCommand.StartDeed(offered))
        f.choose()
        f.ack()
        f.apply(EngineCommand.Feed("basic"))
        f.apply(EngineCommand.FinishDay)
        // Older games have all decisions and occurrences, even when receipts are absent.
        f.repo.update { it.copy(engine = it.engine!!.copy(journal = emptyList())) }
        val before = f.state
        val summary = f.newEngine().daySummary(before)!!
        assertEquals(listOf("quiet-choice", "small-choice"), summary.completedDecisions.map { it.choiceId })
        assertTrue(summary.completedLoreEventIds.isEmpty())
        assertEquals(before, f.state)
    }

    @Test fun journalKeepsRepeatedMealsAndResetsOnlyWhenTheNextDayBegins() = runTest {
        val f = Fixture()
        f.begin()
        f.apply(EngineCommand.Feed("basic"))
        val secondMeal = f.request(EngineCommand.Feed("basic"))
        assertTrue(f.engine.dispatch(secondMeal) is EngineResult.Applied)
        assertEquals(EngineResult.Blocked(BlockReason.StaleRevision), f.engine.dispatch(secondMeal))
        assertEquals(listOf(-4L, -4L), f.day.journal.map { it.moneyDelta })
        assertEquals(2, f.day.journal.map { it.id }.distinct().size)
        f.completePlan()
        f.apply(EngineCommand.FinishDay)
        assertEquals(f.day.journal, f.engine.daySummary(f.state)!!.journal)
        assertEquals(-8L, f.state.economy.balance - f.day.openingBalance)
        f.begin()
        assertTrue(f.day.journal.isEmpty())
        assertEquals(92L, f.day.openingBalance)
        assertEquals(5, f.day.openingEnergy)
    }

    @Test fun journalKeepsOpposingStartCostAndRewardEvenWhenTheNetChangeIsZero() = runTest {
        for (timing in EffectTiming.entries) {
            val f = Fixture()
            val content = f.content.copy(events = f.content.events.map {
                if (it.id == "reward") it.copy(moneyDeltaOnStart = -7) else it
            })
            val engine = GameEngine(f.repo, EventFactory(content,
                f.policies + ("reward" to EventPolicy(0, startEffectsTiming = timing)), emptyList()), f.rules)
            f.begin(listOf("reward", "quiet", "quiet", "quiet"))
            assertTrue(engine.dispatch(f.request(EngineCommand.OpenNextEvent)) is EngineResult.Applied)
            val id = f.day.currentEvent!!.id
            if (timing == EffectTiming.OPEN) {
                assertTrue(engine.dispatch(f.request(EngineCommand.PauseEvent(id))) is EngineResult.Applied)
                assertTrue(engine.dispatch(f.request(EngineCommand.OpenNextEvent)) is EngineResult.Applied)
            }
            assertTrue(engine.dispatch(f.request(EngineCommand.CompleteEvent(id, "reward-choice"))) is EngineResult.Applied)
            assertEquals(100L, f.state.economy.balance)
            assertEquals(listOf(-7L, 7L), f.day.journal.map { it.moneyDelta })
            assertEquals(listOf(DayJournalKind.EVENT_START, DayJournalKind.EVENT_CHOICE), f.day.journal.map { it.kind })
        }
    }

    @Test fun restingFromAnActiveCardCommitsCarryAtomicallyAndResumesOnlyOnContinue() = runTest {
        val f = Fixture()
        val plan = listOf("lore", "quiet", "quiet", "small")
        f.begin(plan)
        f.apply(EngineCommand.OpenNextEvent)
        f.apply(EngineCommand.Feed("basic"))
        f.repo.update { it.copy(engine = it.engine!!.copy(energy = 0)) }
        val before = f.state
        val occurrence = f.day.currentEvent!!
        val request = f.request(EngineCommand.FinishDayFromEvent(occurrence.id))

        f.repo.failCommit = true
        try { f.engine.dispatch(request); fail("Storage failure must propagate") } catch (_: IOException) { }
        assertEquals(before, f.state)
        f.repo.failCommit = false
        assertTrue(f.engine.dispatch(request) is EngineResult.Applied)
        assertEquals(DayPhase.FINISHED, f.day.phase)
        assertNull(f.day.currentEvent)
        assertNull(f.state.story.activeEventId)
        assertEquals(before.economy, f.state.economy)
        assertEquals(before.story.decisions, f.state.story.decisions)
        assertEquals(before.engine!!.steps, f.day.steps)
        assertEquals(before.engine!!.revision + 1, f.day.revision)
        assertEquals(before.engine!!.events.map { it.id }, f.day.events.map { it.id })
        assertEquals(listOf(EventStatus.CARRIED_ACTIVE, EventStatus.CARRIED, EventStatus.CARRIED, EventStatus.CARRIED),
            f.day.events.map { it.status })
        assertTrue(f.engine.daySummary(f.state)!!.completedLoreEventIds.isEmpty())
        assertEquals(EngineResult.Blocked(BlockReason.StaleRevision), f.engine.dispatch(request))

        f.begin(plan)
        assertEquals(5, f.day.energy)
        assertNull(f.day.currentEvent)
        assertEquals(EventStatus.PAUSED, f.day.events.first().status)
        f.apply(EngineCommand.OpenNextEvent)
        assertEquals(occurrence.id, f.day.currentEvent!!.id)
        f.choose()
        assertEquals(1, f.state.story.decisions.size)
    }

    @Test fun restingFromADeedProposalRequiresFoodAndKeepsItsOriginalDeadline() = runTest {
        val f = Fixture(energy = 1)
        f.begin(listOf("large", "quiet", "quiet", "quiet"))
        f.apply(EngineCommand.OpenNextEvent)
        val proposal = f.day.currentEvent!!
        val offer = f.day.deeds.single()
        val command = EngineCommand.FinishDayFromEvent(proposal.id)
        val unfed = f.state
        assertEquals(BlockReason.MustEat, f.blocked(command))
        assertEquals(unfed, f.state)
        f.apply(EngineCommand.Feed("basic"))
        val before = f.state
        f.apply(command)
        assertEquals(DayPhase.FINISHED, f.day.phase)
        assertEquals(offer, f.day.deeds.single())
        assertEquals(EventStatus.COMPLETED, f.day.events.first().status)
        assertTrue(f.day.events.drop(1).all { it.status == EventStatus.CARRIED })
        assertEquals(before.economy, f.state.economy)
        assertEquals(1, f.day.steps)
        assertEquals(1, f.day.energy)
        assertTrue(f.state.story.decisions.isEmpty())

        f.begin(listOf("quiet", "quiet", "quiet", "small"))
        assertEquals(offer, f.engine.availableDeeds(f.state).single())
        assertEquals(3, offer.expiresDay)
        assertFalse(f.day.events.any { it.id == proposal.id })
        assertEquals(0, f.day.steps)
        assertNull(f.day.currentEvent)
    }

    @Test fun restFromCardRejectsWrongIdentityViableActionAndCompletedResults() = runTest {
        val f = Fixture()
        f.begin(listOf("lore", "quiet", "quiet", "quiet"))
        f.apply(EngineCommand.OpenNextEvent)
        val command = EngineCommand.FinishDayFromEvent(f.day.currentEvent!!.id)
        val before = f.state
        assertEquals(BlockReason.InvalidEventAction, f.blocked(EngineCommand.FinishDayFromEvent("another-card")))
        assertEquals(BlockReason.UnfinishedEvents, f.blocked(command))
        assertEquals(before, f.state)
        f.choose()
        f.repo.update { it.copy(engine = it.engine!!.copy(energy = 0, ateToday = true)) }
        val completed = f.state
        assertEquals(BlockReason.InvalidEventAction, f.blocked(command))
        assertEquals(completed, f.state)
    }

    @Test fun realDeedRequiresItsGameAndCommitsTheReducedRewardOnlyOnce() = runTest {
        val f = Fixture(deedKind = DeedGameKind.COMPARISON)
        f.begin(listOf("small", "quiet", "quiet", "quiet"))
        f.apply(EngineCommand.OpenNextEvent)
        f.apply(EngineCommand.AcceptDeedProposal(f.day.currentEvent!!.id))
        val id = f.day.currentEvent!!.id
        val before = f.state
        assertEquals(BlockReason.InvalidEventAction, f.blocked(EngineCommand.CompleteEvent(id, "small-choice")))
        val wrongGame = checkNotNull(DeedGameScore.fromPrecision(TargetStopState(10, round = 5, hits = 5, lastHit = true)))
        assertEquals(BlockReason.InvalidEventAction, f.blocked(EngineCommand.CompleteDeed(id, wrongGame)))
        assertEquals(before, f.state)

        val score = checkNotNull(DeedGameScore.fromComparison(PriceQuizState.create().copy(current = 5, correctAnswers = 3)))
        val command = EngineCommand.CompleteDeed(id, score)
        f.repo.failCommit = true
        try { f.apply(command); fail("A failed commit must propagate") } catch (_: IOException) { }
        assertEquals(before, f.state)
        f.repo.failCommit = false
        f.apply(command)
        assertEquals(106L, f.state.economy.balance)
        assertEquals(6L, f.day.journal.single { it.kind == DayJournalKind.DEED }.moneyDelta)
        assertEquals(-1, f.day.journal.single { it.kind == DayJournalKind.DEED }.energyDelta)
        assertEquals(4, f.day.energy)
        assertEquals(2, f.day.steps)
        assertTrue(f.day.deeds.single().completed)
        assertNull(f.day.currentEvent)
        assertEquals(1, f.state.story.decisions.size)
        val completed = f.state
        assertEquals(BlockReason.InvalidEventAction, f.blocked(command))
        assertEquals(completed, f.state)
    }

    @Test fun completedDeedWithNoCorrectAnswersCreditsOneCoinToWalletReserveAndJournal() = runTest {
        val f = Fixture(deedKind = DeedGameKind.COMPARISON)
        f.begin(listOf("small", "quiet", "quiet", "quiet"))
        f.apply(EngineCommand.OpenNextEvent)
        f.apply(EngineCommand.AcceptDeedProposal(f.day.currentEvent!!.id))
        val before = f.state.economy
        val score = checkNotNull(DeedGameScore.fromComparison(PriceQuizState.create().copy(current = 5, correctAnswers = 0)))
        val command = EngineCommand.CompleteDeed(f.day.currentEvent!!.id, score)
        f.apply(command)
        assertEquals(before.availableBalance + 1, f.state.economy.availableBalance)
        assertEquals(before.plan.reserve + 1, f.state.economy.plan.reserve)
        assertEquals(before.savingsBalance, f.state.economy.savingsBalance)
        assertEquals(1L, f.day.journal.single { it.kind == DayJournalKind.DEED }.moneyDelta)
        assertTrue(f.day.deeds.single().completed)
        val completed = f.state
        assertEquals(BlockReason.InvalidEventAction, f.blocked(command))
        assertEquals(completed, f.state)
    }

    @Test fun completionClosesTheEventAndCommitsItsEffectsOnlyOnce() = runTest {
        val f = Fixture()
        f.begin(listOf("small", "quiet", "quiet", "quiet"))
        f.apply(EngineCommand.OpenNextEvent)
        f.ack()
        f.apply(EngineCommand.StartDeed(f.day.deeds.single().id))
        val request = f.request(EngineCommand.CompleteEvent(f.day.currentEvent!!.id, "small-choice"))
        val result = f.engine.dispatch(request) as EngineResult.Applied
        assertEquals(110L, result.state.economy.balance)
        assertEquals(2, result.state.engine!!.steps)
        assertEquals(4, result.state.engine!!.energy)
        assertNull(result.state.engine!!.currentEvent)
        assertTrue(result.state.engine!!.deeds.single().completed)
        assertEquals(1, result.state.story.decisions.size)
        assertEquals(EngineResult.Blocked(BlockReason.StaleRevision), f.engine.dispatch(request))
    }

    @Test fun explicitRefusalDiscardsOfferButDeferringKeepsIt() = runTest {
        val f = Fixture()
        f.begin(listOf("small", "medium", "quiet", "quiet"))
        f.apply(EngineCommand.OpenNextEvent)
        val factory = EventFactory(f.content, f.policies + ("small" to f.policies.getValue("small").copy(discardOfferOnDismiss = true)), emptyList())
        val engine = GameEngine(f.repo, factory, f.rules)
        assertTrue(engine.dispatch(f.request(EngineCommand.DismissDeedProposal(f.day.currentEvent!!.id))) is EngineResult.Applied)
        assertTrue(f.day.deeds.isEmpty())
        assertEquals(1, f.day.steps)
        assertEquals(100L, f.state.economy.balance)
        f.apply(EngineCommand.OpenNextEvent)
        f.apply(EngineCommand.DismissDeedProposal(f.day.currentEvent!!.id))
        assertEquals("medium", f.day.deeds.single().eventId)
        assertEquals(2, f.day.steps)
    }

    @Test fun acceptingAnOfferStartsExecutionWithoutPayingOrSpendingEnergy() = runTest {
        val f = Fixture()
        f.begin(listOf("small", "quiet", "quiet", "quiet"))
        f.apply(EngineCommand.OpenNextEvent)
        val request = f.request(EngineCommand.AcceptDeedProposal(f.day.currentEvent!!.id))
        val result = f.engine.dispatch(request) as EngineResult.Applied
        assertEquals(100L, result.state.economy.balance)
        assertEquals(1, result.state.engine!!.steps)
        assertEquals(5, result.state.engine!!.energy)
        assertEquals(EventOrigin.DEED, result.state.engine!!.currentEvent!!.origin)
        assertEquals(EventStatus.ACTIVE, result.state.engine!!.currentEvent!!.status)
        assertFalse(result.state.engine!!.deeds.single().completed)
        assertEquals(EngineResult.Blocked(BlockReason.StaleRevision), f.engine.dispatch(request))
    }

    @Test fun rejectedOfferExecutionLeavesTheOfferScreenAndMoneyUnchanged() = runTest {
        val f = Fixture(hunger = 1)
        f.begin(listOf("small", "quiet", "quiet", "quiet"))
        f.apply(EngineCommand.OpenNextEvent)
        val before = f.state
        assertEquals(BlockReason.MustEat, f.blocked(EngineCommand.AcceptDeedProposal(f.day.currentEvent!!.id)))
        assertEquals(before, f.state)
    }

    @Test fun pausedLoreDoesNotGrantCompletionAndResumesAfterOtherWork() = runTest {
        val f = Fixture()
        f.begin(listOf("small", "lore", "quiet", "quiet"))
        f.completeOne()
        f.apply(EngineCommand.OpenNextEvent)
        val id = f.day.currentEvent!!.id
        f.apply(EngineCommand.PauseEvent(id))
        assertTrue(f.state.story.decisions.isEmpty())
        assertNull(f.state.story.activeEventId)
        f.apply(EngineCommand.StartDeed(f.day.deeds.single().id))
        f.choose(); f.ack()
        assertEquals(DayPhase.RUNNING, f.day.phase)
        f.apply(EngineCommand.OpenNextEvent)
        assertEquals(id, f.day.currentEvent!!.id)
        f.choose()
        assertEquals(1, f.state.story.decisions.count { it.choiceId == "lore-choice" })
    }

    @Test fun earlySleepCarriesAllUnseenEventsInOrderIncludingRepeatedDefinitions() = runTest {
        val f = Fixture(energy = 1)
        f.begin(listOf("drain", "lore", "quiet", "quiet"))
        f.completeOne(); f.endFedDay()
        val carried = f.day.events.drop(1)
        assertTrue(carried.all { it.status == EventStatus.CARRIED })
        assertEquals(BlockReason.MissingCarriedLore, f.blocked(EngineCommand.BeginDay("day", listOf("quiet", "lore", "quiet", "quiet"))))
        f.begin(listOf("lore", "quiet", "quiet", "small"))
        assertEquals(carried.map { it.id }, f.day.events.take(3).map { it.id })
        assertTrue(f.day.events.all { it.status == EventStatus.PENDING })
    }

    @Test fun pausedDeedResumesTheSameOccurrenceAndCannotBePaidTwice() = runTest {
        val f = Fixture()
        f.begin(listOf("small", "quiet", "quiet", "quiet")); f.completeOne()
        val offer = f.day.deeds.single()
        f.apply(EngineCommand.StartDeed(offer.id))
        val id = f.day.currentEvent!!.id
        f.apply(EngineCommand.PauseEvent(id))
        f.apply(EngineCommand.StartDeed(offer.id))
        assertEquals(id, f.day.currentEvent!!.id)
        f.choose(); f.ack()
        assertEquals(110L, f.state.economy.balance)
        assertEquals(BlockReason.DeedUnavailable, f.blocked(EngineCommand.StartDeed(offer.id)))
    }

    @Test fun weeklyIncomeIsGrantedOnceOnBeginningDayEightAndNotOnReadingSummary() = runTest {
        val f = Fixture()
        f.begin()
        repeat(6) { f.completePlan(); f.endFedDay(); f.begin() }
        f.completePlan(); f.endFedDay()
        val balance = f.state.economy.balance
        val engine = GameEngine(f.repo, f.factory, f.rules.copy(weeklyIncome = 100))
        assertNotNull(engine.daySummary(f.state))
        assertEquals(balance, f.state.economy.balance)
        val request = f.request(EngineCommand.BeginDay("day", List(4) { "quiet" }, openFirst = true))
        assertTrue(engine.dispatch(request) is EngineResult.Applied)
        assertEquals(balance + 100, f.state.economy.balance)
        assertNull(f.day.currentEvent)
        assertEquals(BudgetPlanningStage.RECEIPT, f.state.economy.planning!!.stage)
        assertEquals(100L, f.day.journal.single { it.kind == DayJournalKind.WEEKLY_INCOME }.moneyDelta)
        assertEquals(EngineResult.Blocked(BlockReason.StaleRevision), engine.dispatch(request))
    }

    @Test fun offerAndItsLaterExecutionAreSeparateSteps() = runTest {
        val f = Fixture()
        f.begin(listOf("small", "quiet", "quiet", "quiet"))
        f.apply(EngineCommand.OpenNextEvent)
        assertEquals(1, f.day.steps)
        assertEquals(5, f.day.energy)
        assertEquals(100L, f.state.economy.balance)
        val offer = f.day.deeds.single()
        assertEquals(1, offer.expiresDay)
        f.ack()
        f.apply(EngineCommand.StartDeed(offer.id))
        f.choose()
        assertEquals(2, f.day.steps)
        assertEquals(4, f.day.energy)
        assertEquals(110L, f.state.economy.balance)
        f.ack()
        assertEquals(BlockReason.DeedUnavailable, f.blocked(EngineCommand.StartDeed(offer.id)))
    }

    @Test fun shortDeedRemainsAvailableAfterMainScheduleUntilExplicitDayEnd() = runTest {
        val f = Fixture()
        f.begin(listOf("small", "quiet", "quiet", "quiet"))
        f.completePlan()
        assertEquals(DayPhase.READY_TO_END, f.day.phase)
        assertEquals(4, f.day.steps)
        val offer = f.engine.availableDeeds(f.state).single()
        f.apply(EngineCommand.StartDeed(offer.id))
        f.choose(); f.ack()
        assertEquals(5, f.day.steps)
        assertEquals(DayPhase.READY_TO_END, f.day.phase)
        f.apply(EngineCommand.Feed("basic"))
        f.apply(EngineCommand.FinishDay)
        assertTrue(f.engine.availableDeeds(f.state).isEmpty())
        assertEquals(5, f.engine.daySummary(f.state)!!.steps)
        assertEquals(106L, f.engine.daySummary(f.state)!!.closingBalance)
    }

    @Test fun longDeedDoesNotSilentlyUseShortDeedException() = runTest {
        val f = Fixture()
        f.begin(listOf("large", "quiet", "quiet", "quiet")); f.completePlan()
        assertEquals(BlockReason.OnlyShortDeedsAfterSchedule, f.blocked(EngineCommand.StartDeed(f.day.deeds.single().id)))
    }

    @Test fun inclusiveDeadlinesAreBasedOnOfferDayAndSurviveSeveralDays() = runTest {
        val f = Fixture()
        f.begin(listOf("small", "medium", "large", "quiet")); f.completePlan(); f.endFedDay()
        f.begin()
        assertEquals(setOf("medium", "large"), f.engine.availableDeeds(f.state).map { it.eventId }.toSet())
        f.completePlan(); f.endFedDay(); f.begin()
        assertEquals(listOf("large"), f.engine.availableDeeds(f.state).map { it.eventId })
        f.completePlan(); f.endFedDay(); f.begin()
        assertTrue(f.engine.availableDeeds(f.state).isEmpty())
    }

    @Test fun aFutureOfferOfTheSameWorkHasANewIdentityAndDeadline() = runTest {
        val f = Fixture()
        f.begin(listOf("small", "quiet", "quiet", "quiet")); f.completePlan()
        val old = f.day.deeds.single()
        f.endFedDay(); f.begin(listOf("small", "quiet", "quiet", "quiet")); f.apply(EngineCommand.OpenNextEvent)
        val current = f.engine.availableDeeds(f.state).single()
        assertNotEquals(old.id, current.id)
        assertEquals(2, current.expiresDay)
    }

    @Test fun hungerBlocksTheActionWithoutChargingOrAutomaticallyFeeding() = runTest {
        val f = Fixture(hunger = 1)
        f.begin(); f.completeOne()
        val before = f.state
        assertEquals(BlockReason.MustEat, f.blocked(EngineCommand.OpenNextEvent))
        assertEquals(before, f.state)
        f.apply(EngineCommand.Feed("basic"))
        assertEquals(before.engine!!.energy, f.day.energy)
        assertEquals(before.engine.steps, f.day.steps)
        assertEquals(96L, f.state.economy.balance)
        f.apply(EngineCommand.OpenNextEvent)
    }

    @Test fun insufficientEnergyRequiresFoodBeforeSleepIfNoMealWasTaken() = runTest {
        val f = Fixture(energy = 1)
        f.begin(listOf("drain", "drain", "lore", "lore")); f.completeOne()
        assertEquals(BlockReason.MustEat, f.blocked(EngineCommand.OpenNextEvent))
        f.apply(EngineCommand.Feed("basic"))
        assertEquals(BlockReason.MustSleep, f.blocked(EngineCommand.OpenNextEvent))
        assertEquals(0, f.day.energy)
    }

    @Test fun unfinishedLoreIsCarriedAndCannotDisappearFromTomorrowPlan() = runTest {
        val f = Fixture(energy = 3)
        f.begin(listOf("drain", "drain", "drain", "lore"))
        repeat(3) { f.completeOne() }
        f.apply(EngineCommand.Feed("basic")); f.apply(EngineCommand.FinishDay)
        val carried = f.day.events.last()
        assertEquals(EventStatus.CARRIED, carried.status)
        assertEquals(BlockReason.MissingCarriedLore, f.blocked(EngineCommand.BeginDay("day", List(4) { "quiet" })))
        f.begin(listOf("lore", "quiet", "quiet", "quiet"))
        assertEquals(carried.id, f.day.events.first().id)
        assertEquals(3, f.day.energy)
        f.completeOne()
    }

    @Test fun freeMealEndsTodaysEnergyAndKeepsTheConfiguredLimitForOnlyTheNextMorning() = runTest {
        val f = Fixture()
        f.begin(listOf("drain", "quiet", "quiet", "quiet")); f.completePlan()
        assertEquals(4, f.day.energy)
        val steps = f.day.steps
        f.apply(EngineCommand.Feed("free"))
        assertEquals(0, f.day.energy)
        assertEquals(steps, f.day.steps)
        assertTrue(f.day.ateToday)
        assertEquals(PetVisualState.TIRED, f.state.pet.visualState)
        assertEquals(-4, f.day.journal.single { it.kind == DayJournalKind.MEAL }.energyDelta)
        assertEquals(0L, f.day.journal.single { it.kind == DayJournalKind.MEAL }.moneyDelta)
        assertEquals(100L, f.state.economy.balance)
        f.apply(EngineCommand.FinishDay); f.begin()
        assertEquals(3, f.day.energy)
        assertEquals(3, f.day.openingEnergy)
        assertEquals(PetVisualState.TIRED, f.state.pet.visualState)
        assertNull(f.day.nextMorningEnergy)
        f.completePlan(); f.endFedDay(); f.begin()
        assertEquals(5, f.day.energy)
        assertEquals(PetVisualState.NORMAL, f.state.pet.visualState)
    }

    @Test fun anotherPaidMealCannotRestoreEnergyOrCancelTheFreeMealLimitAndTrainingStillWorks() = runTest {
        val f = Fixture()
        f.begin()
        f.apply(EngineCommand.Feed("free"))
        for (meal in listOf("basic", "luxury")) {
            f.apply(EngineCommand.Feed(meal))
            assertEquals(0, f.day.energy)
            assertEquals(3, f.day.nextMorningEnergy)
            assertEquals(PetVisualState.TIRED, f.state.pet.visualState)
        }
        assertEquals(88L, f.state.economy.balance)
        assertEquals(listOf(-5, 0, 0), f.day.journal.map { it.energyDelta })
        val economy = f.state.economy
        val steps = f.day.steps
        f.apply(EngineCommand.RequestFinancialPractice(series = true))
        val question = checkNotNull(f.state.financial.practice)
        f.apply(EngineCommand.AnswerFinancialQuestion(question.id, question.correctAnswerId))
        f.apply(EngineCommand.AdvanceFinancialPractice(question.id))
        assertEquals(2, f.state.financial.practice!!.series!!.questionNumber)
        assertEquals(economy, f.state.economy)
        assertEquals(0, f.day.energy)
        assertEquals(steps, f.day.steps)
    }

    @Test fun freeMealBlocksAcceptingAnOfferedDeedAndStartingItFromTheListWithoutChangingItsDeadline() = runTest {
        val f = Fixture(deedKind = DeedGameKind.COMPARISON)
        f.begin(listOf("small", "quiet", "quiet", "quiet"))
        f.apply(EngineCommand.OpenNextEvent)
        val proposal = f.day.currentEvent!!
        val offer = f.day.deeds.single()
        f.apply(EngineCommand.Feed("free"))
        val beforeAccept = f.state
        assertEquals(BlockReason.MustSleep, f.blocked(EngineCommand.AcceptDeedProposal(proposal.id)))
        assertEquals(beforeAccept, f.state)
        f.apply(EngineCommand.DismissDeedProposal(proposal.id))
        val beforeStart = f.state
        assertEquals(BlockReason.MustSleep, f.blocked(EngineCommand.StartDeed(offer.id)))
        assertEquals(beforeStart, f.state)
        assertEquals(offer, f.day.deeds.single())
        assertEquals(100L, f.state.economy.balance)
        assertTrue(f.state.story.decisions.isEmpty())
    }

    @Test fun freeMealBlocksResumingOrCompletingAlreadyStartedWorkAndAllowsRest() = runTest {
        val f = Fixture(deedKind = DeedGameKind.COMPARISON)
        f.begin(listOf("small", "quiet", "quiet", "quiet"))
        f.apply(EngineCommand.OpenNextEvent)
        f.apply(EngineCommand.AcceptDeedProposal(f.day.currentEvent!!.id))
        val work = f.day.currentEvent!!
        val offer = f.day.deeds.single()
        val score = checkNotNull(DeedGameScore.fromComparison(PriceQuizState.create().copy(current = 5, correctAnswers = 5)))
        f.apply(EngineCommand.Feed("free"))
        val exhausted = f.state
        for (command in listOf(EngineCommand.StartDeed(offer.id), EngineCommand.CompleteDeed(work.id, score))) {
            assertEquals(BlockReason.MustSleep, f.blocked(command))
            assertEquals(exhausted, f.state)
        }
        f.apply(EngineCommand.PauseEvent(work.id))
        val paused = f.state
        assertEquals(BlockReason.MustSleep, f.blocked(EngineCommand.StartDeed(offer.id)))
        assertEquals(paused, f.state)
        assertFalse(f.day.deeds.single().completed)
        assertEquals(100L, f.state.economy.balance)
        f.apply(EngineCommand.FinishDay)
        assertEquals(DayPhase.FINISHED, f.day.phase)
    }

    @Test fun normalAndLuxuryMealsDoNotGiveEnergy() = runTest {
        val f = Fixture()
        f.begin(listOf("drain", "quiet", "quiet", "quiet")); f.completeOne()
        f.apply(EngineCommand.Feed("luxury"))
        assertEquals(4, f.day.energy)
        assertEquals(PetVisualState.HAPPY, f.state.pet.visualState)
        f.completeOne()
        assertEquals(PetVisualState.NORMAL, f.state.pet.visualState)
    }

    @Test fun twoConcurrentCopiesOfAChoiceOnlyApplyOneOutcome() = runTest {
        val f = Fixture()
        f.begin(listOf("reward", "quiet", "quiet", "quiet")); f.apply(EngineCommand.OpenNextEvent)
        val request = f.request(EngineCommand.Choose(f.day.currentEvent!!.id, "reward-choice"))
        val results = (1..10).map { async { f.engine.dispatch(request) } }.awaitAll()
        assertEquals(1, results.count { it is EngineResult.Applied })
        assertEquals(9, results.count { it == EngineResult.Blocked(BlockReason.StaleRevision) })
        assertEquals(107L, f.state.economy.balance)
        assertEquals(1, f.state.story.decisions.size)
    }

    @Test fun savedResultCanBeRestoredAndAcknowledgedWithoutReplayingReward() = runTest {
        val f = Fixture()
        f.begin(listOf("reward", "quiet", "quiet", "quiet")); f.apply(EngineCommand.OpenNextEvent); f.choose()
        val restored = f.newEngine()
        val old = f.day.currentEvent!!
        assertEquals(EngineResult.Blocked(BlockReason.InvalidEventAction), restored.dispatch(f.request(EngineCommand.Choose(old.id, "reward-choice"))))
        assertTrue(restored.dispatch(f.request(EngineCommand.AcknowledgeResult(old.id))) is EngineResult.Applied)
        assertEquals(107L, f.state.economy.balance)
    }

    @Test fun failedCommitLeavesEveryPartOfTheSnapshotUntouched() = runTest {
        val f = Fixture()
        f.begin(listOf("reward", "quiet", "quiet", "quiet")); f.apply(EngineCommand.OpenNextEvent)
        val before = f.state
        f.repo.failCommit = true
        try { f.choose(); fail("Storage failure must propagate") } catch (_: IOException) { }
        assertEquals(before, f.state)
        f.repo.failCommit = false
        f.choose()
        assertEquals(107L, f.state.economy.balance)
    }

    @Test fun blockedCostRollsBackEarlierEffectsWithinTheSameOutcome() = runTest {
        val f = Fixture(balance = 2)
        f.begin(listOf("expensive", "quiet", "quiet", "quiet")); f.apply(EngineCommand.OpenNextEvent)
        val before = f.state
        assertEquals(BlockReason.InsufficientMoney(2), f.blocked(EngineCommand.Choose(f.day.currentEvent!!.id, "expensive-choice")))
        assertEquals(before, f.state)
    }

    @Test fun guardsReadCurrentInventoryAndGateFinalEventBeforeItIsShown() = runTest {
        val f = Fixture()
        f.begin(listOf("final", "quiet", "quiet", "quiet"))
        assertEquals(BlockReason.ChapterGoalIncomplete, f.blocked(EngineCommand.OpenNextEvent))
        f.repo.update { it.copy(ownedItems = listOf(OwnedItem("owned", "rope"))) }
        f.apply(EngineCommand.OpenNextEvent)
        f.repo.update { it.copy(ownedItems = emptyList()) }
        assertEquals(BlockReason.ChapterGoalIncomplete, f.blocked(EngineCommand.Choose(f.day.currentEvent!!.id, "final-choice")))
    }

    @Test fun requiredItemAndPreviousLoreAreIndependentGuards() = runTest {
        val f = Fixture()
        f.begin(listOf("locked", "quiet", "quiet", "quiet"))
        assertEquals(BlockReason.MissingItems(setOf("rope")), f.blocked(EngineCommand.OpenNextEvent))
        f.repo.update { it.copy(ownedItems = listOf(OwnedItem("owned", "rope"))) }
        assertEquals(BlockReason.PreviousLoreIncomplete, f.blocked(EngineCommand.OpenNextEvent))
    }

    @Test fun identicalEventDefinitionsCanBeUsedMoreThanOnceWithoutMergingDecisions() = runTest {
        val f = Fixture()
        f.begin(); f.completePlan()
        assertEquals(4, f.state.story.decisions.size)
        assertEquals(4, f.state.story.decisions.map { it.id }.distinct().size)
    }

    @Test fun noSilentWeekIncomeOrLegacyParameterResetOccursOnReopening() = runTest {
        val f = Fixture()
        f.begin(); val before = f.state
        f.newEngine().availableDeeds(f.state)
        assertEquals(before, f.state)
        assertEquals(17, f.state.satiety)
        assertEquals(29, f.state.fatigue)
    }

    @Test fun incompatibleRulesVersionDoesNotReinterpretAnExistingSave() = runTest {
        val f = Fixture()
        f.begin()
        val changed = GameEngine(f.repo, f.factory, f.rules.copy(id = "different"))
        assertTrue((changed.dispatch(f.request(EngineCommand.OpenNextEvent)) as EngineResult.Blocked).reason is BlockReason.InvalidContent)
    }

    @Test fun completionIsNotAllowedAfterDayEndAndDayCannotEndWithoutFood() = runTest {
        val f = Fixture()
        f.begin(); f.completePlan()
        assertEquals(BlockReason.MustEat, f.blocked(EngineCommand.FinishDay))
        f.endFedDay()
        assertEquals(BlockReason.DayFinished, f.blocked(EngineCommand.OpenNextEvent))
        assertEquals(BlockReason.DayFinished, f.blocked(EngineCommand.Feed("basic")))
    }

    @Test fun factoryRequiresExplicitEffectTimingAndProtectsGoalItems() {
        val f = Fixture()
        val changed = f.content.copy(events = f.content.events.map { if (it.id == "quiet") it.copy(moneyDeltaOnStart = 3) else it })
        try { EventFactory(changed, f.policies, emptyList()); fail() } catch (_: IllegalArgumentException) { }
        val forbidden = f.content.copy(choiceItemEffects = listOf(ChoiceItemEffect("gift", "lore-choice", 0, "rope", ItemOperation.ADD)))
        try { EventFactory(forbidden, f.policies, emptyList()); fail() } catch (_: IllegalArgumentException) { }
    }

    @Test fun namingBeforeTheFirstDayOnlyChangesTheNameAndRejectsStaleEditors() = runTest {
        val f = Fixture()
        val before = f.state
        f.apply(EngineCommand.RenamePet("  Тоша  ", before.pet.name))
        assertEquals(before.copy(pet = before.pet.copy(name = "Тоша")), f.state)
        assertNull(f.state.engine)
        assertEquals(BlockReason.StaleRevision, f.blocked(EngineCommand.RenamePet("Лис", before.pet.name)))
        assertEquals("Тоша", f.state.pet.name)
    }

    @Test fun renamingDoesNotCompleteTheActiveEventOrChangeAnyGameplayValues() = runTest {
        val f = Fixture()
        f.begin()
        f.apply(EngineCommand.OpenNextEvent)
        val before = f.state
        val request = f.request(EngineCommand.RenamePet("Тоша", before.pet.name))
        assertTrue(f.engine.dispatch(request) is EngineResult.Applied)
        assertEquals(before.copy(pet = before.pet.copy(name = "Тоша"),
            engine = before.engine!!.copy(revision = before.engine.revision + 1)), f.state)
        assertEquals(EngineResult.Blocked(BlockReason.StaleRevision), f.engine.dispatch(request))
    }

    @Test fun invalidNameOrCommitFailurePreservesTheWholeSnapshot() = runTest {
        val f = Fixture()
        val before = f.state
        for (invalid in listOf("   ", "Тоша\nЛис", "Тоша\u2028Лис")) {
            assertEquals(BlockReason.InvalidPetName, f.blocked(EngineCommand.RenamePet(invalid, before.pet.name)))
            assertEquals(before, f.state)
        }
        f.repo.failCommit = true
        try {
            f.apply(EngineCommand.RenamePet("Тоша", before.pet.name))
            fail("Failed commit must propagate")
        } catch (_: IOException) { }
        assertEquals(before, f.state)
    }

    @Test fun colorSelectionBeforeDayAndDuringEventOnlyChangesColorAndRejectsStaleWrites() = runTest {
        val f = Fixture()
        val initial = f.state
        f.apply(EngineCommand.SetPetColor(PetColor.SAND, initial.pet.color))
        assertEquals(initial.copy(pet = initial.pet.copy(color = PetColor.SAND)), f.state)
        val chosen = f.state
        assertEquals(BlockReason.StaleRevision, f.blocked(EngineCommand.SetPetColor(
            PetColor.DARK_RUSSET, initial.pet.color)))
        assertEquals(chosen, f.state)
        f.begin()
        f.apply(EngineCommand.OpenNextEvent)
        val before = f.state
        val request = f.request(EngineCommand.SetPetColor(PetColor.DARK_RUSSET, before.pet.color))
        assertTrue(f.engine.dispatch(request) is EngineResult.Applied)
        val after = before.copy(pet = before.pet.copy(color = PetColor.DARK_RUSSET),
            engine = before.engine!!.copy(revision = before.engine.revision + 1))
        assertEquals(after, f.state)
        assertEquals(EngineResult.Blocked(BlockReason.StaleRevision), f.engine.dispatch(request))
        f.repo.failCommit = true
        try { f.apply(EngineCommand.SetPetColor(initial.pet.color, after.pet.color)); fail("Expected commit error") }
        catch (_: IOException) { }
        assertEquals(after, f.state)
    }

    @Test fun weeklyPlanningBlocksFirstEventAndEveryGameplayCommand() = runTest {
        val f = Fixture()
        f.begin()
        f.repo.update { it.copy(economy = EconomyOperations.weekly(it.economy, "week:8", 100)) }
        val before = f.state
        listOf(EngineCommand.OpenNextEvent, EngineCommand.Feed("free"), EngineCommand.FinishDay,
            EngineCommand.StartDeed("fake"), EngineCommand.BuyGoalItem("goal", "rope")).forEach {
            assertEquals(BlockReason.BudgetPlanningRequired, f.blocked(it))
            assertEquals(before, f.state)
        }
    }

    @Test fun earningContentRejectsStartCostAndAnyPaidAnswer() {
        val f = Fixture()
        val paidStart = f.content.copy(events = f.content.events.map {
            if (it.id == "small") it.copy(moneyDeltaOnStart = -1) else it
        })
        assertThrows(IllegalArgumentException::class.java) { EventFactory(paidStart, f.policies, emptyList()) }
        val paidAnswer = f.content.copy(choices = f.content.choices.map {
            if (it.eventId == "small") it.copy(moneyDelta = -1) else it
        })
        assertThrows(IllegalArgumentException::class.java) { EventFactory(paidAnswer, f.policies, emptyList()) }
    }

    @Test fun firstDayPreviewExplainsEntryCostWithoutOpeningOrSpending() = runTest {
        val f = Fixture()
        val content = f.content.copy(events = f.content.events.map {
            if (it.id == "reward") it.copy(type = EventType.WANT, moneyDeltaOnStart = -7) else it
        })
        val engine = GameEngine(f.repo, EventFactory(content,
            f.policies + ("reward" to EventPolicy(0, startEffectsTiming = EffectTiming.OPEN)), emptyList()), f.rules)
        val before = f.state
        val quote = engine.advanceSpending(before, EngineCommand.BeginDay("day", listOf("reward", "quiet", "quiet", "quiet"), true))!!
        assertEquals(SpendingKind.WANT, quote.kind)
        assertEquals(7L, quote.quote.parts.sumOf { it.amount })
        assertEquals(before, f.state)
        assertNull(f.state.engine)
    }

    @Test fun migrationCorrectionIsSeparateFromDayActivityAndResetsNextMorning() = runTest {
        val f = Fixture(balance = 20)
        f.begin()
        val originalOpening = f.day.openingBalance
        f.repo.update { it.copy(economy = it.economy.copy(plan = it.economy.plan.copy(reserve = 15),
            availableBalance = it.economy.availableBalance + 15),
            engine = it.engine!!.copy(balanceAdjustment = 15)) }
        f.completePlan()
        f.endFedDay()
        val summary = f.engine.daySummary(f.state)!!
        assertEquals(originalOpening, summary.openingBalance)
        assertEquals(15L, summary.balanceAdjustment)
        assertEquals(-4L, summary.journal.sumOf { it.moneyDelta })
        assertEquals(summary.journal.sumOf { it.moneyDelta },
            summary.closingBalance - summary.openingBalance - summary.balanceAdjustment)
        f.begin()
        assertEquals(0L, f.day.balanceAdjustment)
        assertEquals(31L, f.day.openingBalance)
    }

    @Test fun pendingBudgetPreservesActiveAndResultUntilTheirOriginalCommandsCanResume() = runTest {
        for (resultSaved in listOf(false, true)) {
            val f = Fixture()
            f.begin(listOf("reward", "quiet", "quiet", "quiet"))
            f.apply(EngineCommand.OpenNextEvent)
            if (resultSaved) f.choose()
            val original = f.state
            val occurrence = f.day.currentEvent!!
            val command = if (resultSaved) EngineCommand.AcknowledgeResult(occurrence.id)
                else EngineCommand.PauseEvent(occurrence.id)
            val stale = f.request(command)
            f.repo.update { current ->
                current.copy(economy = EconomyState(BudgetPlan(0, 0, 0, 0), current.economy.balance,
                    BudgetPlanning("migration", BudgetPlanningReason.MIGRATION, BudgetPlanningStage.ALLOCATION, 0)),
                    engine = current.engine!!.copy(revision = current.engine.revision + 1))
            }
            val pending = f.state
            assertEquals(BlockReason.BudgetPlanningRequired, f.blocked(command))
            assertEquals(pending, f.state)
            assertEquals(original.engine!!.events, f.day.events)
            assertEquals(original.story, f.state.story)
            f.repo.update { current ->
                var economy = EconomyOperations.setAllocation(current.economy, "migration", 0, BudgetSection.NEEDS, 35)
                economy = EconomyOperations.setAllocation(economy, "migration", 1, BudgetSection.RESERVE, economy.unallocated)
                current.copy(economy = EconomyOperations.confirm(economy, "migration", 2),
                    engine = current.engine!!.copy(revision = current.engine.revision + 1))
            }
            assertEquals(original.copy(economy = f.state.economy,
                engine = original.engine.copy(revision = f.day.revision)), f.state)
            assertEquals(EngineResult.Blocked(BlockReason.StaleRevision), f.newEngine().dispatch(stale))
            assertTrue(f.newEngine().dispatch(f.request(command)) is EngineResult.Applied)
            assertEquals(original.economy.balance, f.state.economy.balance)
            assertEquals(original.engine.journal, f.day.journal)
            assertEquals(original.engine.steps, f.day.steps)
            assertEquals(original.engine.energy, f.day.energy)
            assertEquals(original.story.decisions, f.state.story.decisions)
            if (!resultSaved) {
                f.apply(EngineCommand.OpenNextEvent)
                assertEquals(occurrence, f.day.currentEvent)
            } else assertNull(f.day.currentEvent)
        }
    }

    @Test fun energyAndEarlySleepUseTheSameAffordabilityAsTheFormerSingleBalance() = runTest {
        val types = listOf(EventType.STATE, EventType.RANDOM, EventType.WANT, EventType.STORY)
        for (type in types) for (timing in EffectTiming.entries) {
            val f = Fixture(energy = 1)
            val content = f.content.copy(
                events = f.content.events.map { if (it.id == "reward") it.copy(type = type, moneyDeltaOnStart = -2) else it },
                choices = f.content.choices.map { if (it.id == "reward-choice") it.copy(moneyDelta = -4) else it } +
                    EventChoiceDefinition("work", "reward", 1, "Work", 0, null, null, GoalImpact.NEUTRAL),
            )
            val engine = GameEngine(f.repo, EventFactory(content, f.policies + ("reward" to
                EventPolicy(0, startEffectsTiming = timing, choiceEnergyCosts = mapOf("work" to 2))), emptyList()), f.rules)
            f.begin(listOf("reward", "quiet", "quiet", "quiet"))
            for (balance in listOf(2L, 3L, 4L, 5L, 6L, 7L)) for (section in BudgetSection.entries) {
                for (status in listOf(EventStatus.PENDING, EventStatus.PAUSED, EventStatus.ACTIVE)) {
                    val day = f.day.copy(ateToday = true, events = f.day.events.mapIndexed { i, e ->
                        if (i == 0) e.copy(status = status) else e
                    })
                    val state = f.state.copy(
                        economy = EconomyState(BudgetPlan(0, 0, 0, 0).withAmount(section, balance),
                            availableBalance = balance, savingsBalance = 0),
                        engine = day, story = f.state.story.copy(activeEventId = if (status == EventStatus.ACTIVE) "reward" else null),
                    )
                    // Former rule: an unapplied entry charge must be affordable before the paid answer.
                    val entry = if (timing == EffectTiming.COMPLETE || status == EventStatus.PENDING) 2 else 0
                    val canPayWithoutEnergy = balance >= entry + 4
                    val command = if (status == EventStatus.ACTIVE)
                        EngineCommand.FinishDayFromEvent(day.events.first().id) else EngineCommand.OpenNextEvent
                    val expected = if (status == EventStatus.ACTIVE) {
                        if (canPayWithoutEnergy) BlockReason.UnfinishedEvents else null
                    } else if (canPayWithoutEnergy) null else BlockReason.MustSleep
                    assertEquals("$type / $timing / $balance / $section / $status", expected, engine.blockReason(state, command))
                    assertEquals(1, state.engine!!.energy)
                }
            }
        }
    }

    @Test fun spendingPreviewUsesOriginalPriorityAndNeverChangesScheduledOrInsertedEvents() = runTest {
        for (selected in listOf(false, true)) for (paused in listOf(false, true)) {
            val f = Fixture()
            val content = f.content.copy(
                events = f.content.events.map { e -> e.copy(moneyDeltaOnStart = when (e.id) {
                    "quiet" -> -3; "reward" -> -7; "lore" -> -11; else -> 0
                }) },
                items = f.content.items.map { it.copy(priceCoins = 10) },
            )
            val policies = f.policies.mapValues { (id, policy) ->
                if (id in listOf("quiet", "reward", "lore")) policy.copy(startEffectsTiming = EffectTiming.OPEN) else policy
            }
            val goals = if (selected) listOf(GoalCampaign("goal", "lore", listOf("rope"))) else emptyList()
            val engine = GameEngine(f.repo, EventFactory(content, policies, emptyList(), goals), f.rules)
            f.begin(listOf("quiet", "reward", "quiet", "quiet"))
            f.repo.update { it.copy(selectedGoalId = if (selected) "goal" else null,
                engine = it.engine!!.copy(events = it.engine.events.map { e ->
                    if (paused && e.eventId == "reward") e.copy(status = EventStatus.PAUSED) else e
                })) }
            val before = f.state
            val expectedId = when { selected -> "lore"; paused -> "reward"; else -> "quiet" }
            val expectedCost = when { selected -> 11L; paused -> 0L; else -> 3L }
            val request = f.request(EngineCommand.OpenNextEvent)
            val withoutPreview = engine.transition(before, request)
            repeat(3) {
                val preview = engine.nextEventSpendingPreview(before)
                if (paused && !selected) assertNull(preview)
                else assertEquals(expectedCost, preview!!.quote.parts.sumOf { it.amount })
                assertEquals(before, f.state)
            }
            val applied = (engine.dispatch(request) as EngineResult.Applied).state
            assertEquals(withoutPreview, applied)
            assertEquals(expectedId, applied.engine!!.currentEvent!!.eventId)
            assertEquals(expectedCost, before.economy.balance - applied.economy.balance)
            if (!selected) assertEquals(before.engine!!.events.map { it.id }, applied.engine.events.map { it.id })
        }
    }

    @Test fun paidEventsPreserveAllNonFinancialOutcomesAcrossDifferentAllocations() = runTest {
        for (type in listOf(EventType.STATE, EventType.RANDOM, EventType.WANT, EventType.STORY)) {
            for (timing in EffectTiming.entries) for (delta in listOf(-4L, 7L)) {
                val f = Fixture()
                val content = f.content.copy(
                    events = f.content.events.map { if (it.id == "reward")
                        it.copy(type = type, moneyDeltaOnStart = -6) else it },
                    choices = f.content.choices.map { if (it.id == "reward-choice")
                        it.copy(moneyDelta = delta, petStateAfter = PetVisualState.HAPPY) else it },
                    items = f.content.items + ItemDefinition("ticket", "Ticket", ""),
                    choiceItemEffects = listOf(ChoiceItemEffect("ticket-effect", "reward-choice", 0, "ticket", ItemOperation.ADD)),
                )
                val factory = EventFactory(content, f.policies + ("reward" to EventPolicy(1, startEffectsTiming = timing)),
                    listOf(MealDefinition("basic", 4, null)))
                val engine = GameEngine(f.repo, factory, f.rules)
                f.begin(listOf("reward", "quiet", "quiet", "quiet"))
                val original = f.state
                fun play(plan: BudgetPlan): List<GameState> {
                    var state = original.copy(economy = EconomyState(plan, availableBalance = 100, savingsBalance = 0))
                    val outcomes = mutableListOf<GameState>()
                    var id = 0
                    fun step(command: EngineCommand) {
                        state = engine.transition(state, EngineRequest("regression:${++id}", state.engine!!.revision, command))
                        outcomes += state.copy(economy = EconomyState(BudgetPlan(0, 0, 0, state.economy.balance)))
                    }
                    step(EngineCommand.OpenNextEvent)
                    step(EngineCommand.PauseEvent(state.engine!!.currentEvent!!.id))
                    step(EngineCommand.OpenNextEvent)
                    step(EngineCommand.CompleteEvent(state.engine!!.currentEvent!!.id, "reward-choice"))
                    assertEquals(100 - 6 + delta, state.economy.balance)
                    assertEquals(4, state.engine!!.energy)
                    assertEquals(1, state.engine!!.steps)
                    assertEquals(PetVisualState.HAPPY, state.pet.visualState)
                    assertEquals(listOf("ticket"), state.ownedItems.map { it.itemId })
                    step(EngineCommand.Feed("basic"))
                    repeat(3) {
                        step(EngineCommand.OpenNextEvent)
                        step(EngineCommand.CompleteEvent(state.engine!!.currentEvent!!.id, "quiet-choice"))
                    }
                    step(EngineCommand.FinishDay)
                    step(EngineCommand.BeginDay("day", List(4) { "quiet" }))
                    assertEquals(2, state.engine!!.day)
                    assertEquals(5, state.engine!!.energy)
                    assertEquals(0, state.engine!!.steps)
                    return outcomes
                }
                val reference = play(BudgetPlan(0, 0, 0, 100))
                for (plan in listOf(BudgetPlan(100, 0, 0, 0), BudgetPlan(0, 100, 0, 0),
                    BudgetPlan(0, 0, 100, 0), BudgetPlan(90, 2, 3, 5))) {
                    assertEquals("$type / $timing / $delta / $plan", reference, play(plan))
                }
                assertEquals(original, f.state)
            }
        }
    }

    private class Fixture(energy: Int = 5, hunger: Int = 50, balance: Long = 100, deedKind: DeedGameKind? = null) {
        val content = content()
        val policies = content.events.associate { event -> event.id to EventPolicy(
            energyCost = when (event.id) { "small", "drain" -> 1; "medium" -> 2; "large" -> 3; else -> 0 },
            requiredItemIds = if (event.id == "locked") setOf("rope") else emptySet(),
            previousLoreEventId = if (event.id == "locked") "lore" else null,
            chapterEntryDayId = if (event.id == "final") "next-day" else null,
            deedGameKind = if (event.id == "small") deedKind else null,
        ) }
        val factory = EventFactory(content, policies, listOf(
            MealDefinition("basic", 4, null),
            MealDefinition("luxury", 8, PetVisualState.HAPPY),
            MealDefinition("free", 0, null, minOf(3, energy)),
        ))
        val rules = EngineRules("test-rules", energy, hunger, 1)
        val repo = MemoryRepository(GameState(
            PetState("BACKPACK", PetVisualState.NORMAL), EconomyState(BudgetPlan(0, 0, balance, 0),
                availableBalance = balance, savingsBalance = 0),
            StoryState(null, null, null, emptyList()), 17, 29, emptyList(),
        ))
        val engine = newEngine()
        val state get() = repo.value
        val day get() = state.engine!!
        private var sequence = 0
        fun newEngine() = GameEngine(repo, factory, rules)
        fun request(command: EngineCommand) = EngineRequest("request-${++sequence}", state.engine?.revision, command)
        suspend fun apply(command: EngineCommand): GameState {
            val result = engine.dispatch(request(command))
            assertTrue("$command was $result", result is EngineResult.Applied)
            return (result as EngineResult.Applied).state
        }
        suspend fun blocked(command: EngineCommand): BlockReason = (engine.dispatch(request(command)) as EngineResult.Blocked).reason
        suspend fun begin(plan: List<String> = List(4) { "quiet" }) { apply(EngineCommand.BeginDay("day", plan)) }
        suspend fun ack() { apply(EngineCommand.AcknowledgeResult(day.currentEvent!!.id)) }
        suspend fun choose() {
            val current = day.currentEvent!!
            apply(EngineCommand.Choose(current.id, "${current.eventId}-choice"))
        }
        suspend fun completeOne() {
            apply(EngineCommand.OpenNextEvent)
            if (day.currentEvent!!.status == EventStatus.ACTIVE) choose()
            ack()
        }
        suspend fun completePlan() { while (day.events.any { it.status == EventStatus.PENDING }) completeOne() }
        suspend fun endFedDay() { apply(EngineCommand.Feed("basic")); apply(EngineCommand.FinishDay) }
    }

    private class MemoryRepository(initial: GameState) : GameRepository {
        private val flow = MutableStateFlow<GameState?>(initial)
        private val mutex = Mutex()
        var failCommit = false
        val value get() = flow.value!!
        override fun observe() = flow
        override suspend fun read() = flow.value
        override suspend fun initializeIfAbsent(initial: GameState) = mutex.withLock {
            flow.value ?: initial.also { flow.value = it }
        }
        override suspend fun update(transform: (GameState) -> GameState) = mutex.withLock {
            val next = transform(value)
            if (failCommit) throw IOException("Simulated failed transaction")
            next.also { flow.value = it }
        }
    }

    companion object {
        private fun content(): StoryContent {
            val events = listOf("quiet", "small", "medium", "large", "drain", "lore", "reward", "expensive", "locked", "final").map { id ->
                EventDefinition(id, when (id) {
                    "small", "medium", "large" -> EventType.EARNING
                    "lore", "locked", "final" -> EventType.STORY
                    else -> EventType.RANDOM
                }, id, "Test content", null, null, null, 0, null, if (id == "final") "next" else null)
            }
            return StoryContent(
                chapters = listOf(ChapterDefinition("chapter", "Chapter", "goal"), ChapterDefinition("next", "Next", "next-goal")),
                days = listOf(GameDayDefinition("day", "chapter", 1), GameDayDefinition("next-day", "next", 1)),
                events = events,
                choices = events.map { e -> EventChoiceDefinition("${e.id}-choice", e.id, 0, "Continue", when (e.id) {
                    "small", "medium", "large" -> 10L
                    "reward" -> 7L
                    "expensive" -> -4L
                    else -> 0L
                }, null, null, GoalImpact.NEUTRAL) },
                items = listOf(ItemDefinition("rope", "Rope", "")),
                goals = listOf(GoalDefinition("goal", "Goal", ""), GoalDefinition("next-goal", "Next", "")),
                requiredItems = listOf(GoalRequiredItem("goal", "rope")),
            )
        }
    }
}
