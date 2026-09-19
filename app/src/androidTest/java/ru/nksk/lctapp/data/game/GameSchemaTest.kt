package ru.nksk.lctapp.data.game

import androidx.room3.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.nksk.lctapp.app.createInitialGameState
import ru.nksk.lctapp.data.game.local.GameDatabase
import ru.nksk.lctapp.data.game.local.MIGRATION_1_2
import ru.nksk.lctapp.data.game.local.MIGRATION_5_6
import ru.nksk.lctapp.data.game.local.MIGRATION_4_5
import ru.nksk.lctapp.data.game.local.MIGRATION_3_4
import ru.nksk.lctapp.data.game.local.MIGRATION_2_3
import ru.nksk.lctapp.domain.pet.PetVisualState

/** Upgrade from the exported v1 baseline without losing existing values or relationships. */
@RunWith(AndroidJUnit4::class)
class GameSchemaTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val name = "schema-${java.util.UUID.randomUUID()}.db"

    @get:Rule val schemas = MigrationTestHelper(
        instrumentation = instrumentation,
        databaseClass = GameDatabase::class,
        driver = BundledSQLiteDriver(),
        file = context.getDatabasePath(name),
    )

    @After fun deleteTestFile() { context.deleteDatabase(name) }

    @Test fun exportedVersionOneOpensWithRealBuilderAndKeepsValues() = runBlocking {
        schemas.createDatabase(1).use { connection ->
            connection.execSQL("""
                INSERT INTO GAME_STATE VALUES
                ('current', 'HUNGRY', 'BANDANA', 17, 29, 247, 10, 20, 30, 40, NULL, NULL, NULL)
            """.trimIndent())
        }
        withDatabase { db ->
            val state = RoomGameRepository(db).read()!!
            assertEquals(247L, state.economy.balance)
            assertEquals(17, state.satiety)
            assertEquals(29, state.fatigue)
            assertEquals(30L, state.economy.plan.savings)
            assertEquals(PetVisualState.HUNGRY, state.pet.visualState)
            assertEquals("BANDANA", state.pet.selectedLookId)
        }
        schemas.runMigrationsAndValidate(6, listOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)).close()
    }

    @Test fun migrationPreservesEveryV1TableAndRepeatedOccurrences() = runBlocking {
        val statements = listOf(
            "INSERT INTO GOAL VALUES ('goal', 'Goal', 'Description')",
            "INSERT INTO ITEM VALUES ('rope', 'Rope', 'Both decorative and useful')",
            "INSERT INTO CHAPTER VALUES ('chapter', 'Chapter', 'goal')",
            "INSERT INTO GAME_DAY VALUES ('day', 'chapter', 1)",
            "INSERT INTO EVENT VALUES ('event', 'RANDOM', 'Event', 'Description', 17, 29, 'WORRIED', -5, NULL, NULL)",
            "INSERT INTO DAY_EVENT VALUES ('schedule', 'day', 4, 'event')",
            "INSERT INTO EVENT_CHOICE VALUES ('choice', 'event', 0, 'Choice', -3, NULL, 'HAPPY', 'NEUTRAL')",
            "INSERT INTO GOAL_REQUIRED_ITEM VALUES ('goal', 'rope')",
            "INSERT INTO EVENT_ITEM_EFFECT VALUES ('effect', 'event', 0, 'rope', 'ADD')",
            "INSERT INTO CHOICE_ITEM_EFFECT VALUES ('choice-effect', 'choice', 0, 'rope', 'REMOVE')",
            "INSERT INTO GAME_STATE VALUES ('current', 'UPSET', 'HAT', 17, 29, 3000000000, 5, 6, 7, 8, 'day', 4, 'event')",
            "INSERT INTO PLAYER_DECISION VALUES ('decision-2', 'current', 0, 'choice')",
            "INSERT INTO PLAYER_DECISION VALUES ('decision-1', 'current', 1, 'choice')",
            "INSERT INTO OWNED_ITEM VALUES ('item-2', 'current', 0, 'rope')",
            "INSERT INTO OWNED_ITEM VALUES ('item-1', 'current', 1, 'rope')",
        )
        val tables = listOf("GOAL", "ITEM", "CHAPTER", "GAME_DAY", "EVENT", "DAY_EVENT", "EVENT_CHOICE",
            "GOAL_REQUIRED_ITEM", "EVENT_ITEM_EFFECT", "CHOICE_ITEM_EFFECT", "GAME_STATE", "PLAYER_DECISION", "OWNED_ITEM")
        val before = schemas.createDatabase(1).use { connection ->
            statements.forEach { connection.execSQL(it) }
            tables.associateWith { dump(connection, it) }
        }
        schemas.runMigrationsAndValidate(2, listOf(MIGRATION_1_2)).use { connection ->
            tables.forEach { assertEquals(it, before[it], dump(connection, it)) }
            assertTrue(dump(connection, "ENGINE_STATE").isEmpty())
        }
        withDatabase { database ->
            val state = RoomGameRepository(database).read()!!
            assertNull(state.engine)
            assertEquals(3_000_000_000L, state.economy.balance)
            assertEquals(listOf("item-2", "item-1"), state.ownedItems.map { it.id })
            assertEquals(listOf("decision-2", "decision-1"), state.story.decisions.map { it.id })
        }
    }

    @Test fun engineVersionTwoMigrationPreservesRuntimeAndOccurrences() = runBlocking {
        val tables = listOf("GAME_STATE", "EVENT", "ENGINE_STATE", "ENGINE_DEED", "ENGINE_EVENT")
        val before = schemas.createDatabase(2).use { connection ->
            connection.execSQL("INSERT INTO GAME_STATE VALUES ('current', 'HAPPY', 'HAT', 17, 29, 247, 10, 20, 30, 40, NULL, NULL, NULL)")
            connection.execSQL("INSERT INTO EVENT VALUES ('event', 'EARNING', 'Work', 'Description', NULL, NULL, NULL, 0, NULL, NULL)")
            connection.execSQL("INSERT INTO ENGINE_STATE VALUES ('current', 'rules', 9, 3, 'RUNNING', 2, 4, 1, NULL, 300)")
            connection.execSQL("INSERT INTO ENGINE_DEED VALUES ('offer', 'current', 0, 'event', 5, 0)")
            connection.execSQL("INSERT INTO ENGINE_EVENT VALUES ('occurrence', 'current', 0, NULL, 'RESULT', 'offer')")
            tables.associateWith { dump(connection, it) }
        }
        schemas.runMigrationsAndValidate(3, listOf(MIGRATION_2_3)).use { connection ->
            tables.forEach { assertEquals(it, before[it], dump(connection, it)) }
            assertTrue(dump(connection, "LEGACY_EXPENSE_STATE").isEmpty())
            connection.prepare("PRAGMA foreign_key_check").use { assertFalse(it.step()) }
        }
    }

    @Test fun expensesBranchVersionTwoOpensAndPreservesItsSaveAndContent() = runBlocking {
        context.getDatabasePath(name).parentFile!!.mkdirs()
        BundledSQLiteDriver().open(context.getDatabasePath(name).absolutePath).use { connection ->
            val sql = instrumentation.context.assets.open("legacy-expenses-v2.sql")
                .bufferedReader().use { it.readText() }
            sql.split(';').filter { it.isNotBlank() }.forEach { connection.execSQL(it) }
            connection.execSQL("INSERT INTO ARTWORK VALUES ('art', 'old_art')")
            connection.execSQL("INSERT INTO EFFORT_LEVEL VALUES ('effort', 'Medium', 2)")
            connection.execSQL("INSERT INTO EVENT VALUES ('event', 'RANDOM', 'Event', 'Description', NULL, NULL, NULL, 0, NULL, NULL)")
            connection.execSQL("INSERT INTO EVENT_CHOICE VALUES ('choice', 'event', 0, 'Choice', -3, NULL, NULL, 'NEUTRAL')")
            connection.execSQL("INSERT INTO EXPENSE VALUES ('event', 1, 'art', 'Needs', 'Footer')")
            connection.execSQL("INSERT INTO EXPENSE_CHOICE_RULE VALUES ('choice', 'PAY', 'effort')")
            connection.execSQL("INSERT INTO EVENT_ARTWORK_LAYER VALUES ('event', 0, 'art', 1, 2, 3, 4)")
            connection.execSQL("INSERT INTO GAME_STATE VALUES ('current', 'NORMAL', 'BACKPACK', 20, 0, 82, 1, 2, 3, 4, NULL, NULL, 'event', 2, 'CHOOSING', 7)")
            connection.execSQL("INSERT INTO PLAYER_DECISION VALUES ('decision', 'current', 0, 'choice')")
        }
        val retainedTables = listOf("ARTWORK", "EFFORT_LEVEL", "EXPENSE", "EXPENSE_CHOICE_RULE", "EVENT_ARTWORK_LAYER", "EVENT", "EVENT_CHOICE", "PLAYER_DECISION")
        val before = BundledSQLiteDriver().open(context.getDatabasePath(name).absolutePath).use { connection ->
            retainedTables.associateWith { dump(connection, it) }
        }
        withDatabase { database ->
            val repository = RoomGameRepository(database)
            val saved = repository.initializeIfAbsent(createInitialGameState())
            assertEquals(82L, saved.economy.balance)
            assertEquals(20, saved.satiety)
            assertEquals("event", saved.story.activeEventId)
            assertNull(saved.engine)
            repository.update { it.copy(satiety = 21) }
        }
        BundledSQLiteDriver().open(context.getDatabasePath(name).absolutePath).use { connection ->
            retainedTables.forEach { assertEquals(it, before[it], dump(connection, it)) }
            assertEquals(listOf(listOf("current", "2", "CHOOSING", "7")), dump(connection, "LEGACY_EXPENSE_STATE"))
            assertTrue(dump(connection, "ENGINE_STATE").isEmpty())
            connection.prepare("PRAGMA foreign_key_check").use { assertFalse(it.step()) }
        }
        withDatabase { database ->
            assertEquals(21, RoomGameRepository(database).read()!!.satiety)
        }
    }

    @Test fun versionThreeAddsHungerWithoutReinterpretingExistingValues() = runBlocking {
        schemas.createDatabase(3).use { connection ->
            connection.execSQL("INSERT INTO GAME_STATE VALUES ('current', 'HAPPY', 'HAT', 17, 29, 247, 10, 20, 30, 40, NULL, NULL, NULL)")
        }
        schemas.runMigrationsAndValidate(4, listOf(MIGRATION_3_4)).use { connection ->
            connection.prepare("SELECT hunger, satiety, fatigue, balance FROM GAME_STATE").use { row ->
                assertTrue(row.step())
                assertEquals(0, row.getInt(0))
                assertEquals(17, row.getInt(1))
                assertEquals(29, row.getInt(2))
                assertEquals(247L, row.getLong(3))
            }
            assertTrue(dump(connection, "MINI_GAME_COMPLETION").isEmpty())
        }
        withDatabase { db ->
            RoomGameRepository(db).update {
                ru.nksk.lctapp.domain.minigame.MiniGameKind.MEMORY.complete(it, "persisted-attempt")
            }
        }
        withDatabase { db ->
            val repository = RoomGameRepository(db)
            val restored = repository.read()!!
            assertEquals(37, restored.satiety)
            assertEquals(59, restored.fatigue)
            assertEquals(setOf("persisted-attempt"), restored.completedMiniGames)
            assertEquals(restored, repository.update {
                ru.nksk.lctapp.domain.minigame.MiniGameKind.MEMORY.complete(it, "persisted-attempt")
            })
        }
    }

    private fun dump(connection: androidx.sqlite.SQLiteConnection, table: String): List<List<String?>> =
        connection.prepare("SELECT * FROM $table ORDER BY rowid").use { statement ->
            buildList {
                while (statement.step()) add((0 until statement.getColumnCount()).map {
                    if (statement.isNull(it)) null else statement.getText(it)
                })
            }
        }

    @Test fun unknownStoredCodeIsAnErrorAndInitializationDoesNotReplaceIt() = runBlocking {
        schemas.createDatabase(1).use { connection ->
            connection.execSQL("""
                INSERT INTO GAME_STATE VALUES
                ('current', 'FUTURE_STATE', 'HAT', 17, 29, 247, 10, 20, 30, 40, NULL, NULL, NULL)
            """.trimIndent())
        }
        withDatabase { db ->
            try {
                RoomGameRepository(db).initializeIfAbsent(createInitialGameState())
                fail("Unknown state must surface as an error")
            } catch (expected: IllegalArgumentException) {
                assertTrue(expected.message.orEmpty().contains("FUTURE_STATE"))
            }
        }
        BundledSQLiteDriver().open(context.getDatabasePath(name).absolutePath).use { connection ->
            connection.prepare("SELECT visual_state, balance FROM GAME_STATE").use { row ->
                assertTrue(row.step())
                assertEquals("FUTURE_STATE", row.getText(0))
                assertEquals(247L, row.getLong(1))
            }
        }
    }

    @Test fun unsupportedSchemaVersionFailsWithoutDestructiveFallback() = runBlocking {
        schemas.createDatabase(1).use { connection ->
            connection.execSQL("INSERT INTO ITEM (id, name, description) VALUES ('kept', 'Retained', 'Do not delete')")
            connection.execSQL("PRAGMA user_version = 99")
        }
        withDatabase { db ->
            try {
                RoomGameRepository(db).read()
                fail("Missing migration must fail")
            } catch (expected: IllegalStateException) {
                assertTrue(expected.message.orEmpty().contains("migration", ignoreCase = true))
            }
        }
        BundledSQLiteDriver().open(context.getDatabasePath(name).absolutePath).use { connection ->
            connection.prepare("SELECT name FROM ITEM WHERE id = 'kept'").use { row ->
                assertTrue(row.step())
                assertEquals("Retained", row.getText(0))
            }
        }
    }

    private suspend fun withDatabase(block: suspend (GameDatabase) -> Unit) {
        val database = GameDatabase.open(context, name)
        try { block(database) } finally { database.close() }
    }
}
