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
}
