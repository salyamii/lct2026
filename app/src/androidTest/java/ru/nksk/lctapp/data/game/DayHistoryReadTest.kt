package ru.nksk.lctapp.data.game

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.nksk.lctapp.app.createInitialGameState
import ru.nksk.lctapp.data.game.local.GameDatabase
import ru.nksk.lctapp.data.game.local.GameAuditEntity
import ru.nksk.lctapp.domain.analytics.AnalyticsFact
import ru.nksk.lctapp.domain.analytics.FactDetail
import ru.nksk.lctapp.domain.engine.*
import ru.nksk.lctapp.domain.economy.BudgetPlan
import ru.nksk.lctapp.domain.economy.BudgetRevisionReason
import ru.nksk.lctapp.domain.finance.BudgetPlanRevision
import ru.nksk.lctapp.domain.history.*

/** Compile only unless device testing is explicitly requested. Exercises the bundled JSON SQL path. */
@RunWith(AndroidJUnit4::class)
class DayHistoryReadTest {
    private lateinit var database: GameDatabase
    private lateinit var games: RoomGameRepository

    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder<GameDatabase>(ApplicationProvider.getApplicationContext<Context>())
            .setDriver(BundledSQLiteDriver()).build()
        games = RoomGameRepository(database)
    }

    @After fun close() { database.close() }

    @Test fun receiptAndHeadReadsDoNotDecodeUnrelatedCheckpoints() = runBlocking {
        assertNull(games.readSnapshotHead())
        games.initializeIfAbsent(createInitialGameState())
        rename("first", "Первый")
        val first = checkNotNull(games.readLatestCommand())
        rename("second", "Второй")
        val second = checkNotNull(games.readLatestCommand())
        // Invalid indexed metadata in an unrelated document would fail a whole-history decode.
        val unrelated = checkNotNull(database.gameHistoryDao().find(first.id))
        database.gameHistoryDao().insert(unrelated.copy(id = "unrelated-technical", type = "TECHNICAL_UPDATE",
            sequence = second.sequence + 1))
        val world = checkNotNull(games.read())
        assertEquals(first, games.readCommandReceipt("first"))
        assertEquals(second, games.readLatestCommand())
        assertNull(games.readCommandReceipt("missing"))
        assertEquals(GameSnapshotHead(first.runId, world, second.sequence + 1), games.readSnapshotHead())
        assertEquals(world, games.read())
    }

    @Test fun importedOpaqueReceiptIdsRemainReadableAndSelectedCorruptionStillFails() = runBlocking {
        games.initializeIfAbsent(createInitialGameState())
        rename("original", "Друг")
        val original = checkNotNull(games.readLatestCommand())
        val imported = original.copy(id = "legacy-opaque-receipt", sequence = original.sequence + 1,
            request = checkNotNull(original.request).copy(id = "imported-request"))
        database.gameHistoryDao().insert(imported.row())
        assertEquals(imported, games.readCommandReceipt("imported-request"))
        val world = games.read()
        database.gameHistoryDao().insert(imported.row().copy(id = "command:${original.runId}:corrupt",
            sequence = imported.sequence + 1))
        var failed = false
        try { games.readCommandReceipt("corrupt") } catch (_: IllegalStateException) { failed = true }
        assertTrue(failed)
        assertEquals(world, games.read())
    }

    @Test fun expenseReadIncludesCompletedRecoveryFactsButNoUnrelatedCheckpoint() = runBlocking {
        games.initializeIfAbsent(createInitialGameState())
        rename("unrelated", "Друг")
        games.recordFacts(listOf(AnalyticsFact("expense", "current", "episode", "expense-action", 0,
            FactDetail.UnexpectedExpense("operation", 7, false))))
        games.recordFacts(listOf(AnalyticsFact("view", "current", "episode", "view-action", 0,
            FactDetail.Interaction("shown"))))
        games.recordFacts(listOf(AnalyticsFact("recovery", "current", "episode", "recovery-action", 0,
            FactDetail.RecoveryAction("operation", "revise", true))))
        val full = games.readHistory()
        assertEquals(full.filter { entry -> entry.facts.any {
            it.detail is FactDetail.UnexpectedExpense || it.detail is FactDetail.RecoveryAction
        } }, games.readExpenseRecoveryHistory())
        assertEquals(full, games.readHistory())
    }

    @Test fun planReadKeepsTheEntireVerifiedTailForCanonicalAndLegacyPlanIds() = runBlocking {
        games.initializeIfAbsent(createInitialGameState())
        rename("before-plan", "Друг")
        val first = confirmPlan("confirm-one", "confirm-one:plan")
        games.recordFacts(listOf(AnalyticsFact("shown", "current", "episode", "show-plan", 0,
            FactDetail.Interaction("shown"))))
        games.recordRejected(EngineRequest("blocked", null, EngineCommand.OpenNextEvent), "test")
        games.update { it.copy(pet = it.pet.copy(name = "После плана")) }
        val legacy = confirmPlan("confirm-two", "legacy-plan-id")
        val full = games.readHistory()
        val firstSequence = full.single { it.request?.id == "confirm-one" }.sequence
        val legacySequence = full.single { it.request?.id == "confirm-two" }.sequence
        assertEquals(full.filter { it.sequence >= firstSequence }, games.readBudgetPlanHistory(first.id))
        assertEquals(full.filter { it.sequence >= legacySequence }, games.readBudgetPlanHistory(legacy.id))
        assertTrue(games.readBudgetPlanHistory("missing-plan").isEmpty())
        assertEquals(full, games.readHistory())
    }

    @Test fun indexedFactLookupReturnsOnlyRequestedFactsAndCurrentRunAndSequence() = runBlocking {
        games.initializeIfAbsent(createInitialGameState())
        val first = AnalyticsFact("first", "current", "episode", "shown-first", 0, FactDetail.Interaction("first"))
        val other = AnalyticsFact("other", "current", "episode", "shown-other", 0, FactDetail.Interaction("other"))
        games.recordFacts(listOf(first, other))
        games.recordFacts(listOf(AnalyticsFact("last", "current", "episode", "shown-last", 0, FactDetail.Interaction("last"))))
        val full = games.readHistory()
        val before = games.read()
        val found = checkNotNull(games.readFacts(setOf("first", "missing", "last")))
        assertEquals(full.last().runId, found.runId)
        assertEquals(full.last().sequence, found.sequence)
        assertEquals(full.flatMap { it.facts }.filter { it.eventId in setOf("first", "last") }, found.facts)
        val empty = checkNotNull(games.readFacts(emptySet()))
        assertTrue(empty.facts.isEmpty())
        assertEquals(found.runId, empty.runId)
        assertEquals(found.sequence, empty.sequence)
        assertEquals(before, games.read())
        assertEquals(full, games.readHistory())
    }

    @Test fun readsOnlyCommandsOfRequestedDayAndChecksLatestCheckpointAfterFacts() = runBlocking {
        val initial = createInitialGameState()
        games.initializeIfAbsent(initial.copy(engine = EngineState("test", 0, 1, DayPhase.FINISHED,
            0, 4, true, null, initial.economy.balance, emptyList(), emptyList())))
        rename("day-one", "Первый")
        games.update { it.copy(engine = checkNotNull(it.engine).copy(day = 2)) }
        rename("day-two", "Второй")
        games.recordFacts(listOf(AnalyticsFact("shown", "current", "episode", "presentation", 0,
            FactDetail.Interaction("shown"))))
        val full = games.readHistory()
        assertEquals(full.last().id, games.latestHistoryId())
        val world = games.read()
        assertEquals(full.filter { it.type == AuditType.COMMAND && it.before?.engine?.day == 1 }, games.readDayHistory(1))
        assertEquals(full.filter { it.type == AuditType.COMMAND && it.before?.engine?.day == 2 }, games.readDayHistory(2))
        assertTrue(checkNotNull(games.readDayHistory(3)).isEmpty())
        assertEquals(world, games.read())
        assertEquals(full, games.readHistory())
    }

    @Test fun selectedCorruptPayloadPropagatesAndDoesNotResetTheWorld() = runBlocking {
        val initial = createInitialGameState()
        games.initializeIfAbsent(initial.copy(engine = EngineState("test", 0, 1, DayPhase.FINISHED,
            0, 4, true, null, initial.economy.balance, emptyList(), emptyList())))
        rename("valid", "Лис")
        val world = games.read()
        val last = checkNotNull(database.gameHistoryDao().latestCheckpoint())
        // Keep a valid JSON document but break the indexed identity; strict decode must still fail.
        database.gameHistoryDao().insert(last.copy(id = "corrupt-copy", sequence = last.sequence + 1))
        var failed = false
        try { games.readDayHistory(1) } catch (_: IllegalStateException) { failed = true }
        assertTrue(failed)
        assertEquals(world, games.read())
        assertNotNull(database.gameHistoryDao().find("corrupt-copy"))
    }

    private suspend fun rename(id: String, name: String) {
        val current = checkNotNull(games.read())
        games.commit(EngineRequest(id, current.engine?.revision, EngineCommand.RenamePet(name, current.pet.name))) {
            it.copy(pet = it.pet.copy(name = name))
        }
    }

    private suspend fun confirmPlan(requestId: String, planId: String): BudgetPlanRevision {
        val current = checkNotNull(games.read())
        val allocation = BudgetPlan(35, 20, 20, current.economy.availableBalance - 75)
        val plan = BudgetPlanRevision(planId, null, current.financial.plans.size + 1, 1,
            current.economy.availableBalance, allocation, BudgetRevisionReason.INITIAL,
            previousId = current.financial.plans.lastOrNull()?.id)
        games.commit(EngineRequest(requestId, current.engine?.revision, EngineCommand.ConfirmBudget("draft", 0))) {
            it.copy(economy = it.economy.copy(plan = allocation, planning = null, unallocated = 0),
                financial = it.financial.copy(plans = it.financial.plans + plan))
        }
        return plan
    }

    private fun AuditEntry.row() = GameAuditEntity(id, runId, sequence, type.name, formatVersion, HistoryCodec.encode(this))
}
