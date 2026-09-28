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
import ru.nksk.lctapp.data.game.local.GameDatabase
import ru.nksk.lctapp.data.game.local.MIGRATION_21_22

@RunWith(AndroidJUnit4::class)
class CampaignArchiveMigrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val name = "campaign-archive-22-${java.util.UUID.randomUUID()}.db"
    @get:Rule val schemas = MigrationTestHelper(instrumentation = instrumentation,
        databaseClass = GameDatabase::class, driver = BundledSQLiteDriver(), file = context.getDatabasePath(name))
    @After fun cleanup() { context.deleteDatabase(name) }

    @Test fun v21WorldJournalAndPendingRequestsSurviveAdditiveArchiveMigration() = runBlocking {
        schemas.createDatabase(21).use { db ->
            db.execSQL("""INSERT INTO GAME_STATE(id, visual_state, selected_look, satiety, fatigue,
                unallocated, needs, wants, savings, reserve, available_balance, savings_balance,
                budget_model_version, pet_name, location_id, location_lighting)
                VALUES ('current', 'NORMAL', 'PLAIN', 3, 2, 0, 5, 7, 0, 11, 23, 19,
                1, 'До обновления', 'pier', 'EVENING')""")
            db.execSQL("INSERT INTO GAME_RUN(game_state_id, run_id) VALUES ('current', 'old-run')")
            db.execSQL("INSERT INTO GAME_AUDIT(id, run_id, sequence, type, format_version, payload) VALUES ('entry', 'old-run', 1, 'INITIALIZED', 1, 'unchanged-payload')")
            db.execSQL("INSERT INTO PENDING_BACKEND_REQUEST(profile_id, kind, request_id, payload) VALUES ('device', 'snapshot', 'pending', 'frozen-request')")
        }
        schemas.runMigrationsAndValidate(22, listOf(MIGRATION_21_22)).use { db ->
            db.prepare("SELECT pet_name, available_balance, savings_balance FROM GAME_STATE").use {
                assertTrue(it.step()); assertEquals("До обновления", it.getText(0))
                assertEquals(23L, it.getLong(1)); assertEquals(19L, it.getLong(2))
            }
            db.prepare("SELECT payload FROM GAME_AUDIT WHERE id = 'entry'").use {
                assertTrue(it.step()); assertEquals("unchanged-payload", it.getText(0))
            }
            db.prepare("SELECT payload FROM PENDING_BACKEND_REQUEST WHERE request_id = 'pending'").use {
                assertTrue(it.step()); assertEquals("frozen-request", it.getText(0))
            }
            db.prepare("SELECT COUNT(*) FROM GAME_RUN_ARCHIVE").use { assertTrue(it.step()); assertEquals(0L, it.getLong(0)) }
        }
    }
}
