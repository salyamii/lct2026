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
import ru.nksk.lctapp.data.game.local.MIGRATION_17_18
import ru.nksk.lctapp.domain.engine.EventExposure
import ru.nksk.lctapp.domain.finance.*
import ru.nksk.lctapp.domain.history.HistoryCodec
import ru.nksk.lctapp.domain.history.RestoreGuard

/** Source is compiled only; device execution requires the user's explicit authorization. */
@RunWith(AndroidJUnit4::class)
class EventExposureMigrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val name = "exposure-18-${java.util.UUID.randomUUID()}.db"
    @get:Rule val schemas = MigrationTestHelper(instrumentation = instrumentation,
        databaseClass = GameDatabase::class, driver = BundledSQLiteDriver(), file = context.getDatabasePath(name))
    @After fun cleanup() { context.deleteDatabase(name) }

    @Test fun legacyMoneyAndPracticeStayUnchangedAndNewEvidenceSurvivesRestoreAndReopen() = runBlocking {
        schemas.createDatabase(17).use { connection ->
            connection.execSQL("""
                INSERT INTO GAME_STATE(id, visual_state, selected_look, satiety, fatigue, unallocated,
                    needs, wants, savings, reserve, available_balance, savings_balance)
                VALUES ('current', 'NORMAL', 'PLAIN', 75, 0, 0, 35, 20, 30, 5, 61, 19)
            """.trimIndent())
            connection.execSQL("""
                INSERT INTO FINANCIAL_PERIOD VALUES ('period', 'current', 0, 'goal', 1, 1, 61, 19,
                    NULL, 1, 1, 1, 1, 10, 4, 6, 2, 1)
            """.trimIndent())
            connection.execSQL("INSERT INTO FINANCIAL_CURSOR VALUES ('current', 'period')")
            connection.execSQL("INSERT INTO EVENT(id, type, title, description, money_delta_on_start) VALUES ('event', 'RANDOM', 'Event', '', 0)")
        }
        schemas.runMigrationsAndValidate(18, listOf(MIGRATION_17_18)).close()
        val db = GameDatabase.open(context, name)
        val expected = try {
            val games = RoomGameRepository(db)
            val migrated = games.read()!!
            assertEquals(61L, migrated.economy.availableBalance)
            assertEquals(19L, migrated.economy.savingsBalance)
            assertTrue(migrated.eventHistory.isEmpty())
            assertNull(migrated.financial.currentPeriod!!.savingPractice)
            assertNull(migrated.financial.currentPeriod!!.reviewEvidence)
            assertTrue(migrated.financial.currentPeriod!!.reviewedPlan)
            val after = games.update { state -> state.copy(
                eventHistory = listOf(EventExposure("event", 3, null, 1)),
                financial = state.financial.copy(periods = listOf(state.financial.currentPeriod!!.copy(
                    savingPractice = SavingsPracticeState(openingSavings = 19, recoveryQuestionId = "practice"),
                    reviewEvidence = PeriodReviewEvidence("review", null, false, true, guidedRecovery = true))))) }
            val snapshot = games.exportSnapshot()
            HistoryCodec.validate(HistoryCodec.decodeSnapshot(HistoryCodec.encodeSnapshot(snapshot)))
            games.update { it.copy(eventHistory = emptyList()) }
            games.restoreSnapshot(snapshot, RestoreGuard(null, games.readHistory().last().sequence))
            assertEquals(after, games.read())
            after
        } finally { db.close() }
        val reopened = GameDatabase.open(context, name)
        try { assertEquals(expected, RoomGameRepository(reopened).read()) } finally { reopened.close() }
    }
}
