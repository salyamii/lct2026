package ru.nksk.lctapp.data.game

import androidx.room3.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.nksk.lctapp.app.createInitialGameState
import ru.nksk.lctapp.data.game.content.bundledGameCatalog
import ru.nksk.lctapp.data.game.local.GameDatabase
import ru.nksk.lctapp.data.game.local.MIGRATION_6_7
import ru.nksk.lctapp.domain.engine.*

@RunWith(AndroidJUnit4::class)
class GoalPersistenceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val name = "goal-${java.util.UUID.randomUUID()}.db"
    @get:Rule val schemas = MigrationTestHelper(instrumentation = instrumentation,
        databaseClass = GameDatabase::class, driver = BundledSQLiteDriver(), file = context.getDatabasePath(name))
    @After fun cleanup() { context.deleteDatabase(name) }

    @Test fun versionSixMigrationPreservesBalanceRepeatedItemsEngineAndLegacyReceipts() = runBlocking {
        schemas.createDatabase(6).use { connection ->
            connection.execSQL("""
                INSERT INTO GAME_STATE (id, visual_state, selected_look, satiety, fatigue,
                    balance, planned_needs, planned_wants, planned_savings, planned_reserve)
                VALUES ('current', 'HAPPY', 'custom-look', 17, 30, 247, 10, 20, 30, 40)
            """.trimIndent())
            connection.execSQL("INSERT INTO ITEM VALUES ('map', 'Map', 'Description', 'STORY', 15)")
            connection.execSQL("INSERT INTO OWNED_ITEM VALUES ('second', 'current', 0, 'map')")
            connection.execSQL("INSERT INTO OWNED_ITEM VALUES ('first', 'current', 1, 'map')")
            connection.execSQL("INSERT INTO MINI_GAME_COMPLETION VALUES ('old-attempt', 'current')")
            connection.execSQL("INSERT INTO ENGINE_STATE VALUES ('current', 'rules', 42, 3, 'RUNNING', 2, 4, 1, 3, 255)")
        }
        schemas.runMigrationsAndValidate(7, listOf(MIGRATION_6_7)).use { connection ->
            connection.prepare("PRAGMA foreign_key_check").use { assertFalse(it.step()) }
        }
        val database = GameDatabase.open(context, name)
        val snapshot = try {
            RoomGameRepository(database).read()!!.also { saved ->
                assertNull(saved.selectedGoalId)
                assertEquals(247L, saved.economy.balance)
                assertEquals(17, saved.satiety)
                assertEquals(30, saved.fatigue)
                assertEquals(listOf("second", "first"), saved.ownedItems.map { it.id })
                assertEquals(setOf("old-attempt"), saved.completedMiniGames)
                assertEquals(42L, saved.engine!!.revision)
                assertEquals(3, saved.engine!!.nextMorningEnergy)
            }
        } finally { database.close() }
        val reopened = GameDatabase.open(context, name)
        try { assertEquals(snapshot, RoomGameRepository(reopened).read()) } finally { reopened.close() }
    }

    @Test fun selectedGoalPurchaseAndRevisionSurviveReopenAndBrokenFkRollsBackEverything() = runBlocking {
        val catalog = bundledGameCatalog()
        val goal = catalog.goals.first { it.goalId == "figma-stargazing-180-v1" }
        var database = GameDatabase.open(context, name)
        try {
            var games = RoomGameRepository(database)
            var session = GameSession(games, RoomStoryContentRepository(database), catalog, createInitialGameState())
            session.prepare()
            val select = session.selectGoalCommand(games.read()!!, goal.goalId)
            assertTrue(session.dispatch(EngineRequest("select", null, select)) is EngineResult.Applied)
            val request = EngineRequest("buy", games.read()!!.engine!!.revision,
                EngineCommand.BuyGoalItem(goal.goalId, goal.itemIds.first()))
            assertTrue(session.dispatch(request) is EngineResult.Applied)
            val bought = games.read()!!
            assertEquals(bought, games.observe().first())
            database.close()
            database = GameDatabase.open(context, name)
            games = RoomGameRepository(database)
            session = GameSession(games, RoomStoryContentRepository(database), catalog, createInitialGameState())
            session.prepare()
            assertEquals(bought, games.read())
            assertEquals(EngineResult.Blocked(BlockReason.StaleRevision), session.dispatch(request))
            try {
                games.update { it.copy(selectedGoalId = "missing-goal", economy = it.economy.copy(balance = 0), ownedItems = emptyList()) }
                fail("Unknown goal FK must roll back the whole outcome")
            } catch (_: androidx.sqlite.SQLiteException) { }
            assertEquals(bought, games.read())
            assertEquals(76L, bought.economy.balance)
            assertEquals(goal.goalId, bought.selectedGoalId)
            assertEquals(1, bought.ownedItems.size)
        } finally { database.close() }
    }
}
