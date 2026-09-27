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
import ru.nksk.lctapp.data.game.local.MIGRATION_18_19
import ru.nksk.lctapp.domain.history.HistoryCodec
import ru.nksk.lctapp.domain.history.RestoreGuard

/** Compiled only; device execution follows the user's separate verification preference. */
@RunWith(AndroidJUnit4::class)
class SavingGoalMigrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val name = "saving-goal-19-${java.util.UUID.randomUUID()}.db"
    @get:Rule val schemas = MigrationTestHelper(instrumentation = instrumentation,
        databaseClass = GameDatabase::class, driver = BundledSQLiteDriver(), file = context.getDatabasePath(name))
    @After fun cleanup() { context.deleteDatabase(name) }

    @Test fun migrationPreservesMoneyRepeatedItemsAndDraftThenTargetSurvivesRestoreAndReopen() = runBlocking {
        schemas.createDatabase(18).use { connection ->
            connection.execSQL("""
                INSERT INTO GAME_STATE(id, visual_state, selected_look, satiety, fatigue, unallocated,
                    needs, wants, savings, reserve, available_balance, savings_balance)
                VALUES ('current', 'NORMAL', 'PLAIN', 75, 0, 0, 35, 20, 30, 5, 61, 19)
            """.trimIndent())
            connection.execSQL("INSERT INTO GOAL VALUES ('old-goal', 'Old goal', '')")
            connection.execSQL("INSERT INTO GOAL_SELECTION VALUES ('current', 'old-goal')")
            connection.execSQL("INSERT INTO ITEM VALUES ('map', 'Map', '', 'STORY', 24)")
            connection.execSQL("INSERT INTO ITEM VALUES ('tripod', 'Tripod', '', 'STORY', 36)")
            connection.execSQL("INSERT INTO OWNED_ITEM VALUES ('second', 'current', 0, 'map')")
            connection.execSQL("INSERT INTO OWNED_ITEM VALUES ('first', 'current', 1, 'map')")
            connection.execSQL("INSERT INTO ONBOARDING_DRAFT VALUES ('current', 'Искорка', 'Curious', 'Sand', 'INTRODUCTION', 'BANDANA', 'old-goal')")
        }
        schemas.runMigrationsAndValidate(19, listOf(MIGRATION_18_19)).use { connection ->
            connection.prepare("PRAGMA foreign_key_check").use { assertFalse(it.step()) }
            connection.prepare("SELECT name, accessory_id, step, goal_id, saving_item_id FROM ONBOARDING_DRAFT").use {
                assertTrue(it.step())
                assertEquals("Искорка", it.getText(0))
                assertEquals("BANDANA", it.getText(1))
                assertEquals("GOAL_SELECTION", it.getText(2))
                assertEquals("old-goal", it.getText(3))
                assertTrue(it.isNull(4))
            }
        }
        val db = GameDatabase.open(context, name)
        val expected = try {
            val games = RoomGameRepository(db)
            val migrated = games.read()!!
            assertEquals(61L, migrated.economy.availableBalance)
            assertEquals(19L, migrated.economy.savingsBalance)
            assertEquals(listOf("second", "first"), migrated.ownedItems.map { it.id })
            assertEquals("old-goal", migrated.selectedGoalId)
            assertNull(migrated.selectedSavingItemId)
            val selected = games.update { it.copy(selectedSavingItemId = "tripod") }
            val snapshot = HistoryCodec.decodeSnapshot(HistoryCodec.encodeSnapshot(games.exportSnapshot()))
            games.update { it.copy(selectedSavingItemId = null) }
            games.restoreSnapshot(snapshot, RestoreGuard(null, games.readHistory().last().sequence))
            assertEquals(selected, games.read())
            val historyBefore = games.readHistory()
            try { games.update { it.copy(selectedSavingItemId = "missing-item") }; fail("Foreign key must reject the target") }
            catch (_: androidx.sqlite.SQLiteException) { }
            assertEquals(selected, games.read())
            assertEquals(historyBefore, games.readHistory())
            selected
        } finally { db.close() }
        val reopened = GameDatabase.open(context, name)
        try { assertEquals(expected, RoomGameRepository(reopened).read()) } finally { reopened.close() }
    }
}
