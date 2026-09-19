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
import ru.nksk.lctapp.data.game.local.MIGRATION_5_6
import ru.nksk.lctapp.domain.minigame.MiniGameKind

@RunWith(AndroidJUnit4::class)
class HungerMigrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val name = "hunger-${java.util.UUID.randomUUID()}.db"
    @get:Rule val schemas = MigrationTestHelper(
        instrumentation = instrumentation, databaseClass = GameDatabase::class,
        driver = BundledSQLiteDriver(), file = context.getDatabasePath(name),
    )
    @After fun cleanup() { context.deleteDatabase(name) }

    @Test fun mergePreservesBothContributionsAndCompletionReceiptsAcrossReopen() = runBlocking {
        schemas.createDatabase(5).use { connection ->
            connection.execSQL("""
                INSERT INTO GAME_STATE (id, visual_state, selected_look, satiety, fatigue, hunger,
                    balance, planned_needs, planned_wants, planned_savings, planned_reserve)
                VALUES ('current', 'NORMAL', 'BACKPACK', 17, 30, 20, 247, 10, 20, 30, 40)
            """.trimIndent())
            connection.execSQL("INSERT INTO MINI_GAME_COMPLETION VALUES ('already-applied', 'current')")
            connection.execSQL("INSERT INTO ITEM VALUES ('map', 'Map', 'Description', 'STORY', 15)")
            connection.execSQL("INSERT INTO OWNED_ITEM VALUES ('second', 'current', 0, 'map')")
            connection.execSQL("INSERT INTO OWNED_ITEM VALUES ('first', 'current', 1, 'map')")
        }
        schemas.runMigrationsAndValidate(6, listOf(MIGRATION_5_6)).use { connection ->
            connection.prepare("PRAGMA table_info(GAME_STATE)").use { rows ->
                val columns = buildList { while (rows.step()) add(rows.getText(1)) }
                assertTrue("satiety" in columns)
                assertFalse("hunger" in columns)
            }
            connection.prepare("PRAGMA foreign_key_check").use { assertFalse(it.step()) }
        }
        val db = GameDatabase.open(context, name)
        try {
            val repository = RoomGameRepository(db)
            val saved = repository.read()!!
            assertEquals(37, saved.satiety)
            assertEquals(30, saved.fatigue)
            assertEquals(247L, saved.economy.balance)
            assertEquals(listOf("second", "first"), saved.ownedItems.map { it.id })
            assertEquals(setOf("already-applied"), saved.completedMiniGames)
            assertEquals(saved, repository.update { MiniGameKind.MEMORY.complete(it, "already-applied") })
            repository.update { MiniGameKind.MEMORY.complete(it, "new-attempt") }
        } finally { db.close() }
        val reopened = GameDatabase.open(context, name)
        try {
            val saved = RoomGameRepository(reopened).read()!!
            assertEquals(57, saved.satiety)
            assertEquals(60, saved.fatigue)
            assertEquals(2, saved.completedMiniGames.size)
        } finally { reopened.close() }
    }

    @Test fun mergeDoesNotClampOrResetExistingValues() = runBlocking {
        schemas.createDatabase(5).use { connection ->
            connection.execSQL("""
                INSERT INTO GAME_STATE (id, visual_state, selected_look, satiety, fatigue, hunger,
                    balance, planned_needs, planned_wants, planned_savings, planned_reserve)
                VALUES ('current', 'NORMAL', 'BACKPACK', 90, 0, 20, 100, 0, 0, 0, 0)
            """.trimIndent())
        }
        schemas.runMigrationsAndValidate(6, listOf(MIGRATION_5_6)).close()
        val db = GameDatabase.open(context, name)
        try {
            val saved = RoomGameRepository(db).read()!!
            assertEquals(110, saved.satiety)
            assertFalse(MiniGameKind.MEMORY.canPlay(saved))
        } finally { db.close() }
    }
}
