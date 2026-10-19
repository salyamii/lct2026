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
    @Test fun disabledContentGuardPublishesDiagnosticOnceWithoutIntentHistoryOrWrites() = runTest {
        val fixture = Fixture()
        fixture.repository.update { state -> state.copy(engine = state.engine!!.copy(
            phase = DayPhase.READY_TO_END, ateToday = true,
            events = listOf(EventOccurrence("old-occurrence", "retired-event", EventOrigin.SCHEDULE, EventStatus.COMPLETED)),
        )) }
        val before = fixture.repository.value
        val failures = mutableListOf<RejectedGameCommand>()
        val rejectedCommands = mutableListOf<RejectedGameCommand>()
        val appliedCommands = mutableListOf<AppliedGameCommand>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            fixture.session.contentFailures.collect { failures += it }
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            fixture.session.rejectedCommands.collect { rejectedCommands += it }
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            fixture.session.appliedCommands.collect { appliedCommands += it }
        }

        repeat(2) {
            assertEquals(BlockReason.InvalidContent("Unknown event: retired-event"),
                fixture.session.engine.blockReason(before, EngineCommand.FinishDay))
        }
        val failure = failures.single()
        assertEquals(RejectionOrigin.GUARD_CHECK, failure.origin)
        assertEquals(EngineCommand.FinishDay, failure.command)
        assertTrue(checkNotNull(failure.failure).frames.first().contains("EventFactory"))

        val hungry = before.copy(engine = before.engine!!.copy(ateToday = false, events = emptyList()))
        assertEquals(BlockReason.MustEat, fixture.session.engine.blockReason(hungry, EngineCommand.FinishDay))
        assertEquals(listOf(failure), failures)
        assertTrue(rejectedCommands.isEmpty())
        assertTrue(appliedCommands.isEmpty())
        assertEquals(before, fixture.repository.value)
        assertTrue(fixture.repository.commits.isEmpty())
        assertTrue(fixture.repository.rejections.isEmpty())

        val recreatedObserver = mutableListOf<RejectedGameCommand>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            fixture.session.contentFailures.collect { recreatedObserver += it }
        }
        assertEquals(listOf(failure), recreatedObserver)
    }

    @Test fun contentFailuresInAnalyticalPreviewsStaySilent() = runTest {
        val fixture = Fixture()
        val before = fixture.repository.value
        val incompatible = before.copy(engine = before.engine!!.copy(rulesId = "other-rules"))
        val expected = EngineResult.Blocked(BlockReason.InvalidContent("Saved engine uses a different rules version"))
        assertEquals(expected, fixture.session.engine.preview(incompatible, EngineCommand.FinishDay))
        assertEquals(expected, fixture.session.engine.previewEventChoice(incompatible, "event", "choice"))
        assertEquals(expected, fixture.session.engine.previewEventChoice(incompatible, "event", "choice", demoMode = true))
        assertTrue(fixture.session.contentFailures.replayCache.isEmpty())
        assertTrue(fixture.session.rejectedCommands.replayCache.isEmpty())
        assertTrue(fixture.session.appliedCommands.replayCache.isEmpty())
        assertTrue(fixture.repository.commits.isEmpty())
        assertTrue(fixture.repository.rejections.isEmpty())
        assertEquals(before, fixture.repository.value)
    }

    @Test fun rejectedContentCapturesActualCommandAndCodeFrameWithoutChangingTheSave() = runTest {
        val fixture = Fixture()
        fixture.repository.update { state -> state.copy(engine = state.engine!!.copy(
            phase = DayPhase.READY_TO_END, ateToday = true,
            events = listOf(EventOccurrence("old-occurrence", "retired-event", EventOrigin.SCHEDULE, EventStatus.COMPLETED)),
        )) }
        val before = fixture.repository.value
        val notifications = mutableListOf<RejectedGameCommand>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            fixture.session.rejectedCommands.collect { notifications += it }
        }

        assertEquals(BlockReason.InvalidContent("Unknown event: retired-event"),
            fixture.session.engine.blockReason(before, EngineCommand.FinishDay))
        assertTrue("Read-only admission must not report an attempted command", notifications.isEmpty())
        val result = fixture.session.dispatch(EngineRequest("finish", before.engine!!.revision, EngineCommand.FinishDay))

        assertEquals(EngineResult.Blocked(BlockReason.InvalidContent("Unknown event: retired-event")), result)
        val rejected = notifications.single()
        assertEquals(EngineCommand.FinishDay, rejected.command)
        assertEquals(RejectionOrigin.COMMAND, rejected.origin)
        assertEquals(listOf(rejected), fixture.session.contentFailures.replayCache)
        val failure = checkNotNull(rejected.failure)
        assertEquals(IllegalArgumentException::class.java.name, failure.exception)
        assertTrue(failure.frames.first().contains("EventFactory"))
        assertTrue(failure.frames.none { it.contains("retired-event") || it.contains("old-occurrence") })
        assertEquals(before, fixture.repository.value)
        assertTrue(fixture.repository.commits.isEmpty())
        val recreatedObserver = mutableListOf<RejectedGameCommand>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            fixture.session.rejectedCommands.collect { recreatedObserver += it }
        }
        assertEquals(listOf(rejected), recreatedObserver)
    }

    @Test fun diagnosticListenerCannotTurnARejectionIntoADifferentFailure() = runTest {
        val fixture = Fixture()
        val engine = GameEngine(fixture.repository,
            EventFactory(fixture.catalog.content, fixture.catalog.policies, fixture.catalog.meals),
            fixture.catalog.rules, onRejected = { throw IllegalStateException("diagnostic observer unavailable") },
            onGuardFailure = { throw IllegalStateException("diagnostic observer unavailable") })
        val before = fixture.repository.value
        assertEquals(BlockReason.InvalidContent("Saved engine uses a different rules version"),
            engine.blockReason(before.copy(engine = before.engine!!.copy(rulesId = "other-rules")), EngineCommand.FinishDay))
        assertEquals(EngineResult.Blocked(BlockReason.StaleRevision),
            engine.dispatch(fixture.feedRequest().copy(expectedRevision = 10)))
        assertEquals(before, fixture.repository.value)
        assertTrue(fixture.repository.commits.isEmpty())
    }

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
        val rejections = mutableListOf<EngineRequest>()
        var failBeforeSave = false
        var failAfterSave = false
        override fun observe() = states
        override suspend fun read() = value
        override suspend fun initializeIfAbsent(initial: GameState) = value
        override suspend fun update(transform: (GameState) -> GameState) = transform(value).also { states.value = it }
        override suspend fun recordRejected(request: EngineRequest, reasonName: String, contentFingerprint: String?) {
            rejections += request
        }
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
