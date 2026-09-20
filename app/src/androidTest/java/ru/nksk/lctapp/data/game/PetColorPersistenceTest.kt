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
import ru.nksk.lctapp.data.game.local.GameDatabase
import ru.nksk.lctapp.data.game.local.MIGRATION_11_12
import ru.nksk.lctapp.domain.game.OwnedItem
import ru.nksk.lctapp.domain.pet.PetAge
import ru.nksk.lctapp.domain.pet.PetColor
import ru.nksk.lctapp.domain.pet.PetVisualState

@RunWith(AndroidJUnit4::class)
class PetColorPersistenceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val name = "pet-color-${java.util.UUID.randomUUID()}.db"
    @get:Rule val schemas = MigrationTestHelper(instrumentation = instrumentation,
        databaseClass = GameDatabase::class, driver = BundledSQLiteDriver(), file = context.getDatabasePath(name))
    @After fun cleanup() { context.deleteDatabase(name) }

    @Test fun migrationRetainsIdentityBudgetRepeatedItemsSelectionAndDayJournal() = runBlocking {
        schemas.createDatabase(11).use { connection ->
            connection.execSQL("""
                INSERT INTO GAME_STATE (id, visual_state, selected_look, satiety, fatigue,
                    balance, planned_needs, planned_wants, planned_savings, planned_reserve, pet_name, pet_age)
                VALUES ('current', 'HAPPY', 'backend:scarf', 17, 30, 247, 10, 20, 30, 40, 'Тоша', 'SENIOR')
            """.trimIndent())
            connection.execSQL("INSERT INTO GOAL VALUES ('goal', 'Goal', 'Description')")
            connection.execSQL("INSERT INTO GOAL_SELECTION VALUES ('current', 'goal')")
            connection.execSQL("INSERT INTO ITEM VALUES ('map', 'Map', 'Description', 'STORY', 24)")
            connection.execSQL("INSERT INTO OWNED_ITEM VALUES ('second', 'current', 0, 'map')")
            connection.execSQL("INSERT INTO OWNED_ITEM VALUES ('first', 'current', 1, 'map')")
            connection.execSQL("INSERT INTO MINI_GAME_COMPLETION VALUES ('receipt', 'current')")
            connection.execSQL("INSERT INTO ENGINE_STATE VALUES ('current', 'rules', 42, 3, 'FINISHED', 2, 0, 1, 3, 255, 5)")
            connection.execSQL("INSERT INTO DAY_JOURNAL VALUES ('meal', 'current', 0, 'MEAL', 'basic-v1', -5, 0)")
        }
        schemas.runMigrationsAndValidate(12, listOf(MIGRATION_11_12)).use { connection ->
            connection.prepare("PRAGMA foreign_key_check").use { assertFalse(it.step()) }
        }
        var database = GameDatabase.open(context, name)
        try {
            var games = RoomGameRepository(database)
            val saved = games.read()!!
            assertEquals(PetColor.COPPER, saved.pet.color)
            assertEquals("Тоша", saved.pet.name)
            assertEquals(PetAge.SENIOR, saved.pet.age)
            assertEquals(PetVisualState.HAPPY, saved.pet.visualState)
            assertEquals("backend:scarf", saved.pet.selectedLookId)
            assertEquals(247L, saved.economy.balance)
            assertEquals(listOf(10L, 20L, 30L, 40L), saved.economy.plan.let { listOf(it.needs, it.wants, it.savings, it.reserve) })
            assertEquals(17, saved.satiety)
            assertEquals(30, saved.fatigue)
            assertEquals("goal", saved.selectedGoalId)
            assertEquals(listOf("second", "first"), saved.ownedItems.map { it.id })
            assertEquals(setOf("receipt"), saved.completedMiniGames)
            assertEquals(42L, saved.engine!!.revision)
            assertEquals(3, saved.engine!!.nextMorningEnergy)
            assertEquals(5, saved.engine!!.openingEnergy)
            assertEquals(-5L, saved.engine!!.journal.single().moneyDelta)
            for (color in PetColor.entries) {
                val colored = games.update { it.copy(pet = it.pet.copy(color = color)) }
                assertEquals(saved.copy(pet = saved.pet.copy(color = color)), colored)
                database.close()
                database = GameDatabase.open(context, name)
                games = RoomGameRepository(database)
                assertEquals(colored, games.observe().first())
                assertEquals(colored, games.initializeIfAbsent(createInitialGameState()))
            }
            val before = games.read()!!
            try {
                games.update { it.copy(pet = it.pet.copy(color = PetColor.COPPER),
                    ownedItems = it.ownedItems + OwnedItem("invalid", "missing")) }
                fail("Failed aggregate write must roll back its color too")
            } catch (_: androidx.sqlite.SQLiteException) { }
            assertEquals(before, games.read())
        } finally { database.close() }
    }
}
