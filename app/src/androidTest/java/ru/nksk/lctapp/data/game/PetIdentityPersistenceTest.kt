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
import ru.nksk.lctapp.data.game.local.MIGRATION_7_8
import ru.nksk.lctapp.data.game.local.MIGRATION_8_9
import ru.nksk.lctapp.domain.pet.PetAge
import ru.nksk.lctapp.domain.pet.PetDefaults
import ru.nksk.lctapp.domain.pet.PetVisualState
import ru.nksk.lctapp.domain.pet.renderPetText

@RunWith(AndroidJUnit4::class)
class PetIdentityPersistenceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val name = "pet-identity-${java.util.UUID.randomUUID()}.db"
    @get:Rule val schemas = MigrationTestHelper(instrumentation = instrumentation,
        databaseClass = GameDatabase::class, driver = BundledSQLiteDriver(), file = context.getDatabasePath(name))
    @After fun cleanup() { context.deleteDatabase(name) }

    @Test fun petInitializationAndLiteralNameTemplatesWorkOnAndroid() {
        val initial = createInitialGameState()
        assertEquals(PetDefaults.FOX_NAME, initial.pet.name)
        assertEquals("PLAIN", initial.pet.selectedLookId)
        val pet = initial.pet.copy(name = "Тоша ${'$'}1 {petName}")
        assertEquals("${pet.name} готов. ${pet.name} рад.",
            renderPetText("{petName} готов. Рыжик рад.", pet.name))
    }

    @Test fun migrationPreservesProgressAndRenamedPetSurvivesReopenAndInitialization() = runBlocking {
        schemas.createDatabase(7).use { connection ->
            connection.execSQL("""
                INSERT INTO GAME_STATE (id, visual_state, selected_look, satiety, fatigue,
                    balance, planned_needs, planned_wants, planned_savings, planned_reserve)
                VALUES ('current', 'HAPPY', 'custom-look', 17, 30, 247, 10, 20, 30, 40)
            """.trimIndent())
            connection.execSQL("INSERT INTO GOAL VALUES ('goal', 'Goal', 'Description')")
            connection.execSQL("INSERT INTO GOAL_SELECTION VALUES ('current', 'goal')")
            connection.execSQL("INSERT INTO ITEM VALUES ('map', 'Map', 'Description', 'STORY', 24)")
            connection.execSQL("INSERT INTO OWNED_ITEM VALUES ('second', 'current', 0, 'map')")
            connection.execSQL("INSERT INTO OWNED_ITEM VALUES ('first', 'current', 1, 'map')")
            connection.execSQL("INSERT INTO MINI_GAME_COMPLETION VALUES ('old-attempt', 'current')")
            connection.execSQL("INSERT INTO ENGINE_STATE VALUES ('current', 'rules', 42, 3, 'RUNNING', 2, 4, 1, 3, 255)")
        }
        schemas.runMigrationsAndValidate(8, listOf(MIGRATION_7_8)).use { connection ->
            connection.prepare("PRAGMA foreign_key_check").use { assertFalse(it.step()) }
        }
        var database = GameDatabase.open(context, name)
        try {
            var games = RoomGameRepository(database)
            val migrated = games.read()!!
            assertEquals(PetDefaults.FOX_NAME, migrated.pet.name)
            assertEquals(PetAge.CUB, migrated.pet.age)
            assertEquals("custom-look", migrated.pet.selectedLookId)
            assertEquals(PetVisualState.HAPPY, migrated.pet.visualState)
            assertEquals("goal", migrated.selectedGoalId)
            assertEquals(247L, migrated.economy.balance)
            assertEquals(17, migrated.satiety)
            assertEquals(30, migrated.fatigue)
            assertEquals(listOf(10L, 20L, 30L, 40L), migrated.economy.plan.let { listOf(it.needs, it.wants, it.savings, it.reserve) })
            assertEquals(listOf("second", "first"), migrated.ownedItems.map { it.id })
            assertEquals(setOf("old-attempt"), migrated.completedMiniGames)
            assertEquals(42L, migrated.engine!!.revision)
            assertEquals(3, migrated.engine!!.nextMorningEnergy)
            val renamed = games.update { it.copy(pet = it.pet.copy(name = "Лис ${'$'}1 🦊", age = PetAge.ADULT)) }
            assertEquals(migrated.copy(pet = renamed.pet), renamed)
            assertEquals(renamed, games.observe().first())
            database.close()
            database = GameDatabase.open(context, name)
            games = RoomGameRepository(database)
            assertEquals(renamed, games.read())
            assertEquals(renamed, games.initializeIfAbsent(createInitialGameState()))
            assertEquals(renamed, games.read())
        } finally { database.close() }
    }

    @Test fun versionEightCorrectsTheCubStarterLookWithoutLosingProgressOrOtherLooks() = runBlocking {
        schemas.createDatabase(8).use { connection ->
            for ((id, age, look) in listOf(Triple("current", "CUB", "BACKPACK"),
                Triple("custom", "CUB", "backend:hat"), Triple("older", "TEEN", "BACKPACK"))) {
                connection.execSQL("""
                    INSERT INTO GAME_STATE (id, visual_state, selected_look, satiety, fatigue,
                        balance, planned_needs, planned_wants, planned_savings, planned_reserve, pet_name, pet_age)
                    VALUES ('$id', 'HAPPY', '$look', 17, 30, 247, 10, 20, 30, 40, 'Тоша', '$age')
                """.trimIndent())
            }
            connection.execSQL("INSERT INTO GOAL VALUES ('goal', 'Goal', 'Description')")
            connection.execSQL("INSERT INTO GOAL_SELECTION VALUES ('current', 'goal')")
            connection.execSQL("INSERT INTO ITEM VALUES ('bag', 'Bag', 'Description', 'ACCESSORY', 24)")
            connection.execSQL("INSERT INTO OWNED_ITEM VALUES ('second', 'current', 0, 'bag')")
            connection.execSQL("INSERT INTO OWNED_ITEM VALUES ('first', 'current', 1, 'bag')")
            connection.execSQL("INSERT INTO MINI_GAME_COMPLETION VALUES ('old-attempt', 'current')")
            connection.execSQL("INSERT INTO ENGINE_STATE VALUES ('current', 'rules', 42, 3, 'RUNNING', 2, 4, 1, 3, 255)")
        }
        schemas.runMigrationsAndValidate(9, listOf(MIGRATION_8_9)).use { connection ->
            val looks = mutableMapOf<String, String>()
            connection.prepare("SELECT id, selected_look FROM GAME_STATE").use {
                while (it.step()) looks[it.getText(0)] = it.getText(1)
            }
            assertEquals(mapOf("current" to "PLAIN", "custom" to "backend:hat", "older" to "BACKPACK"), looks)
            connection.prepare("PRAGMA foreign_key_check").use { assertFalse(it.step()) }
        }
        val database = GameDatabase.open(context, name)
        val saved = try {
            val games = RoomGameRepository(database)
            games.read()!!.also {
                assertEquals("PLAIN", it.pet.selectedLookId)
                assertEquals("Тоша", it.pet.name)
                assertEquals(PetAge.CUB, it.pet.age)
                assertEquals(PetVisualState.HAPPY, it.pet.visualState)
                assertEquals(247L, it.economy.balance)
                assertEquals(17, it.satiety)
                assertEquals(30, it.fatigue)
                assertEquals("goal", it.selectedGoalId)
                assertEquals(listOf("second", "first"), it.ownedItems.map { item -> item.id })
                assertEquals(setOf("old-attempt"), it.completedMiniGames)
                assertEquals(42L, it.engine!!.revision)
                assertEquals(2, it.engine!!.steps)
                assertEquals(it, games.initializeIfAbsent(createInitialGameState()))
            }
        } finally { database.close() }
        val reopened = GameDatabase.open(context, name)
        try { assertEquals(saved, RoomGameRepository(reopened).read()) } finally { reopened.close() }
    }

    @Test fun unknownStoredAgeIsAnErrorInsteadOfReplacingAnExistingPet() = runBlocking {
        schemas.createDatabase(8).use { connection ->
            connection.execSQL("""
                INSERT INTO GAME_STATE (id, visual_state, selected_look, satiety, fatigue,
                    balance, planned_needs, planned_wants, planned_savings, planned_reserve, pet_name, pet_age)
                VALUES ('current', 'NORMAL', 'PLAIN', 0, 0, 247, 10, 20, 30, 40, 'Тоша', 'UNKNOWN')
            """.trimIndent())
        }
        val database = GameDatabase.open(context, name)
        try {
            try { RoomGameRepository(database).read(); fail("Unknown age must not become CUB") }
            catch (_: IllegalArgumentException) { }
        } finally { database.close() }
    }
}
