package ru.nksk.lctapp.feature.tasks.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import ru.nksk.lctapp.app.createInitialGameState
import ru.nksk.lctapp.data.game.content.bundledGameCatalog
import ru.nksk.lctapp.domain.analytics.AnalyticsFact
import ru.nksk.lctapp.domain.analytics.DecisionContext
import ru.nksk.lctapp.domain.content.StoryContent
import ru.nksk.lctapp.domain.content.StoryContentRepository
import ru.nksk.lctapp.domain.demo.DemoPreferences
import ru.nksk.lctapp.domain.demo.DemoPreferencesRepository
import ru.nksk.lctapp.domain.economy.BudgetPlan
import ru.nksk.lctapp.domain.economy.EconomyState
import ru.nksk.lctapp.domain.engine.*
import ru.nksk.lctapp.domain.game.GameRepository
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.history.AuditEntry
import ru.nksk.lctapp.domain.history.AuditType
import ru.nksk.lctapp.domain.history.CanonicalLedger
import ru.nksk.lctapp.domain.minigame.DeedGameKind
import ru.nksk.lctapp.domain.minigame.DeedGameScore
import ru.nksk.lctapp.domain.minigame.chapterone.BoatJourney
import ru.nksk.lctapp.domain.minigame.chapterone.BoatPhase
import ru.nksk.lctapp.domain.minigame.chapterone.BoatPlan
import ru.nksk.lctapp.feature.tasks.navigation.ChapterRepairPayment

