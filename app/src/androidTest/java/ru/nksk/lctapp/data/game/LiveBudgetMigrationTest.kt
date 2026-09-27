package ru.nksk.lctapp.data.game

import androidx.room3.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.nksk.lctapp.app.createInitialGameState
import ru.nksk.lctapp.data.game.local.GameDatabase
import ru.nksk.lctapp.data.game.local.MIGRATION_19_20
import ru.nksk.lctapp.data.game.local.toEntity
import ru.nksk.lctapp.domain.economy.*
import ru.nksk.lctapp.domain.finance.BudgetPlanRevision
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.history.*

/** Source coverage only: connected execution requires the user's explicit authorization. */
@RunWith(AndroidJUnit4::class)
class LiveBudgetMigrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val name = "live-budget-20-${java.util.UUID.randomUUID()}.db"
    @get:Rule val schemas = MigrationTestHelper(instrumentation = instrumentation,
        databaseClass = GameDatabase::class, driver = BundledSQLiteDriver(), file = context.getDatabasePath(name))
    @After fun cleanup() { context.deleteDatabase(name) }

    @Test fun v19WithoutImmutablePlanKeepsItsOriginalInAuditBeforeExposingLiveState() = runBlocking {
        createLegacyDatabase()
        schemas.runMigrationsAndValidate(20, listOf(MIGRATION_19_20)).use { connection ->
            connection.prepare("SELECT budget_model_version, available_balance, savings_balance, needs FROM GAME_STATE").use {
                assertTrue(it.step())
                assertEquals(0L, it.getLong(0))
                assertEquals(32L, it.getLong(1))
                assertEquals(41L, it.getLong(2))
                assertEquals(35L, it.getLong(3))
            }
        }
        val db = GameDatabase.open(context, name)
        val expected = try {
            val repository = RoomGameRepository(db)
            val saved = checkNotNull(repository.observe().first())
            assertEquals(32L, saved.economy.availableBalance)
            assertEquals(41L, saved.economy.savingsBalance)
            assertEquals(32L, saved.economy.unallocated)
            assertEquals(BudgetPlan(0, 0, 0, 0), saved.economy.plan)
            assertEquals(BudgetPlanningReason.MIGRATION, saved.economy.planning!!.reason)
            assertEquals(32L, saved.economy.planning!!.baseAmount)
            assertEquals(listOf("second", "first"), saved.ownedItems.map { it.id })
            assertTrue(saved.financial.plans.isEmpty())
            val history = repository.readHistory()
            assertEquals(listOf(AuditType.IMPORTED_BASELINE, AuditType.TECHNICAL_UPDATE), history.map { it.type })
            val original = checkNotNull(history.last().before)
            assertEquals(BudgetPlan(35, 20, 35, 0), original.economy.plan)
            assertEquals(saved.copy(economy = original.economy), original)
            assertEquals(original, history.first().after)
            assertEquals(saved, history.last().after)
            assertTrue(history.all { it.operations.isEmpty() && it.facts.isEmpty() })
            assertEquals(1, db.gameStateDao().readStates().single().budgetModelVersion)
            repository.read()
            assertEquals(history, repository.readHistory())
            assertEquals(4, repository.exportSnapshot().formatVersion)
            HistoryCodec.validate(repository.exportSnapshot())
            saved to history
        } finally { db.close() }
        val reopened = GameDatabase.open(context, name)
        try {
            val repository = RoomGameRepository(reopened)
            assertEquals(expected.first, repository.read())
            assertEquals(expected.second, repository.readHistory())
        } finally { reopened.close() }
    }

    @Test fun existingHistoryAndImmutablePlanRowsArePreservedVerbatim() = runBlocking {
        val db = GameDatabase.open(context, name)
        try {
            val original = legacyState()
            val setup = RoomGameRepository(db)
            setup.initializeIfAbsent(original)
            val originalHistory = setup.readHistory()
            // Emulate a pre-upgrade row while preserving its real checkpoint and immutable revision.
            assertEquals(1, db.gameStateDao().updateState(original.toEntity().copy(budgetModelVersion = 0)))
            val repository = RoomGameRepository(db)
            val history = repository.readHistory() // History access must also finish the upgrade first.
            val saved = checkNotNull(repository.read())
            assertEquals(originalHistory, history.take(originalHistory.size))
            assertEquals(original.financial, saved.financial)
            assertEquals(original, history.last().before)
            assertEquals(saved, history.last().after)
            assertEquals(0L, saved.economy.plan.total)
            HistoryCodec.validate(repository.exportSnapshot())
        } finally { db.close() }
    }

    @Test fun v19ValidActiveDraftKeepsItsExactIdentityAndPartialAmounts() = runBlocking {
        createLegacyDatabase(withDraft = true)
        val db = GameDatabase.open(context, name)
        try {
            val repository = RoomGameRepository(db)
            val saved = checkNotNull(repository.read())
            assertEquals(32L, saved.economy.availableBalance)
            assertEquals(41L, saved.economy.savingsBalance)
            assertEquals(22L, saved.economy.unallocated)
            assertEquals(BudgetPlan(35, 20, 35, 0), saved.economy.plan)
            assertEquals(BudgetPlanning("existing-draft", BudgetPlanningReason.MANUAL, BudgetPlanningStage.ALLOCATION,
                0, revision = 8, draft = BudgetPlan(10, 0, 0, 0), baseAmount = 32), saved.economy.planning)
            val upgrade = repository.readHistory().last()
            assertEquals(upgrade.before, upgrade.after)
            assertEquals(1, db.gameStateDao().readStates().single().budgetModelVersion)
        } finally { db.close() }
    }

    @Test fun oldSnapshotFormatsUpgradeAfterValidationWhileFormat4RestoresTheExactLiveSnapshot() = runBlocking {
        val db = GameDatabase.open(context, name)
        try {
            val repository = RoomGameRepository(db)
            repository.initializeIfAbsent(createInitialGameState())
            for (format in 1..3) {
                val original = legacyState()
                val archive = oldSnapshot(original, format)
                val saved = repository.restoreSnapshot(archive, RestoreGuard(null, repository.readHistory().last().sequence))
                assertEquals(32L, saved.economy.availableBalance)
                assertEquals(41L, saved.economy.savingsBalance)
                assertEquals(BudgetPlan(0, 0, 0, 0), saved.economy.plan)
                assertEquals(original.financial, saved.financial)
                val history = repository.readHistory()
                assertEquals(listOf(AuditType.RESTORED, AuditType.TECHNICAL_UPDATE), history.map { it.type })
                assertEquals(original, history.first().after)
                assertEquals(original, history.last().before)
                assertEquals(saved, history.last().after)
                HistoryCodec.validate(repository.exportSnapshot())
            }
            val live = repository.exportSnapshot()
            assertEquals(4, live.formatVersion)
            repository.update { it.copy(fatigue = it.fatigue + 1) }
            val restored = repository.restoreSnapshot(live, RestoreGuard(null, repository.readHistory().last().sequence))
            assertEquals(live.state, restored)
            assertEquals(live.history, repository.readHistory().dropLast(1))
            assertEquals(AuditType.RESTORED, repository.readHistory().last().type)

            val beforeFailure = repository.exportSnapshot()
            try {
                repository.restoreSnapshot(oldSnapshot(legacyState(), 3).copy(checksum = "invalid"),
                    RestoreGuard(null, beforeFailure.historySequence))
                fail("An invalid old checksum must fail before any write")
            } catch (_: IllegalArgumentException) { }
            assertEquals(beforeFailure, repository.exportSnapshot())
        } finally { db.close() }
    }

    @Test fun format4MustContainAValidLiveAllocationOrAValidActiveDraftBeforeAnyReplacement() = runBlocking {
        val db = GameDatabase.open(context, name)
        try {
            val repository = RoomGameRepository(db)
            repository.initializeIfAbsent(createInitialGameState())
            val original = repository.exportSnapshot()
            val invalidReady = legacyState()
            val validDraft = invalidReady.copy(economy = invalidReady.economy.copy(unallocated = 22,
                planning = BudgetPlanning("existing", BudgetPlanningReason.MANUAL, BudgetPlanningStage.ALLOCATION,
                    0, revision = 8, draft = BudgetPlan(10, 0, 0, 0), baseAmount = 32)))
            val invalidDraft = validDraft.copy(economy = validDraft.economy.copy(unallocated = 21))
            for (invalid in listOf(invalidReady, invalidDraft)) {
                val archive = HistoryCodec.snapshot("invalid-live", invalid, emptyList())
                HistoryCodec.validate(archive) // A valid checksum does not establish live-budget semantics.
                try {
                    repository.restoreSnapshot(archive, RestoreGuard(null, original.historySequence))
                    fail("Invalid live amounts must be rejected")
                } catch (_: IllegalArgumentException) { }
                assertEquals(original, repository.exportSnapshot())
            }
            val archive = HistoryCodec.snapshot("valid-draft", validDraft, emptyList())
            assertEquals(validDraft, repository.restoreSnapshot(archive, RestoreGuard(null, original.historySequence)))
            assertEquals(listOf(AuditType.RESTORED), repository.readHistory().map { it.type })
            val receipt = HistoryCodec.snapshot("valid-receipt", createInitialGameState(), emptyList())
            assertEquals(receipt.state, repository.restoreSnapshot(receipt,
                RestoreGuard(null, repository.readHistory().last().sequence)))
        } finally { db.close() }
    }

    private suspend fun createLegacyDatabase(withDraft: Boolean = false) {
        schemas.createDatabase(19).use { connection ->
            connection.execSQL("""
                INSERT INTO GAME_STATE(id, visual_state, selected_look, satiety, fatigue, unallocated,
                    needs, wants, savings, reserve, available_balance, savings_balance)
                VALUES ('current', 'NORMAL', 'PLAIN', 0, 0, ${if (withDraft) 22 else 0}, 35, 20, 35, 0, 32, 41)
            """.trimIndent())
            connection.execSQL("INSERT INTO ITEM VALUES ('map', 'Map', '', 'STORY', 24)")
            connection.execSQL("INSERT INTO OWNED_ITEM VALUES ('second', 'current', 0, 'map')")
            connection.execSQL("INSERT INTO OWNED_ITEM VALUES ('first', 'current', 1, 'map')")
            if (withDraft) connection.execSQL("""
                INSERT INTO BUDGET_PLANNING VALUES ('current', 'existing-draft', 'MANUAL', 'ALLOCATION', 0, 8, 10, 0, 0, 0, 32)
            """.trimIndent())
        }
    }

    private fun legacyState(): GameState = createInitialGameState().let { state ->
        val plan = BudgetPlan(35, 20, 35, 0)
        state.copy(economy = EconomyState(plan, availableBalance = 32, savingsBalance = 41),
            financial = state.financial.copy(plans = listOf(BudgetPlanRevision("legacy-plan", null, 1, 1,
                90, plan, BudgetRevisionReason.INITIAL))))
    }

    private fun oldSnapshot(state: GameState, format: Int): GameSnapshot {
        fun oldShape(value: JsonElement): JsonElement = when (value) {
            is JsonArray -> JsonArray(value.map(::oldShape))
            is JsonObject -> JsonObject(value.filterKeys { key ->
                !(format < 3 && key == "selectedSavingItemId") &&
                    !(format == 1 && key in setOf("eventHistory", "savingPractice", "reviewEvidence"))
            }.mapValues { oldShape(it.value) })
            else -> value
        }
        val encodedState = oldShape(Json.parseToJsonElement(HistoryCodec.encodeState(state)))
        val run = "legacy-$format"
        val archive = GameSnapshot(formatVersion = format, runId = run, state = state, history = emptyList(),
            historySequence = 0, checksum = HistoryCodec.sha256("$format\n$run\n0\n$encodedState\n[]"))
        return HistoryCodec.decodeSnapshot(HistoryCodec.encodeSnapshot(archive))
    }
}
