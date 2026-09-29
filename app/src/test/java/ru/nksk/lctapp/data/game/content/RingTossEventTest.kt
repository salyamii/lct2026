package ru.nksk.lctapp.data.game.content

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.R
import ru.nksk.lctapp.app.createInitialGameState
import ru.nksk.lctapp.data.game.newDefinitionsComparedTo
import ru.nksk.lctapp.domain.analytics.AnalyticsFact
import ru.nksk.lctapp.domain.analytics.DecisionContext
import ru.nksk.lctapp.domain.content.*
import ru.nksk.lctapp.domain.economy.BudgetPlan
import ru.nksk.lctapp.domain.economy.EconomyState
import ru.nksk.lctapp.domain.engine.*
import ru.nksk.lctapp.domain.game.GameRepository
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.minigame.DeedGameKind
import ru.nksk.lctapp.domain.minigame.DeedGameScore
import ru.nksk.lctapp.domain.minigame.TargetStopState
import ru.nksk.lctapp.domain.pet.PetVisualState
import ru.nksk.lctapp.domain.story.StoryDecision
import ru.nksk.lctapp.feature.tasks.ui.storyGameTheme

class RingTossEventTest {
    private val catalog = bundledGameCatalog()

    @Test fun addsPlayableVersionWithoutRewritingInstalledPurchase() {
        val installedEvent = EventDefinition(LEGACY_RING_TOSS, EventType.WANT, "Ярмарочная игра за 7",
            "На ярмарке предлагают бросить кольца за 7 монет. Это развлечение: денежных призов здесь нет. Поиграть или пройти мимо?",
            null, null, null, 0, null, null)
        val installedChoices = listOf(
            EventChoiceDefinition("$LEGACY_RING_TOSS:buy", LEGACY_RING_TOSS, 0, "Купить · 7", -7,
                null, PetVisualState.HAPPY, GoalImpact.NEUTRAL),
            EventChoiceDefinition("$LEGACY_RING_TOSS:pass", LEGACY_RING_TOSS, 1, "Пройти мимо", 0,
                null, null, GoalImpact.NEUTRAL),
        )
        assertEquals(installedEvent, catalog.content.events.single { it.id == LEGACY_RING_TOSS })
        assertEquals(installedChoices, catalog.content.choices.filter { it.eventId == LEGACY_RING_TOSS })
        assertTrue(catalog.policies.getValue(LEGACY_RING_TOSS).choiceGameKinds.isEmpty())
        assertTrue(catalog.policies.getValue(LEGACY_RING_TOSS).choiceEnergyRestores.isEmpty())
        val previousContent = catalog.content.copy(
            events = catalog.content.events.filterNot { it.id == RING_TOSS },
            choices = catalog.content.choices.filterNot { it.eventId == RING_TOSS },
        )
        val additions = catalog.content.newDefinitionsComparedTo(previousContent)
        assertEquals(listOf(RING_TOSS), additions.events.map { it.id })
        assertEquals(listOf(PLAY, PASS), additions.choices.map { it.id })
        assertTrue(RING_TOSS in catalog.dailyEventPool)
        assertFalse(LEGACY_RING_TOSS in catalog.dailyEventPool)
        assertEquals(RING_TOSS, catalog.eventReplacements[LEGACY_RING_TOSS])
        val policy = catalog.policies.getValue(RING_TOSS)
        assertEquals(mapOf(PLAY to DeedGameKind.PRECISION), policy.choiceGameKinds)
        assertEquals(mapOf(PLAY to 1), policy.choiceEnergyRestores)
        assertEquals(0, policy.energyFor(PLAY))
        assertTrue(LEGACY_RING_TOSS in policy.scheduling.previousEventIds)
        EventFactory(catalog.content, catalog.policies, catalog.meals, catalog.goals, catalog.storyCampaign)
        assertEquals(R.drawable.prop_fair_ring_toss,
            storyGameTheme(catalog.cards.getValue(RING_TOSS).presentation.media, DeedGameKind.PRECISION)?.objectRes)
    }

