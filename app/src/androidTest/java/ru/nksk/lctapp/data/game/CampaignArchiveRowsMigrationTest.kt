package ru.nksk.lctapp.data.game

import androidx.room3.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.nksk.lctapp.app.createInitialGameState
import ru.nksk.lctapp.data.game.local.GameDatabase
import ru.nksk.lctapp.data.game.local.MIGRATION_22_23
import ru.nksk.lctapp.domain.history.AuditEntry
import ru.nksk.lctapp.domain.history.AuditType
import ru.nksk.lctapp.domain.history.HistoryCodec

/** Source coverage only; device execution remains a separate user-authorized action. */
@RunWith(AndroidJUnit4::class)
class CampaignArchiveRowsMigrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val name = "campaign-archive-rows-23-${java.util.UUID.randomUUID()}.db"
    @get:Rule val schemas = MigrationTestHelper(instrumentation = instrumentation,
        databaseClass = GameDatabase::class, driver = BundledSQLiteDriver(), file = context.getDatabasePath(name))
    @After fun cleanup() { context.deleteDatabase(name) }

    @Test fun v22ArchivesKeepWorldsChecksumsReceiptsAndOrderedHistoryInSeparateRows() = runBlocking {
        val first = createInitialGameState().let { it.copy(pet = it.pet.copy(name = "Лис \"с историей\" 🙂\nЕщё строка")) }
        val final = first.copy(pet = first.pet.copy(name = "Второй образ"))
        val entries = listOf(
            AuditEntry("first", 1, "archived-one", AuditType.INITIALIZED, after = first),
            AuditEntry("second", 2, "archived-one", AuditType.TECHNICAL_UPDATE, before = first, after = final),
        )
        val original = HistoryCodec.snapshot("archived-one", final, entries)
        val second = HistoryCodec.snapshot("archived-two", first,
            listOf(AuditEntry("third", 1, "archived-two", AuditType.INITIALIZED, after = first)))
        val encoded = HistoryCodec.encodeSnapshot(original)
        val oldHeader = Json.parseToJsonElement(encoded).jsonObject
        schemas.createDatabase(22).use { db ->
            db.execSQL("""INSERT INTO GAME_STATE(id, visual_state, selected_look, satiety, fatigue,
                unallocated, needs, wants, savings, reserve, available_balance, savings_balance,
                budget_model_version, pet_name, location_id, location_lighting)
                VALUES ('current', 'NORMAL', 'PLAIN', 3, 2, 0, 5, 7, 0, 11, 23, 19,
                1, 'Активный мир', 'pier', 'EVENING')""")
            db.execSQL("INSERT INTO GAME_RUN(game_state_id, run_id) VALUES ('current', 'active-run')")
            db.execSQL("INSERT INTO GAME_AUDIT(id, run_id, sequence, type, format_version, payload) VALUES ('active-entry', 'active-run', 1, 'INITIALIZED', 1, 'active-payload')")
            db.execSQL("INSERT INTO PENDING_BACKEND_REQUEST(profile_id, kind, request_id, payload) VALUES ('device', 'snapshot', 'pending', 'frozen-request')")
            listOf(original, second).forEachIndexed { index, snapshot ->
                db.prepare("INSERT INTO GAME_RUN_ARCHIVE(run_id, position, restart_request_id, next_run_id, snapshot_payload) VALUES (?, ?, ?, ?, ?)").use {
                    it.bindText(1, snapshot.runId); it.bindLong(2, index.toLong())
                    it.bindText(3, "restart-$index"); it.bindText(4, if (index == 0) "archived-two" else "active-run")
                    it.bindText(5, HistoryCodec.encodeSnapshot(snapshot)); it.step()
                }
            }
        }
        schemas.runMigrationsAndValidate(23, listOf(MIGRATION_22_23)).use { db ->
            db.prepare("SELECT position, restart_request_id, next_run_id, snapshot_payload FROM GAME_RUN_ARCHIVE WHERE run_id = 'archived-one'").use {
                assertTrue(it.step()); assertEquals(0L, it.getLong(0))
                assertEquals("restart-0", it.getText(1)); assertEquals("archived-two", it.getText(2))
                val header = Json.parseToJsonElement(it.getText(3)).jsonObject
                assertEquals(oldHeader - "history", header - "history")
                assertEquals(JsonArray(emptyList()), header.getValue("history"))
            }
            db.prepare("SELECT sequence, payload FROM GAME_RUN_ARCHIVE_AUDIT WHERE archive_run_id = 'archived-one' ORDER BY sequence").use {
                val restored = mutableListOf<AuditEntry>()
                entries.forEach { expected ->
                    assertTrue(it.step()); assertEquals(expected.sequence, it.getLong(0))
                    val entry = HistoryCodec.decodeEntry(it.getText(1))
                    assertEquals(expected, entry); restored += entry
                }
                assertFalse(it.step())
                HistoryCodec.validate(original.copy(history = restored))
            }
            db.prepare("SELECT pet_name, available_balance, savings_balance FROM GAME_STATE").use {
                assertTrue(it.step()); assertEquals("Активный мир", it.getText(0))
                assertEquals(23L, it.getLong(1)); assertEquals(19L, it.getLong(2))
            }
            db.prepare("SELECT payload FROM GAME_AUDIT WHERE id = 'active-entry'").use {
                assertTrue(it.step()); assertEquals("active-payload", it.getText(0))
            }
            db.prepare("SELECT payload FROM PENDING_BACKEND_REQUEST WHERE request_id = 'pending'").use {
                assertTrue(it.step()); assertEquals("frozen-request", it.getText(0))
            }
            db.execSQL("PRAGMA foreign_keys=ON")
            db.execSQL("DELETE FROM GAME_RUN_ARCHIVE WHERE run_id = 'archived-one'")
            db.prepare("SELECT archive_run_id, sequence FROM GAME_RUN_ARCHIVE_AUDIT").use {
                assertTrue(it.step()); assertEquals("archived-two", it.getText(0)); assertEquals(1L, it.getLong(1))
                assertFalse(it.step())
            }
            db.prepare("PRAGMA foreign_key_check").use { assertFalse(it.step()) }
        }
    }
}
