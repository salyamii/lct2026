package ru.nksk.lctapp.domain.engine

import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.domain.analytics.AnalyticsFact
import ru.nksk.lctapp.domain.analytics.DecisionContext
import ru.nksk.lctapp.domain.content.*
import ru.nksk.lctapp.domain.demo.DemoPreferences
import ru.nksk.lctapp.domain.demo.DemoPreferencesRepository
import ru.nksk.lctapp.domain.economy.BudgetPlan
import ru.nksk.lctapp.domain.economy.EconomyState
import ru.nksk.lctapp.domain.game.GameRepository
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.history.CanonicalLedger
import ru.nksk.lctapp.domain.history.HistoryCodec
import ru.nksk.lctapp.domain.pet.PetState
import ru.nksk.lctapp.domain.pet.PetVisualState
import ru.nksk.lctapp.domain.story.StoryState

class DemoModeTest {
    @Test fun disabledDemoFlagKeepsExactLegacyRequestBytesEvenWithEncodeDefaults() {
        val json = Json { encodeDefaults = true; classDiscriminator = "_type" }
        val legacy = LegacyRequest("old-meal", 7, EngineCommand.Feed("basic"))
        val oldBytes = json.encodeToString(legacy)
        val request = EngineRequest(legacy.id, legacy.expectedRevision, legacy.command)
        assertEquals(oldBytes, json.encodeToString(request))
        assertEquals(oldBytes, HistoryCodec.encodeRequest(request))
        val decoded = json.decodeFromString<EngineRequest>(oldBytes)
        assertFalse(decoded.demoMode)
        assertEquals(oldBytes, HistoryCodec.encodeRequest(decoded))

        val demo = request.copy(demoMode = true)
        val demoBytes = json.encodeToString(demo)
        assertTrue(demoBytes.contains("\"demoMode\":true"))
        assertEquals(demo, json.decodeFromString<EngineRequest>(demoBytes))
    }

    @Test fun exhaustedDemoChoiceFinishesAtFullEnergyWhileOrdinaryChoiceStaysBlocked() = runTest {
        val fixture = Fixture(energy = 0)
        val before = fixture.repository.value
        val command = EngineCommand.CompleteEvent("occurrence", "work-choice")
        assertEquals(EngineResult.Blocked(BlockReason.MustSleep),
            fixture.engine.dispatch(EngineRequest("ordinary", 0, command)))
        assertEquals(before, fixture.repository.value)

        val result = fixture.engine.dispatch(EngineRequest("demo", 0, command, demoMode = true))
        assertTrue(result is EngineResult.Applied)
        assertEquals(5, fixture.repository.value.engine!!.energy)
        assertNull(fixture.repository.value.engine!!.nextMorningEnergy)
        assertNull(fixture.repository.value.engine!!.currentEvent)
        assertEquals(PetVisualState.NORMAL, fixture.repository.value.pet.visualState)
        assertTrue(fixture.repository.commits.getValue("demo").facts.isEmpty())
    }

    @Test fun demoGoalAtZeroFundsPreservesBothAccountsAndAllocationAndHasAZeroReceipt() = runTest {
        val fixture = Fixture(coins = 0, activeEvent = false)
        val before = fixture.repository.value
        val command = EngineCommand.BuyGoalItem("goal", "part")
        assertTrue(fixture.engine.dispatch(EngineRequest("normal-purchase", 0, command)) is EngineResult.Blocked)
        assertEquals(before, fixture.repository.value)

        assertTrue(fixture.engine.dispatch(EngineRequest("demo-purchase", 0, command, demoMode = true)) is EngineResult.Applied)
        val after = fixture.repository.value
        assertEquals(before.economy, after.economy)
        assertEquals(listOf("part"), after.ownedItems.map { it.itemId })
        val receipt = after.engine!!.journal.single { it.kind == DayJournalKind.ITEM_PURCHASE }
        assertEquals("part", receipt.sourceId)
        assertEquals(0L, receipt.moneyDelta)
        val commit = fixture.repository.commits.getValue("demo-purchase")
        assertTrue(commit.request.demoMode)
        assertTrue(commit.facts.isEmpty())
        assertTrue(CanonicalLedger.fromTransition(before, after, commit.request).isEmpty())
        assertEquals(EngineResult.Blocked(BlockReason.ItemAlreadyOwned), fixture.engine.dispatch(
            EngineRequest("duplicate-purchase", after.engine!!.revision, command, demoMode = true)))
    }

    @Test fun lostCommittedReplyRetainsCapturedDemoRulesWhenPreferenceIsDisabled() = runTest {
        val fixture = Fixture(energy = 0, coins = 0, activeEvent = false)
        val preferences = MutablePreferences(true)
        val session = fixture.session(preferences)
        val request = EngineRequest("uncertain-demo", 0, EngineCommand.BuyGoalItem("goal", "part"))
        fixture.repository.failAfterSave = true
        expectStorageFailure { session.dispatch(request) }
        val committed = fixture.repository.value
        assertTrue(fixture.repository.commits.getValue(request.id).request.demoMode)

        preferences.setDemoModeEnabled(false)
        fixture.repository.failAfterSave = false
        val result = session.dispatch(request)
        assertTrue(result is EngineResult.Applied)
        assertEquals(committed, fixture.repository.value)
        assertEquals(1, fixture.repository.commits.size)
        assertEquals(1, fixture.repository.value.ownedItems.size)
        assertTrue(fixture.repository.receivedRequests.all { it.demoMode })

        val next = EngineRequest("next-ordinary", committed.engine!!.revision, EngineCommand.Feed("free"))
        assertTrue(session.dispatch(next) is EngineResult.Applied)
        assertFalse(fixture.repository.commits.getValue(next.id).request.demoMode)
        assertEquals(0, fixture.repository.value.engine!!.energy)
    }

