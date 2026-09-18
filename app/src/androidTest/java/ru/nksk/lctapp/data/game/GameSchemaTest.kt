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
import ru.nksk.lctapp.domain.pet.PetLook
import ru.nksk.lctapp.domain.pet.PetVisualState

/** Version 1 baseline for future migrations; no previous released Room schema exists. */
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
            assertEquals(PetLook.BANDANA, state.pet.selectedLook)
        }
        schemas.runMigrationsAndValidate(1, emptyList()).close()
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
