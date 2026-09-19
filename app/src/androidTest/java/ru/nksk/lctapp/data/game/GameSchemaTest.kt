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
        schemas.runMigrationsAndValidate(2, listOf(MIGRATION_1_2)).close()
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
            connection.execSQL("INSERT INTO ITEM VALUES ('kept', 'Retained', 'Do not delete')")
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
