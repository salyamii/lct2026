package ru.nksk.lctapp.data.game

import androidx.room3.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.nksk.lctapp.data.game.local.*
import ru.nksk.lctapp.data.backend.RoomBackendSyncStore

/** Compilation coverage only; connected execution requires separate user authorization. */
@RunWith(AndroidJUnit4::class)
class BackendSyncMigrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val name = "backend-sync-21-${java.util.UUID.randomUUID()}.db"
    @get:Rule val schemas = MigrationTestHelper(instrumentation = instrumentation,
        databaseClass = GameDatabase::class, driver = BundledSQLiteDriver(), file = context.getDatabasePath(name))
    @After fun cleanup() { context.deleteDatabase(name) }

    @Test fun v20UpgradeKeepsWorldAndRepeatedInventoryAndCreatesEmptyTransportState() = runBlocking {
        schemas.createDatabase(20).use { db ->
            db.execSQL("INSERT INTO ITEM(id, name, description, category) VALUES ('item', 'Item', '', 'STORY')")
            db.execSQL("""INSERT INTO GAME_STATE(id, visual_state, selected_look, satiety, fatigue,
                unallocated, needs, wants, savings, reserve, available_balance, savings_balance,
                budget_model_version, pet_name, location_id, location_lighting)
                VALUES ('current', 'NORMAL', 'PLAIN', 3, 2, 0, 5, 7, 0, 11, 23, 19,
                1, 'Имя до обновления', 'pier', 'EVENING')""")
            db.execSQL("INSERT INTO OWNED_ITEM(id, game_state_id, position, item_id) VALUES ('second', 'current', 0, 'item'), ('first', 'current', 1, 'item')")
        }
        schemas.runMigrationsAndValidate(21, listOf(MIGRATION_20_21)).use { db ->
            db.prepare("SELECT pet_name, available_balance, savings_balance, location_id, location_lighting FROM GAME_STATE").use {
                assertTrue(it.step())
                assertEquals("Имя до обновления", it.getText(0))
                assertEquals(23L, it.getLong(1)); assertEquals(19L, it.getLong(2))
                assertEquals("pier", it.getText(3)); assertEquals("EVENING", it.getText(4))
            }
            db.prepare("SELECT id FROM OWNED_ITEM ORDER BY position").use {
                assertTrue(it.step()); assertEquals("second", it.getText(0))
                assertTrue(it.step()); assertEquals("first", it.getText(0))
                assertFalse(it.step())
            }
            for (table in listOf("BACKEND_SYNC_STATE", "PENDING_BACKEND_REQUEST")) {
                db.prepare("SELECT COUNT(*) FROM $table").use { assertTrue(it.step()); assertEquals(0L, it.getLong(0)) }
            }
        }
    }

    @Test fun frozenRequestSurvivesReopenRejectsChangedBodyAndIgnoresAnOldAcknowledgement() = runBlocking {
        var db = GameDatabase.open(context, name)
        try {
            val state = BackendSyncStateEntity("profile", "https://example.test/", serverRevision = 4,
                gameRunId = "run", localGeneration = "generation", rewardFetchCursor = 3)
            val pending = PendingBackendRequestEntity("profile", "snapshot", "request", "{\"value\":1}\n")
            db.backendSyncDao().upsertState(state)
            db.backendSyncDao().upsertPending(pending)
            db.close()
            db = GameDatabase.open(context, name)
            val dao = db.backendSyncDao()
            assertEquals(state, dao.readState("profile"))
            assertEquals(pending, dao.readPending("profile", "snapshot"))
            dao.upsertPending(pending)
            try { dao.upsertPending(pending.copy(payload = "{\"value\":2}")); fail("A retry body must be immutable") }
            catch (_: IllegalArgumentException) { }
            dao.deletePending("profile", "snapshot", "older-request")
            assertEquals(pending, dao.readPending("profile", "snapshot"))
            dao.deletePending("profile", "snapshot", pending.requestId)
            assertNull(dao.readPending("profile", "snapshot"))
        } finally { db.close() }
    }

    @Test fun v23UpgradePreservesTheWorldAndFrozenRequestsAndStartsWithoutAReplacementChoice() = runBlocking {
        schemas.createDatabase(23).use { db ->
            db.execSQL("""INSERT INTO GAME_STATE(id, visual_state, selected_look, satiety, fatigue,
                unallocated, needs, wants, savings, reserve, available_balance, savings_balance,
                budget_model_version, pet_name, location_id, location_lighting)
                VALUES ('current', 'NORMAL', 'PLAIN', 3, 2, 0, 5, 7, 0, 11, 23, 19,
                1, 'Текущая игра', 'pier', 'EVENING')""")
            db.execSQL("INSERT INTO BACKEND_SYNC_STATE(profile_id, backend_url, server_revision, last_analytics_sequence, game_run_id, local_generation) VALUES ('device', 'https://example.test/', 9, 17, 'run', 'generation')")
            db.execSQL("INSERT INTO PENDING_BACKEND_REQUEST(profile_id, kind, request_id, payload) VALUES ('device', 'snapshot', 'frozen-id', 'exact frozen bytes')")
        }
        schemas.runMigrationsAndValidate(24, listOf(MIGRATION_23_24)).use { db ->
            db.prepare("SELECT pet_name, available_balance, savings_balance FROM GAME_STATE").use {
                assertTrue(it.step()); assertEquals("Текущая игра", it.getText(0))
                assertEquals(23L, it.getLong(1)); assertEquals(19L, it.getLong(2))
            }
            db.prepare("SELECT server_revision, last_analytics_sequence, game_run_id, local_generation, cloud_lineage_payload FROM BACKEND_SYNC_STATE").use {
                assertTrue(it.step()); assertEquals(9L, it.getLong(0)); assertEquals(17L, it.getLong(1))
                assertEquals("run", it.getText(2)); assertEquals("generation", it.getText(3)); assertTrue(it.isNull(4))
            }
            db.prepare("SELECT request_id, payload FROM PENDING_BACKEND_REQUEST").use {
                assertTrue(it.step()); assertEquals("frozen-id", it.getText(0)); assertEquals("exact frozen bytes", it.getText(1))
            }
        }
    }

    @Test fun replacementChoiceAndRequestSurviveReopenAndAFailedStageRollsBackBoth() = runBlocking {
        var db = GameDatabase.open(context, name)
        try {
            val original = BackendSyncStateEntity("device", "https://example.test/", serverRevision = 3)
            val pending = PendingBackendRequestEntity("device", "snapshot", "old-id", "old frozen bytes")
            db.backendSyncDao().upsertState(original)
            db.backendSyncDao().upsertPending(pending)
            val choice = original.copy(cloudLineagePayload = "durable choice", gameRunId = "current-run")
            val next = pending.copy(requestId = "new-id", payload = "new frozen bytes")
            try { RoomBackendSyncStore(db).stageLocalUpload(choice, next); fail("Uncertain write cannot be replaced") }
            catch (_: IllegalStateException) { }
            assertEquals(original, db.backendSyncDao().readState("device"))
            assertEquals(pending, db.backendSyncDao().readPending("device", "snapshot"))
            db.backendSyncDao().deletePending("device", "snapshot", "old-id")
            RoomBackendSyncStore(db).stageLocalUpload(choice, next)
            db.close()
            db = GameDatabase.open(context, name)
            assertEquals(choice, db.backendSyncDao().readState("device"))
            assertEquals(next, db.backendSyncDao().readPending("device", "snapshot"))
        } finally { db.close() }
    }
}
