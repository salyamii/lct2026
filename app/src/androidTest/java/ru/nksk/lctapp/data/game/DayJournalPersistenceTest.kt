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
import ru.nksk.lctapp.data.game.local.MIGRATION_10_11
import ru.nksk.lctapp.domain.engine.*
import ru.nksk.lctapp.domain.game.OwnedItem

@RunWith(AndroidJUnit4::class)
class DayJournalPersistenceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val name = "day-journal-${java.util.UUID.randomUUID()}.db"
    @get:Rule val schemas = MigrationTestHelper(instrumentation = instrumentation,
        databaseClass = GameDatabase::class, driver = BundledSQLiteDriver(), file = context.getDatabasePath(name))
    @After fun cleanup() { context.deleteDatabase(name) }

    @Test fun migrationPreservesOldProgressWithoutInventingReceipts() = runBlocking {
        schemas.createDatabase(10).use { connection ->
            connection.execSQL("""
                INSERT INTO GAME_STATE (id, visual_state, selected_look, satiety, fatigue,
                    balance, planned_needs, planned_wants, planned_savings, planned_reserve, pet_name, pet_age)
                VALUES ('current', 'HAPPY', 'custom-look', 17, 30, 247, 10, 20, 30, 40, 'Тоша', 'SENIOR')
            """.trimIndent())
            connection.execSQL("INSERT INTO ITEM VALUES ('map', 'Map', 'Description', 'STORY', 24)")
            connection.execSQL("INSERT INTO OWNED_ITEM VALUES ('second', 'current', 0, 'map')")
            connection.execSQL("INSERT INTO OWNED_ITEM VALUES ('first', 'current', 1, 'map')")
            connection.execSQL("INSERT INTO ENGINE_STATE VALUES ('current', 'rules', 42, 3, 'FINISHED', 2, 0, 1, 3, 255)")
        }
        schemas.runMigrationsAndValidate(11, listOf(MIGRATION_10_11)).use { connection ->
            connection.prepare("PRAGMA foreign_key_check").use { assertFalse(it.step()) }
        }
        val database = GameDatabase.open(context, name)
        try {
            val saved = RoomGameRepository(database).read()!!
            assertEquals("Тоша", saved.pet.name)
            assertEquals("custom-look", saved.pet.selectedLookId)
            assertEquals(247L, saved.economy.balance)
            assertEquals(listOf("second", "first"), saved.ownedItems.map { it.id })
            assertEquals(42L, saved.engine!!.revision)
            assertEquals(DayPhase.FINISHED, saved.engine!!.phase)
            assertEquals(255L, saved.engine!!.openingBalance)
            assertNull(saved.engine!!.openingEnergy)
            assertTrue(saved.engine!!.journal.isEmpty())
        } finally { database.close() }
    }

    @Test fun repeatedReceiptsSurviveReopenAndRollBackWithTheWholeGame() = runBlocking {
        val catalog = bundledGameCatalog()
        var database = GameDatabase.open(context, name)
        try {
            RoomStoryContentRepository(database).install(catalog.content)
            var games = RoomGameRepository(database)
            games.initializeIfAbsent(createInitialGameState().let { it.copy(economy = it.economy.withTotalBalance(100)) })
            val engine = GameEngine(games, EventFactory(catalog.content, catalog.policies, catalog.meals, catalog.goals, catalog.storyCampaign), catalog.rules)
            assertTrue(engine.dispatch(EngineRequest("begin", null,
                EngineCommand.BeginDay(catalog.storyDayId, catalog.deedPool.take(4)))) is EngineResult.Applied)
            repeat(2) { index ->
                assertTrue(engine.dispatch(EngineRequest("meal-$index", games.read()!!.engine!!.revision,
                    EngineCommand.Feed(catalog.meals.first { it.price > 0 }.id))) is EngineResult.Applied)
            }
            val saved = games.read()!!
            assertEquals(2, saved.engine!!.journal.size)
            database.close()
            database = GameDatabase.open(context, name)
            games = RoomGameRepository(database)
            assertEquals(saved, games.observe().first())
            assertEquals(saved, games.initializeIfAbsent(createInitialGameState().let { it.copy(economy = it.economy.withTotalBalance(100)) }))
            try {
                games.update { it.copy(economy = it.economy.withTotalBalance(0),
                    engine = it.engine!!.copy(journal = emptyList()), ownedItems = it.ownedItems + OwnedItem("bad", "unknown-item")) }
                fail("Invalid inventory must roll back the balance and journal")
            } catch (_: androidx.sqlite.SQLiteException) { }
            assertEquals(saved, games.read())
        } finally { database.close() }
    }
}
