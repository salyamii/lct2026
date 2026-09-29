package ru.nksk.lctapp.domain.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.domain.analytics.AnalyticsFact
import ru.nksk.lctapp.domain.analytics.DecisionContext
import ru.nksk.lctapp.domain.content.*
import ru.nksk.lctapp.domain.economy.BudgetPlan
import ru.nksk.lctapp.domain.economy.EconomyState
import ru.nksk.lctapp.domain.game.GameRepository
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.history.CanonicalLedger
import ru.nksk.lctapp.domain.history.HistoryCodec
import ru.nksk.lctapp.domain.minigame.DeedGameKind
import ru.nksk.lctapp.domain.pet.PetState
import ru.nksk.lctapp.domain.pet.PetVisualState
import ru.nksk.lctapp.domain.story.StoryState

class DemoMiniGameSkipTest {
    @Test fun everyOfferedMechanicCanBeSkippedOnlyByAnExplicitDemoCommand() = runTest {
        for (kind in DeedGameKind.entries) {
            val f = Fixture(kind, deed = true)
            val before = f.repository.value
            val command = EngineCommand.SkipMiniGame("occurrence")
            assertEquals(EngineResult.Blocked(BlockReason.InvalidEventAction),
                f.engine.dispatch(EngineRequest("ordinary", 0, command)))
            assertEquals(before, f.repository.value)
            val request = EngineRequest("demo-skip", 0, command, demoMode = true)
            assertTrue(f.engine.dispatch(request) is EngineResult.Applied)
            val completed = f.repository.value
            assertEquals(before.economy.balance + 7, completed.economy.balance)
            assertEquals(5, completed.engine!!.energy)
            assertEquals(1, completed.engine!!.steps)
            assertTrue(completed.engine!!.deeds.single().completed)
            assertEquals(EventStatus.COMPLETED, completed.engine!!.events.single().status)
            assertEquals("choice", completed.story.decisions.single().choiceId)
            assertEquals(PetVisualState.HAPPY, completed.pet.visualState)
            assertEquals(7L, completed.engine!!.journal.single().moneyDelta)
            assertTrue(f.repository.facts.isEmpty())
            assertFalse(HistoryCodec.encodeRequest(request).contains("score"))
            assertFalse(HistoryCodec.encodeRequest(request).contains("attempts"))
            assertTrue(f.engine.dispatch(request) is EngineResult.Applied)
            assertEquals(completed, f.repository.value)
            assertEquals(1, f.repository.commits.size)
        }
    }

    @Test fun storySkipAppliesChosenEffectsAndPreservesAdmissionChecksWithoutAScore() = runTest {
        val f = Fixture(DeedGameKind.SEQUENCE, deed = false)
        val before = f.repository.value
        val command = EngineCommand.SkipMiniGame("occurrence", "choice")
        val request = EngineRequest("story-skip", 0, command, demoMode = true)
        assertEquals(EngineResult.Blocked(BlockReason.InvalidEventAction), f.engine.dispatch(
            request.copy(id = "wrong-choice", command = command.copy(choiceId = "other"))))
        assertEquals(EngineResult.Blocked(BlockReason.InvalidEventAction), f.engine.dispatch(
            request.copy(id = "wrong-origin", command = command.copy(choiceId = null))))
        assertEquals(before, f.repository.value)
        assertTrue(f.engine.dispatch(request) is EngineResult.Applied)
        val completed = f.repository.value
        assertEquals(before.economy, completed.economy)
        assertEquals(listOf("tool"), completed.ownedItems.map { it.itemId })
        assertEquals("choice", completed.story.decisions.single().choiceId)
        assertEquals(EventStatus.COMPLETED, completed.engine!!.events.single().status)
        assertEquals(5, completed.engine!!.energy)
        assertTrue(f.repository.facts.isEmpty())

        val freeItem = Fixture(DeedGameKind.SEQUENCE, deed = false, coins = 0)
        assertTrue(freeItem.engine.dispatch(request) is EngineResult.Applied)
        assertEquals(listOf("tool"), freeItem.repository.value.ownedItems.map { it.itemId })
        val blocked = Fixture(DeedGameKind.SEQUENCE, deed = false, coins = 0, grantsItem = false)
        assertEquals(EngineResult.Blocked(BlockReason.InsufficientMoney(3)), blocked.engine.dispatch(request))
        assertTrue(blocked.repository.value.ownedItems.isEmpty())
        assertTrue(blocked.repository.value.story.decisions.isEmpty())
        val unavailable = Fixture(null, deed = false)
        assertEquals(EngineResult.Blocked(BlockReason.InvalidEventAction), unavailable.engine.dispatch(request))
    }