    @Test fun openingBoardCannotChargeRewardOrBypassTheGame() = runTest {
        val f = fixture()
        val before = f.repo.value
        assertEquals(EngineResult.Blocked(BlockReason.InvalidEventAction),
            f.send(EngineCommand.CompleteEvent(OCCURRENCE, PLAY)))
        assertEquals(before, f.repo.value)
        assertApplied(f.send(EngineCommand.StartStoryGame(OCCURRENCE, PLAY)))
        val opened = f.repo.value
        assertEquals(before.economy, opened.economy)
        assertEquals(before.pet, opened.pet)
        assertEquals(before.story, opened.story)
        assertEquals(before.engine!!.energy, opened.engine!!.energy)
        assertEquals(before.engine!!.steps, opened.engine!!.steps)
        assertEquals(before.engine!!.journal, opened.engine!!.journal)
        assertEquals(EventStatus.ACTIVE, opened.engine!!.currentEvent!!.status)
        assertApplied(f.send(EngineCommand.PauseEvent(OCCURRENCE)))
        assertEquals(before.economy, f.repo.value.economy)
        assertEquals(before.engine!!.energy, f.repo.value.engine!!.energy)
        assertTrue(f.repo.value.story.decisions.isEmpty())
    }

    @Test fun fiveThrowsChargeSevenRestoreOneAndCommitOnlyOnceRegardlessOfHits() = runTest {
        for (hits in listOf(0, TargetStopState.ROUNDS)) {
            val f = fixture(energy = 2)
            assertApplied(f.send(EngineCommand.StartStoryGame(OCCURRENCE, PLAY)))
            val before = f.repo.value
            val command = EngineCommand.CompleteStoryGame(OCCURRENCE, PLAY, completedPrecision(hits))
            val request = f.request(command)
            assertApplied(f.session.dispatch(request))
            val after = f.repo.value
            assertEquals(13L, after.economy.availableBalance)
            assertEquals(before.economy.savingsBalance, after.economy.savingsBalance)
            assertEquals(3, after.engine!!.energy)
            assertEquals(before.engine!!.steps + 1, after.engine!!.steps)
            assertEquals(PetVisualState.HAPPY, after.pet.visualState)
            assertEquals(listOf(PLAY), after.story.decisions.map { it.choiceId })
            assertEquals(EventStatus.COMPLETED, after.engine!!.events.single().status)
            val outcome = after.engine!!.journal.single { it.sourceId == PLAY }
            assertEquals(-7L, outcome.moneyDelta)
            assertEquals(1, outcome.energyDelta)
            assertApplied(f.session.dispatch(request))
            assertEquals(after, f.repo.value)
            assertEquals(EngineResult.Blocked(BlockReason.InvalidEventAction), f.send(command))
            assertEquals(after, f.repo.value)
        }
    }

    @Test fun energyIsCappedAndPassingHasNeitherCostNorRecovery() = runTest {
        val full = fixture(energy = catalog.rules.fullEnergy)
        assertApplied(full.send(EngineCommand.CompleteStoryGame(OCCURRENCE, PLAY, completedPrecision())))
        assertEquals(catalog.rules.fullEnergy, full.repo.value.engine!!.energy)
        assertEquals(13L, full.repo.value.economy.availableBalance)
        val pass = fixture(energy = 2)
        val before = pass.repo.value
        assertApplied(pass.send(EngineCommand.CompleteEvent(OCCURRENCE, PASS)))
        assertEquals(before.economy, pass.repo.value.economy)
        assertEquals(2, pass.repo.value.engine!!.energy)
        assertEquals(listOf(PASS), pass.repo.value.story.decisions.map { it.choiceId })
    }

    @Test fun latestMoneyAndExistingNeedGuardsApplyBeforeAnyRecovery() = runTest {
        for ((money, energy, ate, steps, expected) in listOf(
            GuardCase(6, 2, true, 0, BlockReason.InsufficientMoney(7)),
            GuardCase(20, 0, true, 0, BlockReason.MustSleep),
            GuardCase(20, 2, false, catalog.rules.hungerBlocksAtStep, BlockReason.MustEat),
        )) {
            val f = fixture(money, energy, ate, steps)
            val before = f.repo.value
            assertEquals(EngineResult.Blocked(expected), f.send(EngineCommand.StartStoryGame(OCCURRENCE, PLAY)))
            assertEquals(EngineResult.Blocked(expected),
                f.send(EngineCommand.CompleteStoryGame(OCCURRENCE, PLAY, completedPrecision())))
            assertEquals(before, f.repo.value)
        }
        val f = fixture()
        assertApplied(f.send(EngineCommand.StartStoryGame(OCCURRENCE, PLAY)))
        f.repo.update { it.copy(economy = EconomyState(BudgetPlan(0, 0, 0, 6), availableBalance = 6, savingsBalance = 0)) }
        val before = f.repo.value
        assertEquals(EngineResult.Blocked(BlockReason.InsufficientMoney(7)),
            f.send(EngineCommand.CompleteStoryGame(OCCURRENCE, PLAY, completedPrecision())))
        assertEquals(before, f.repo.value)
    }

