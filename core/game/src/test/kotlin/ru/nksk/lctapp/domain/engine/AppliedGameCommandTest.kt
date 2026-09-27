package ru.nksk.lctapp.domain.engine

import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.domain.analytics.AnalyticsFact
import ru.nksk.lctapp.domain.analytics.DecisionContext
import ru.nksk.lctapp.domain.content.ChapterDefinition
import ru.nksk.lctapp.domain.content.GameDayDefinition
import ru.nksk.lctapp.domain.content.GoalDefinition
import ru.nksk.lctapp.domain.content.StoryContent
import ru.nksk.lctapp.domain.content.StoryContentRepository
import ru.nksk.lctapp.domain.economy.BudgetPlan
import ru.nksk.lctapp.domain.economy.EconomyState
import ru.nksk.lctapp.domain.game.GameRepository
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.pet.PetState
import ru.nksk.lctapp.domain.pet.PetVisualState
import ru.nksk.lctapp.domain.story.StoryState

@OptIn(ExperimentalCoroutinesApi::class)
class AppliedGameCommandTest {
    @Test fun committedCommandPublishesItsTransactionalSnapshotsOnceAndHasNoReplay() = runTest {
        val fixture = Fixture()
        val notifications = mutableListOf<AppliedGameCommand>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            fixture.session.appliedCommands.collect { notifications += it }
        }
        val before = fixture.repository.value
        val request = fixture.feedRequest()
        val applied = fixture.session.dispatch(request) as EngineResult.Applied
        assertEquals(listOf(AppliedGameCommand(request, before, applied.state)), notifications)
        assertEquals(before.economy.balance - 5, applied.state.economy.balance)
        assertEquals(applied, fixture.session.dispatch(request))
        assertEquals(1, notifications.size)

        val late = mutableListOf<AppliedGameCommand>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            fixture.session.appliedCommands.collect { late += it }
        }
        assertTrue(late.isEmpty())
        assertTrue(fixture.session.appliedCommands.replayCache.isEmpty())
    }

    @Test fun rejectionAndFailedWriteDoNotPublishAnAppliedCommand() = runTest {
        val fixture = Fixture()
        val notifications = mutableListOf<AppliedGameCommand>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            fixture.session.appliedCommands.collect { notifications += it }
        }
        val request = fixture.feedRequest()
        assertEquals(EngineResult.Blocked(BlockReason.StaleRevision),
            fixture.session.dispatch(request.copy(expectedRevision = 10)))
        val before = fixture.repository.value
        fixture.repository.failBeforeSave = true
        try {
            fixture.session.dispatch(request)
            fail("The failed commit must propagate")
        } catch (_: IOException) { }
        assertEquals(before, fixture.repository.value)
        assertTrue(notifications.isEmpty())
        fixture.repository.failBeforeSave = false
        assertTrue(fixture.session.dispatch(request) is EngineResult.Applied)
        assertEquals(1, notifications.size)
    }

    @Test fun lostCommitResponseAndItsDeduplicatedRetryDoNotInventAPlaybackNotification() = runTest {
        val fixture = Fixture()
        val notifications = mutableListOf<AppliedGameCommand>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            fixture.session.appliedCommands.collect { notifications += it }
        }
        val request = fixture.feedRequest()
        val before = fixture.repository.value
        fixture.repository.failAfterSave = true
        try {
            fixture.session.dispatch(request)
            fail("An uncertain commit still reports the storage error")
        } catch (_: IOException) { }
        val committed = fixture.repository.value
        assertEquals(before.economy.balance - 5, committed.economy.balance)
        assertTrue(notifications.isEmpty())
        fixture.repository.failAfterSave = false
        assertEquals(EngineResult.Applied(committed), fixture.session.dispatch(request))
        assertTrue(notifications.isEmpty())
        assertEquals(1, fixture.repository.commits.size)
    }

    @Test fun previewsAndDirectReplayTransitionsNeverPublishOrSave() = runTest {
        val fixture = Fixture()
        val notifications = mutableListOf<AppliedGameCommand>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            fixture.session.appliedCommands.collect { notifications += it }
        }
        val before = fixture.repository.value
        val request = fixture.feedRequest()
        assertTrue(fixture.session.engine.preview(before, request.command) is EngineResult.Applied)
        fixture.session.engine.transition(before, request)
        assertEquals(before, fixture.repository.value)
        assertTrue(notifications.isEmpty())
        assertTrue(fixture.repository.commits.isEmpty())
    }

    @Test fun transientListenerFailureDoesNotReportASuccessfulPaymentAsFailed() = runTest {
        val fixture = Fixture()
        val engine = GameEngine(fixture.repository,
            EventFactory(fixture.catalog.content, fixture.catalog.policies, fixture.catalog.meals),
            fixture.catalog.rules, onApplied = { throw IllegalStateException("Observer unavailable") })
        val result = engine.dispatch(fixture.feedRequest())
        assertTrue(result is EngineResult.Applied)
        assertEquals(95L, fixture.repository.value.economy.balance)
        assertEquals(1, fixture.repository.commits.size)
    }

    private class Fixture {
        val catalog = GameCatalog(
            content = StoryContent(chapters = listOf(ChapterDefinition("chapter", "Chapter", "goal")),
                days = listOf(GameDayDefinition("day", "chapter", 1)),
                goals = listOf(GoalDefinition("goal", "Goal", ""))),
            policies = emptyMap(), cards = emptyMap(), rules = EngineRules("test", 5, 50, 1),
            meals = listOf(MealDefinition("basic", 5, null)),
            storyDayId = "day", introductionId = "unused", deedPool = emptyList(),
        )
        val repository = ReceiptRepository(GameState(PetState("NONE", PetVisualState.NORMAL),
            EconomyState(BudgetPlan(100, 0, 0, 0)), StoryState("day", 0, null, emptyList()),
            satiety = 0, fatigue = 0, ownedItems = emptyList(), engine = EngineState(
                "test", 0, 1, DayPhase.RUNNING, 0, 5, false, null, 100, emptyList(), emptyList())))
        val session = GameSession(repository, object : StoryContentRepository {
            override suspend fun read() = catalog.content
            override suspend fun install(content: StoryContent) { }
        }, catalog, repository.value)
        fun feedRequest() = EngineRequest("feed-once", 0, EngineCommand.Feed("basic"))
    }

    /** Models the production boundary: deduplication bypasses transform; the reply can fail after commit. */
    private class ReceiptRepository(initial: GameState) : GameRepository {
        private val states = MutableStateFlow<GameState?>(initial)
        val value get() = checkNotNull(states.value)
        val commits = linkedMapOf<String, EngineRequest>()
        var failBeforeSave = false
        var failAfterSave = false
        override fun observe() = states
        override suspend fun read() = value
        override suspend fun initializeIfAbsent(initial: GameState) = value
        override suspend fun update(transform: (GameState) -> GameState) = transform(value).also { states.value = it }
        override suspend fun commit(request: EngineRequest, context: DecisionContext?, contentFingerprint: String?,
            facts: (GameState, GameState, String, Long) -> List<AnalyticsFact>, transform: (GameState) -> GameState): GameState {
            commits[request.id]?.let { previous ->
                check(previous == request)
                return value
            }
            val next = transform(value)
            if (failBeforeSave) throw IOException("Not committed")
            states.value = next
            commits[request.id] = request
            if (failAfterSave) throw IOException("Reply lost after commit")
            return next
        }
    }
}