@OptIn(ExperimentalCoroutinesApi::class)
class DeedBoatPaymentViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { store.clear(); Dispatchers.resetMain() }

    @Test fun paymentUsesHostMoneyWithoutCompletingStoryOrLeavingBoard() = runTest(dispatcher) {
        val f = fixture()
        runCurrent()
        val before = f.repo.read()
        val exits = mutableListOf<String?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { f.model.exit.collect { exits += it } }

        assertEquals(ChapterRepairPayment.PAID, f.model.payBoatRepair("trip:rock:0", 0))
        runCurrent()
        val after = f.repo.read()
        assertEquals(before.economy.availableBalance - 2, after.economy.availableBalance)
        assertEquals(before.story, after.story)
        assertEquals(before.engine!!.events, after.engine!!.events)
        assertEquals(before.engine!!.energy, after.engine!!.energy)
        assertEquals(before.engine!!.steps, after.engine!!.steps)
        assertEquals(EventStatus.ACTIVE, after.engine!!.currentEvent!!.status)
        assertTrue(exits.isEmpty())
        assertTrue(f.model.uiState.value.presentation!!.canPlay)
        assertEquals(1, f.repo.committed.size)
        assertTrue(f.repo.committed.single().command is EngineCommand.PayBoatRepair)
    }

    @Test fun lostCommittedReplyRestoresExactRequestAndDoesNotChargeTwice() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val f = fixture(saved = saved)
        runCurrent()
        val before = f.repo.read()
        f.repo.afterWrite = { throw IOException("Committed reply lost") }
        assertEquals(ChapterRepairPayment.FAILED, f.model.payBoatRepair("trip:rock:0", 0))
        val original = f.repo.attempted.single()
        val committed = f.repo.read()
        assertEquals(before.economy.availableBalance - 2, committed.economy.availableBalance)

        val restored = recreate(f, copyOf(saved))
        runCurrent()
        f.repo.afterWrite = {}
        assertEquals(ChapterRepairPayment.PAID, restored.payBoatRepair("trip:rock:0", 0))
        runCurrent()
        assertEquals(listOf(original, original), f.repo.attempted)
        assertEquals(committed, f.repo.read())
        assertEquals(1, f.repo.committed.size)
        assertEquals(EventStatus.ACTIVE, f.repo.read().engine!!.currentEvent!!.status)
    }

    @Test fun failedWriteRetainsOriginalRevisionInsteadOfSilentlyUsingNewerWorldAfterRecreation() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val f = fixture(saved = saved)
        runCurrent()
        f.repo.beforeWriteFailure = IOException("Write outcome unavailable")
        assertEquals(ChapterRepairPayment.FAILED, f.model.payBoatRepair("trip:rock:1", 1))
        val original = f.repo.attempted.single()
        f.repo.externalUpdate { it.copy(engine = it.engine!!.copy(revision = it.engine!!.revision + 1)) }
        val newer = f.repo.read()
        f.repo.beforeWriteFailure = null
        val restored = recreate(f, copyOf(saved))
        runCurrent()

        // The retry must reach the real stale-revision guard with the original request.
        assertEquals(ChapterRepairPayment.FAILED, restored.payBoatRepair("trip:rock:1", 1))
        assertEquals(listOf(original, original), f.repo.attempted)
        assertEquals(newer, f.repo.read())
        assertTrue(f.repo.committed.isEmpty())

        // Only the next explicit press creates a new, revalidated payment intent.
        assertEquals(ChapterRepairPayment.PAID, restored.payBoatRepair("trip:rock:1", 1))
        val fresh = f.repo.attempted.last()
        assertNotEquals(original.id, fresh.id)
        assertEquals(newer.engine!!.revision, fresh.expectedRevision)
        assertEquals(original.command, fresh.command)
        assertEquals(1, f.repo.committed.size)
    }

    @Test fun lifecycleCancellationAfterCommitKeepsTheSameRepairForRecreation() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val f = fixture(saved = saved)
        runCurrent()
        val held = CompletableDeferred<Unit>()
        f.repo.afterWrite = { held.await() }
        val payment = async { f.model.payBoatRepair("trip:rock:2", 2) }
        runCurrent()
        val original = f.repo.attempted.single()
        val committed = f.repo.read()
        payment.cancel()
        runCurrent()
        assertTrue(payment.isCancelled)
        val restored = recreate(f, copyOf(saved))
        runCurrent()
        f.repo.afterWrite = {}
        assertEquals(ChapterRepairPayment.PAID, restored.payBoatRepair("trip:rock:2", 2))
        assertEquals(listOf(original, original), f.repo.attempted)
        assertEquals(committed, f.repo.read())
        assertEquals(1, f.repo.committed.size)
    }

    @Test fun definiteInsufficientMoneyReleasesOldIntentForExplicitNextAttempt() = runTest(dispatcher) {
        val f = fixture(plan = BudgetPlan(35, 0, 0, 0))
        runCurrent()
        assertEquals(0, f.model.uiState.value.repairCoins)
        assertEquals(ChapterRepairPayment.NOT_ENOUGH_COINS, f.model.payBoatRepair("trip:rock:0", 0))
        val rejected = f.repo.attempted.single()
        assertTrue(f.repo.committed.isEmpty())
        f.repo.externalUpdate { it.copy(economy = EconomyState(BudgetPlan(35, 0, 0, 5)),
            engine = it.engine!!.copy(revision = it.engine!!.revision + 1)) }
        runCurrent()
        val beforeRetry = f.repo.read()
        assertEquals(ChapterRepairPayment.PAID, f.model.payBoatRepair("trip:rock:0", 0))
        val replacement = f.repo.attempted.last()
        assertNotEquals(rejected.id, replacement.id)
        assertEquals(beforeRetry.engine!!.revision, replacement.expectedRevision)
        assertEquals(rejected.command, replacement.command)
        assertEquals(38L, f.repo.read().economy.availableBalance)
        assertEquals(1, f.repo.committed.size)
    }

    @Test fun pendingAndUnknownPaymentBlockCompletionExitAndReplacementCollision() = runTest(dispatcher) {
        val f = fixture()
        runCurrent()
        val replies = CompletableDeferred<Unit>()
        f.repo.afterWrite = { replies.await(); throw IOException("Reply lost") }
        val exits = mutableListOf<String?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { f.model.exit.collect { exits += it } }
        val payment = async { f.model.payBoatRepair("trip:rock:0", 0) }
        runCurrent()
        assertFalse(f.model.uiState.value.presentation!!.canPlay)
        f.model.finishChapterGame(arrived())
        f.model.leave()
        assertEquals(ChapterRepairPayment.FAILED, f.model.payBoatRepair("trip:rock:1", 1))
        runCurrent()
        assertTrue(exits.isEmpty())
        assertEquals(1, f.repo.attempted.size)

        replies.complete(Unit)
        runCurrent()
        assertEquals(ChapterRepairPayment.FAILED, payment.await())
        val uncertain = f.repo.read()
        f.model.finishChapterGame(arrived())
        f.model.leave()
        assertEquals(ChapterRepairPayment.FAILED, f.model.payBoatRepair("trip:rock:1", 1))
        runCurrent()
        assertEquals(uncertain, f.repo.read())
        assertTrue(exits.isEmpty())
        assertEquals(1, f.repo.attempted.size)

        f.repo.afterWrite = {}
        assertEquals(ChapterRepairPayment.PAID, f.model.payBoatRepair("trip:rock:0", 0))
        f.model.leave()
        runCurrent()
        assertEquals(EventStatus.PAUSED, f.repo.read().engine!!.events.single { it.id == f.id }.status)
        assertEquals(1, exits.size)
    }

    @Test fun recreationKeepsUnconfirmedPaymentBlockingCompletionAndExit() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val f = fixture(saved = saved)
        runCurrent()
        f.repo.afterWrite = { throw IOException("Lost reply") }
        assertEquals(ChapterRepairPayment.FAILED, f.model.payBoatRepair("trip:rock:0", 0))
        val restored = recreate(f, copyOf(saved))
        runCurrent()
        val exits = mutableListOf<String?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { restored.exit.collect { exits += it } }
        val before = f.repo.read()
        restored.finishChapterGame(arrived())
        restored.leave()
        runCurrent()
        assertEquals(before, f.repo.read())
        assertEquals(1, f.repo.attempted.size)
        assertTrue(exits.isEmpty())
    }

    @Test fun demonstrationSkipCannotBypassAnUnconfirmedPayment() = runTest(dispatcher) {
        val f = fixture(demo = true)
        runCurrent()
        assertTrue(f.model.uiState.value.canSkipGame)
        val held = CompletableDeferred<Unit>()
        f.repo.afterWrite = { held.await(); throw IOException("Lost reply") }
        val payment = async { f.model.payBoatRepair("trip:rock:0", 0) }
        runCurrent()
        assertFalse(f.model.uiState.value.canSkipGame)
        f.model.skipGame()
        runCurrent()
        assertEquals(1, f.repo.attempted.size)
        held.complete(Unit)
        runCurrent()
        assertEquals(ChapterRepairPayment.FAILED, payment.await())
        assertFalse(f.model.uiState.value.canSkipGame)
        f.model.skipGame()
        runCurrent()
        assertEquals(1, f.repo.attempted.size)
        assertEquals(EventStatus.ACTIVE, f.repo.read().engine!!.currentEvent!!.status)
    }

    private fun arrived() = checkNotNull(DeedGameScore.fromBoat(
        BoatJourney(phase = BoatPhase.ARRIVED, plan = BoatPlan.RESERVE, distance = 1f)))

    private fun copyOf(saved: SavedStateHandle) = SavedStateHandle(
        saved.keys().associateWith { key -> saved.get<Any?>(key) })

    private fun recreate(f: Fixture, saved: SavedStateHandle): DeedGameViewModel =
        DeedGameViewModel(f.session, saved).also { store.put("game", it); it.load(f.id, f.choiceId) }

    private suspend fun fixture(
        saved: SavedStateHandle = SavedStateHandle(),
        plan: BudgetPlan = BudgetPlan(35, 20, 0, 25),
        demo: Boolean = false,
    ): Fixture {
        val source = bundledGameCatalog()
        val choice = source.content.choices.single { it.eventId == source.introductionId }.id
        val catalog = source.copy(policies = source.policies + (source.introductionId to
            source.policies.getValue(source.introductionId).copy(choiceGameKinds = mapOf(choice to DeedGameKind.BOAT))))
        val initial = createInitialGameState().copy(economy = EconomyState(plan))
        val repo = BoatHostRepository(initial)
        val content = object : StoryContentRepository {
            override suspend fun read() = catalog.content
            override suspend fun install(content: StoryContent) = Unit
        }
        val preferences = object : DemoPreferencesRepository {
            private val state = MutableStateFlow(DemoPreferences(demo))
            override fun observe() = state
            override suspend fun read() = state.value
            override suspend fun setDemoModeEnabled(enabled: Boolean) { state.value = DemoPreferences(enabled) }
        }
        val session = GameSession(repo, content, catalog, initial, preferences)
        assertTrue(session.dispatch(EngineRequest("begin", null,
            EngineCommand.BeginDay(catalog.storyDayId, listOf(catalog.introductionId) + catalog.deedPool.take(3), openFirst = true))) is EngineResult.Applied)
        val id = repo.read().engine!!.currentEvent!!.id
        assertTrue(session.dispatch(EngineRequest("enter", repo.read().engine!!.revision,
            EngineCommand.StartStoryGame(id, choice))) is EngineResult.Applied)
        val model = DeedGameViewModel(session, saved)
        store.put("game", model)
        model.load(id, choice)
        repo.resetAttempts()
        return Fixture(repo, session, model, id, choice)
    }

    private data class Fixture(val repo: BoatHostRepository, val session: GameSession,
        val model: DeedGameViewModel, val id: String, val choiceId: String)
}