    @Test fun prepareUpdatesOnlyUnresolvedOldOccurrencesWithoutPlayingOrChangingHistory() = runTest {
        for (status in EventStatus.entries) {
            val initial = state(eventId = LEGACY_RING_TOSS).let { current -> current.copy(
                engine = current.engine!!.copy(events = current.engine!!.events.map { it.copy(status = status) })) }
            val f = Fixture(initial)
            f.session.prepare()
            val after = f.repo.value
            val expected = if (status in setOf(EventStatus.RESULT, EventStatus.COMPLETED)) LEGACY_RING_TOSS else RING_TOSS
            assertEquals(status.name, expected, after.engine!!.events.single().eventId)
            assertEquals(initial.economy, after.economy)
            assertEquals(initial.engine!!.energy, after.engine!!.energy)
            assertEquals(initial.engine!!.steps, after.engine!!.steps)
            assertEquals(initial.story.decisions, after.story.decisions)
        }
        val decided = state(eventId = LEGACY_RING_TOSS).let { it.copy(story = it.story.copy(
            decisions = listOf(StoryDecision("$OCCURRENCE:decision", "$LEGACY_RING_TOSS:buy")))) }
        val historical = Fixture(decided)
        historical.session.prepare()
        assertEquals(LEGACY_RING_TOSS, historical.repo.value.engine!!.currentEvent!!.eventId)
        assertEquals(decided.story.decisions, historical.repo.value.story.decisions)
    }

    private data class GuardCase(val money: Long, val energy: Int, val ate: Boolean, val steps: Int, val reason: BlockReason)
    private fun completedPrecision(hits: Int = 3) = checkNotNull(DeedGameScore.fromPrecision(
        TargetStopState(zoneStart = 10, round = TargetStopState.ROUNDS, hits = hits, lastHit = hits > 0)))
    private suspend fun fixture(money: Long = 20, energy: Int = 2, ate: Boolean = true, steps: Int = 0) =
        Fixture(state(money, energy, ate, steps)).also { it.session.prepare() }
    private fun state(money: Long = 20, energy: Int = 2, ate: Boolean = true, steps: Int = 0,
        eventId: String = RING_TOSS): GameState = createInitialGameState().let { initial -> initial.copy(
        pet = initial.pet.copy(selectedLookId = "PLAIN"), selectedGoalId = null,
        economy = EconomyState(BudgetPlan(0, 0, 0, money), availableBalance = money, savingsBalance = 0),
        story = initial.story.copy(currentDayId = catalog.storyDayId, activeEventId = eventId),
        engine = EngineState(catalog.rules.id, 0, 3, DayPhase.RUNNING, steps, energy, ate, null, money,
            listOf(EventOccurrence(OCCURRENCE, eventId, EventOrigin.SCHEDULE, EventStatus.ACTIVE)), emptyList()),
    ) }
    private fun assertApplied(result: EngineResult) = assertTrue("Expected Applied, got $result", result is EngineResult.Applied)
    private inner class Fixture(initial: GameState) {
        val repo = MemoryRepository(initial)
        val session = GameSession(repo, object : StoryContentRepository {
            override suspend fun read() = catalog.content
            override suspend fun install(content: StoryContent) = Unit
        }, catalog, initial)
        private var sequence = 0
        fun request(command: EngineCommand) = EngineRequest("ring-toss-${++sequence}", repo.value.engine?.revision, command)
        suspend fun send(command: EngineCommand) = session.dispatch(request(command))
    }
    private class MemoryRepository(initial: GameState) : GameRepository {
        private val state = MutableStateFlow(initial)
        private data class Receipt(val request: EngineRequest, val context: DecisionContext?, val fingerprint: String?)
        private val receipts = mutableMapOf<String, Receipt>()
        val value get() = state.value
        override fun observe() = state
        override suspend fun read() = value
        override suspend fun initializeIfAbsent(initial: GameState) = value
        override suspend fun update(transform: (GameState) -> GameState): GameState = transform(value).also { state.value = it }
        override suspend fun commit(request: EngineRequest, context: DecisionContext?, contentFingerprint: String?,
            facts: (GameState, GameState, String, Long) -> List<AnalyticsFact>, transform: (GameState) -> GameState): GameState {
            val identity = Receipt(request, context, contentFingerprint)
            receipts[request.id]?.let { check(it == identity); return value }
            val next = transform(value)
            receipts[request.id] = identity
            state.value = next
            return next
        }
    }
    private companion object {
        const val PLAY = "$RING_TOSS:buy"
        const val PASS = "$RING_TOSS:pass"
        const val OCCURRENCE = "ring-toss-occurrence"
    }
}
