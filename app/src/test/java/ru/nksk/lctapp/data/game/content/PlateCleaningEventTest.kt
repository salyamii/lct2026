package ru.nksk.lctapp.data.game.content

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.R
import ru.nksk.lctapp.app.createInitialGameState
import ru.nksk.lctapp.data.game.newDefinitionsComparedTo
import ru.nksk.lctapp.domain.content.*
import ru.nksk.lctapp.domain.economy.BudgetPlan
import ru.nksk.lctapp.domain.economy.EconomyState
import ru.nksk.lctapp.domain.engine.*
import ru.nksk.lctapp.domain.game.GameRepository
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.minigame.DeedGameKind
import ru.nksk.lctapp.domain.minigame.DeedGameScore
import ru.nksk.lctapp.domain.minigame.TargetStopState
import ru.nksk.lctapp.feature.tasks.ui.storyGameTheme

class PlateCleaningEventTest {
    private val catalog = bundledGameCatalog()

    @Test fun installedV2RemainsImmutableAndInstallationOnlyAppendsV3Definitions() {
        // This frozen installed row must not be reconstructed from the incoming version.
        val installedEvent = EventDefinition(LEGACY, EventType.RANDOM, "Пластина покрылась налётом",
            "На звёздной пластине выступил плотный налёт. Обычная салфетка его не берёт — нужен очищающий состав.",
            null, null, null, 0, null, null)
        val installedChoice = EventChoiceDefinition("$LEGACY:pay", LEGACY, 0, "Оплатить · 3",
            -3, null, null, GoalImpact.NEUTRAL)
        val oldCatalog = catalog.content.copy(
            events = catalog.content.events.filterNot { it.id == CURRENT }.map {
                if (it.id == LEGACY) installedEvent else it
            },
            choices = catalog.content.choices.filterNot { it.eventId == CURRENT }.map {
                if (it.id == installedChoice.id) installedChoice else it
            },
        )

        val added = catalog.content.newDefinitionsComparedTo(oldCatalog)

        assertEquals(installedEvent, catalog.content.events.single { it.id == LEGACY })
        assertEquals(listOf(installedChoice), catalog.content.choices.filter { it.eventId == LEGACY })
        assertEquals(listOf(CURRENT), added.events.map { it.id })
        assertEquals(listOf(PAY, WORK), added.choices.sortedBy { it.position }.map { it.id })
        assertEquals(StoryContent(events = added.events, choices = added.choices), added)
        assertEquals(StoryContent(), catalog.content.newDefinitionsComparedTo(catalog.content))

        assertTrue(CURRENT in catalog.dailyEventPool)
        assertFalse(LEGACY in catalog.dailyEventPool)
        assertTrue(LEGACY in catalog.policies.getValue(CURRENT).scheduling.previousEventIds)
        assertEquals(CURRENT, catalog.eventReplacements[LEGACY])
    }

    @Test fun manualChoiceHasPrecisionBoardAndTheTarnishedPlateProp() {
        EventFactory(catalog.content, catalog.policies, catalog.meals, catalog.goals, catalog.storyCampaign)
        val policy = catalog.policies.getValue(CURRENT)
        assertEquals(mapOf(WORK to DeedGameKind.PRECISION), policy.choiceGameKinds)
        assertEquals(0, policy.energyFor(PAY))
        assertEquals(2, policy.energyFor(WORK))
        assertEquals(-3L, catalog.content.choices.single { it.id == PAY }.moneyDelta)
        assertEquals(0L, catalog.content.choices.single { it.id == WORK }.moneyDelta)
        assertTrue(policy.scheduling.blocksStoryUntilResolved)
        assertEquals(R.drawable.event_star_plate_tarnished, storyGameTheme(CURRENT)?.objectRes)
    }

    @Test fun payingSpendsThreeCoinsWithoutSpendingEnergyAndUnblocksTheStory() = runTest {
        val f = fixture(money = 20, energy = 1)
        val before = f.repo.value
        assertFalse(catalog.storyProgress(before).eligible(catalog.introductionId))

        assertApplied(f.send(EngineCommand.CompleteEvent(OCCURRENCE, PAY)))

        val after = f.repo.value
        val afterDay = checkNotNull(after.engine)
        assertEquals(17L, after.economy.availableBalance)
        assertEquals(before.economy.savingsBalance, after.economy.savingsBalance)
        assertEquals(1, afterDay.energy)
        assertEquals(before.engine!!.steps + 1, afterDay.steps)
        assertEquals(EventStatus.COMPLETED, afterDay.events.single().status)
        assertEquals(listOf(PAY), after.story.decisions.map { it.choiceId })
        assertTrue(catalog.storyProgress(after).eligible(catalog.introductionId))
    }