    @Test fun aSkippedBoardStillRequiresTheLatestActiveOccurrenceAndRevision() = runTest {
        val f = Fixture(DeedGameKind.MEMORY, deed = true)
        val before = f.repository.value
        assertEquals(EngineResult.Blocked(BlockReason.StaleRevision), f.engine.dispatch(
            EngineRequest("stale", 99, EngineCommand.SkipMiniGame("occurrence"), demoMode = true)))
        assertEquals(EngineResult.Blocked(BlockReason.InvalidEventAction), f.engine.dispatch(
            EngineRequest("other", 0, EngineCommand.SkipMiniGame("other-occurrence"), demoMode = true)))
        assertEquals(before, f.repository.value)
    }

    private class Fixture(kind: DeedGameKind?, deed: Boolean, coins: Long = 20, grantsItem: Boolean = true) {
        private val content = StoryContent(
            events = listOf(EventDefinition("event", if (deed) EventType.EARNING else EventType.STORY,
                "Activity", "", null, null, null, 0, null, null)),
            choices = listOf(EventChoiceDefinition("choice", "event", 0, "Finish", if (deed) 7 else -3,
                null, PetVisualState.HAPPY, GoalImpact.NEUTRAL)),
            items = listOf(ItemDefinition("tool", "Tool", "")),
            choiceItemEffects = if (deed || !grantsItem) emptyList() else listOf(ChoiceItemEffect("effect", "choice", 0, "tool", ItemOperation.ADD)),
        )
        val repository = Repository(GameState(
            PetState("NONE", PetVisualState.TIRED), EconomyState(BudgetPlan(coins, 0, 0, 0)),
            StoryState(null, null, "event", emptyList()), 0, 0, emptyList(),
            engine = EngineState("skip-rules", 0, 1, DayPhase.RUNNING, 0, 0, true, null, coins,
                listOf(EventOccurrence("occurrence", "event", if (deed) EventOrigin.DEED else EventOrigin.SCHEDULE,
                    EventStatus.ACTIVE, "offer".takeIf { deed })),
                if (deed) listOf(DeedOffer("offer", "event", 3)) else emptyList()),
        ))
        val engine = GameEngine(repository, EventFactory(content, mapOf("event" to EventPolicy(
            energyCost = 1, deedGameKind = kind.takeIf { deed },
            choiceGameKinds = if (!deed && kind != null) mapOf("choice" to kind) else emptyMap(),
        )), emptyList()), EngineRules("skip-rules", 5, 50, 1))
    }

    private class Repository(initial: GameState) : GameRepository {
        private val state = MutableStateFlow(initial)
        val value get() = state.value
        val commits = linkedMapOf<String, EngineRequest>()
        val facts = mutableListOf<AnalyticsFact>()
        override fun observe() = state
        override suspend fun read() = value
        override suspend fun initializeIfAbsent(initial: GameState) = value
        override suspend fun update(transform: (GameState) -> GameState) = transform(value).also { state.value = it }
        override suspend fun commit(request: EngineRequest, context: DecisionContext?, contentFingerprint: String?,
            facts: (GameState, GameState, String, Long) -> List<AnalyticsFact>, transform: (GameState) -> GameState): GameState {
            commits[request.id]?.let { check(it == request); return value }
            val before = value
            val after = transform(before)
            CanonicalLedger.fromTransition(before, after, request)
            this.facts += facts(before, after, "run", commits.size + 1L)
            commits[request.id] = request
            state.value = after
            return after
        }
    }
}