private class BoatHostRepository(initial: GameState) : GameRepository {
    private val state = MutableStateFlow(initial)
    var beforeWriteFailure: Exception? = null
    var afterWrite: suspend () -> Unit = {}
    val attempted = mutableListOf<EngineRequest>()
    val committed = mutableListOf<EngineRequest>()
    private data class Identity(val request: EngineRequest, val context: DecisionContext?, val fingerprint: String?)
    private val receipts = mutableMapOf<String, Identity>()
    private val history = mutableListOf<AuditEntry>()
    override fun observe() = state
    override suspend fun read() = state.value
    override suspend fun initializeIfAbsent(initial: GameState) = state.value
    override suspend fun update(transform: (GameState) -> GameState) = transform(state.value).also { state.value = it }
    override suspend fun readHistory() = history.toList()
    fun externalUpdate(transform: (GameState) -> GameState) { state.value = transform(state.value) }
    fun resetAttempts() { attempted.clear(); committed.clear() }

    override suspend fun commit(request: EngineRequest, context: DecisionContext?, contentFingerprint: String?,
        facts: (GameState, GameState, String, Long) -> List<AnalyticsFact>, transform: (GameState) -> GameState): GameState {
        attempted += request
        val identity = Identity(request, context, contentFingerprint)
        receipts[request.id]?.let {
            check(it == identity) { "A retry replaced the original payload" }
            return state.value
        }
        beforeWriteFailure?.let { throw it }
        val before = state.value
        val next = transform(before)
        val sequence = history.size + 1L
        val entry = AuditEntry("command:${request.id}", sequence, "run", AuditType.COMMAND,
            request, context, before, next, facts(before, next, "run", sequence),
            CanonicalLedger.fromTransition(before, next, request), contentFingerprint = contentFingerprint)
        receipts[request.id] = identity
        committed += request
        history += entry
        state.value = next
        afterWrite()
        return next
    }
}