    @Test fun manualChoiceCannotBypassTheBoardAndStartingItHasNoGameplayEffects() = runTest {
        val f = fixture()
        val before = f.repo.value
        assertEquals(EngineResult.Blocked(BlockReason.InvalidEventAction),
            f.send(EngineCommand.CompleteEvent(OCCURRENCE, WORK)))
        assertEquals(before, f.repo.value)

        assertApplied(f.send(EngineCommand.StartStoryGame(OCCURRENCE, WORK)))

        val started = f.repo.value
        val beforeDay = checkNotNull(before.engine)
        val startedDay = checkNotNull(started.engine)
        assertEquals(before.economy, started.economy)
        assertEquals(before.story, started.story)
        assertEquals(before.ownedItems, started.ownedItems)
        assertEquals(before.satiety, started.satiety)
        assertEquals(before.fatigue, started.fatigue)
        assertEquals(beforeDay.energy, startedDay.energy)
        assertEquals(beforeDay.steps, startedDay.steps)
        assertEquals(beforeDay.events, startedDay.events)
        assertEquals(beforeDay.journal, startedDay.journal)
        assertFalse(catalog.storyProgress(started).eligible(catalog.introductionId))
    }

    @Test fun completedManualGameUsesTwoEnergyWithoutMoneyAndCannotCompleteTwice() = runTest {
        val f = fixture(money = 0, energy = 5)
        assertApplied(f.send(EngineCommand.StartStoryGame(OCCURRENCE, WORK)))
        val before = f.repo.value
        val command = EngineCommand.CompleteStoryGame(OCCURRENCE, WORK, completedPrecision())
        val request = f.request(command)

        assertApplied(f.session.dispatch(request))

        val after = f.repo.value
        val afterDay = checkNotNull(after.engine)
        assertEquals(before.economy, after.economy)
        assertEquals(3, afterDay.energy)
        assertEquals(before.engine!!.steps + 1, afterDay.steps)
        assertEquals(EventStatus.COMPLETED, afterDay.events.single().status)
        assertEquals(listOf(WORK), after.story.decisions.map { it.choiceId })
        assertTrue(catalog.storyProgress(after).eligible(catalog.introductionId))

        // The in-memory repository has no Room command receipts: the revision and
        // occurrence guards must still prevent both a stale retry and a fresh request.
        assertEquals(EngineResult.Blocked(BlockReason.StaleRevision), f.session.dispatch(request))
        assertEquals(after, f.repo.value)
        assertEquals(EngineResult.Blocked(BlockReason.InvalidEventAction), f.send(command))
        assertEquals(after, f.repo.value)
    }

    @Test fun insufficientEnergyBlocksBothStartingAndSubmittingManualWork() = runTest {
        val f = fixture(money = 20, energy = 1)
        val before = f.repo.value

        assertEquals(EngineResult.Blocked(BlockReason.MustSleep),
            f.send(EngineCommand.StartStoryGame(OCCURRENCE, WORK)))
        assertEquals(EngineResult.Blocked(BlockReason.MustSleep),
            f.send(EngineCommand.CompleteStoryGame(OCCURRENCE, WORK, completedPrecision())))

        assertEquals(before, f.repo.value)
        assertFalse(catalog.storyProgress(f.repo.value).eligible(catalog.introductionId))
        assertNull(f.session.engine.blockReason(f.repo.value, EngineCommand.CompleteEvent(OCCURRENCE, PAY)))
    }

    private fun completedPrecision() = checkNotNull(DeedGameScore.fromPrecision(
        TargetStopState(zoneStart = 10, round = TargetStopState.ROUNDS, hits = 3, lastHit = true),
    ))

    private suspend fun fixture(money: Long = 20, energy: Int = 5): Fixture {
        val initial = createInitialGameState()
        val selectedGoal = catalog.goals.first { catalog.storyProgress(initial).goalAvailable(it) }.goalId
        val state = initial.copy(
            selectedGoalId = selectedGoal,
            economy = EconomyState(BudgetPlan(0, 0, 0, money), availableBalance = money, savingsBalance = 0),
            story = initial.story.copy(currentDayId = catalog.storyDayId, activeEventId = CURRENT),
            engine = EngineState(catalog.rules.id, 0, 3, DayPhase.RUNNING, 0, energy, true, null, money,
                listOf(EventOccurrence(OCCURRENCE, CURRENT, EventOrigin.SCHEDULE, EventStatus.ACTIVE)), emptyList()),
        )
        return Fixture(state).also { it.session.prepare() }
    }

    private fun assertApplied(result: EngineResult) = assertTrue("Expected Applied, got $result", result is EngineResult.Applied)

    private inner class Fixture(initial: GameState) {
        val repo = MemoryRepository(initial)
        val session = GameSession(repo, object : StoryContentRepository {
            override suspend fun read() = catalog.content
            override suspend fun install(content: StoryContent) = Unit
        }, catalog, initial)
        private var sequence = 0
        fun request(command: EngineCommand) = EngineRequest("plate-${++sequence}", repo.value.engine?.revision, command)
        suspend fun send(command: EngineCommand) = session.dispatch(request(command))
    }

    private class MemoryRepository(initial: GameState) : GameRepository {
        private val state = MutableStateFlow(initial)
        val value get() = state.value
        override fun observe() = state
        override suspend fun read() = value
        override suspend fun initializeIfAbsent(initial: GameState) = value
        override suspend fun update(transform: (GameState) -> GameState): GameState = transform(value).also { state.value = it }
    }

    private companion object {
        const val LEGACY = "figma-2326-160-v2"
        const val CURRENT = "figma-2326-160-v3"
        const val PAY = "$CURRENT:pay"
        const val WORK = "$CURRENT:work"
        const val OCCURRENCE = "plate-occurrence"
    }
}