    @Test fun uncommittedRetryRetainsOrdinaryRulesWhenPreferenceIsEnabled() = runTest {
        val fixture = Fixture(energy = 1)
        val preferences = MutablePreferences(false)
        val session = fixture.session(preferences)
        val request = EngineRequest("uncertain-ordinary", 0, EngineCommand.CompleteEvent("occurrence", "work-choice"))
        fixture.repository.failBeforeSave = true
        expectStorageFailure { session.dispatch(request) }
        assertTrue(fixture.repository.commits.isEmpty())
        assertEquals(1, fixture.repository.value.engine!!.energy)

        preferences.setDemoModeEnabled(true)
        fixture.repository.failBeforeSave = false
        assertTrue(session.dispatch(request) is EngineResult.Applied)
        assertFalse(fixture.repository.commits.getValue(request.id).request.demoMode)
        assertEquals(0, fixture.repository.value.engine!!.energy)
        assertTrue(fixture.repository.receivedRequests.all { !it.demoMode })
    }

    private suspend fun expectStorageFailure(block: suspend () -> Unit) {
        try { block(); fail("Expected an uncertain storage reply") } catch (_: IOException) { }
    }

    @Serializable
    private data class LegacyRequest(val id: String, val expectedRevision: Long?, val command: EngineCommand,
        val context: DecisionContext? = null)

    private class MutablePreferences(enabled: Boolean) : DemoPreferencesRepository {
        private val state = MutableStateFlow(DemoPreferences(enabled))
        override fun observe() = state
        override suspend fun read() = state.value
        override suspend fun setDemoModeEnabled(enabled: Boolean) { state.value = DemoPreferences(enabled) }
    }

    private class Fixture(energy: Int = 5, coins: Long = 20, activeEvent: Boolean = true) {
        val catalog = GameCatalog(
            content = StoryContent(
                chapters = listOf(ChapterDefinition("chapter", "Chapter", "goal")),
                days = listOf(GameDayDefinition("day", "chapter", 1)),
                goals = listOf(GoalDefinition("goal", "Goal", "")),
                items = listOf(ItemDefinition("part", "Part", "", priceCoins = 50)),
                requiredItems = listOf(GoalRequiredItem("goal", "part")),
                events = listOf(EventDefinition("work", EventType.STORY, "Work", "", null, null, null, 0, null, null)),
                choices = listOf(EventChoiceDefinition("work-choice", "work", 0, "Complete", 0, null, null, GoalImpact.NEUTRAL)),
            ),
            policies = mapOf("work" to EventPolicy(1)), cards = emptyMap(),
            rules = EngineRules("demo-tests", 5, 50, 1),
            meals = listOf(MealDefinition("basic", 5, null), MealDefinition("free", 0, null, nextMorningEnergy = 3)),
            storyDayId = "day", introductionId = "work", deedPool = emptyList(),
            goals = listOf(GoalCampaign("goal", "work", listOf("part"))),
        )
        val repository = ReceiptRepository(GameState(
            PetState("NONE", if (energy == 0) PetVisualState.TIRED else PetVisualState.NORMAL),
            EconomyState(BudgetPlan(coins, 0, 0, 0)),
            StoryState("day", 0, "work".takeIf { activeEvent }, emptyList()),
            0, 0, emptyList(), selectedGoalId = "goal", selectedSavingItemId = "part",
            engine = EngineState("demo-tests", 0, 1, DayPhase.RUNNING, 0, energy, true, null, coins,
                if (activeEvent) listOf(EventOccurrence("occurrence", "work", EventOrigin.SCHEDULE, EventStatus.ACTIVE)) else emptyList(),
                emptyList()),
        ))
        val engine = GameEngine(repository,
            EventFactory(catalog.content, catalog.policies, catalog.meals, catalog.goals), catalog.rules)
        fun session(preferences: DemoPreferencesRepository) = GameSession(repository, object : StoryContentRepository {
            override suspend fun read() = catalog.content
            override suspend fun install(content: StoryContent) { }
        }, catalog, repository.value, preferences)
    }

    private data class Commit(val request: EngineRequest, val facts: List<AnalyticsFact>)

    /** Models atomic commit and command receipts, including both sides of an uncertain response. */
    private class ReceiptRepository(initial: GameState) : GameRepository {
        private val states = MutableStateFlow<GameState?>(initial)
        val value get() = checkNotNull(states.value)
        val commits = linkedMapOf<String, Commit>()
        val receivedRequests = mutableListOf<EngineRequest>()
        var failBeforeSave = false
        var failAfterSave = false
        override fun observe() = states
        override suspend fun read() = value
        override suspend fun initializeIfAbsent(initial: GameState) = value
        override suspend fun update(transform: (GameState) -> GameState) = transform(value).also { states.value = it }
        override suspend fun commit(request: EngineRequest, context: DecisionContext?, contentFingerprint: String?,
            facts: (GameState, GameState, String, Long) -> List<AnalyticsFact>, transform: (GameState) -> GameState): GameState {
            receivedRequests += request
            commits[request.id]?.let { previous ->
                check(previous.request == request) { "A retry changed the committed request" }
                return value
            }
            val before = value
            val after = transform(before)
            CanonicalLedger.fromTransition(before, after, request)
            val computedFacts = facts(before, after, "demo-run", commits.size.toLong() + 1)
            if (failBeforeSave) throw IOException("Save did not commit")
            states.value = after
            commits[request.id] = Commit(request, computedFacts)
            if (failAfterSave) throw IOException("Committed reply was lost")
            return after
        }
    }
}
