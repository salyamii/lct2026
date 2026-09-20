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
import ru.nksk.lctapp.data.game.local.MIGRATION_13_14
import ru.nksk.lctapp.domain.game.OwnedItem
import ru.nksk.lctapp.domain.location.*

@RunWith(AndroidJUnit4::class)
class LocationMigrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val name = "location-${java.util.UUID.randomUUID()}.db"
    @get:Rule val schemas = MigrationTestHelper(instrumentation = instrumentation,
        databaseClass = GameDatabase::class, driver = BundledSQLiteDriver(), file = context.getDatabasePath(name))
    @After fun cleanup() { context.deleteDatabase(name) }

    @Test fun oldSaveAndRepeatedInventorySurviveAndScenePersistsAfterReopen() = runBlocking {
        schemas.createDatabase(13).use { connection ->
            connection.execSQL("INSERT INTO ITEM (id, name, description) VALUES ('map', 'Карта', 'Описание')")
            connection.execSQL("""
                INSERT INTO GAME_STATE (id, visual_state, selected_look, satiety, fatigue,
                balance, planned_needs, planned_wants, planned_savings, planned_reserve)
                VALUES ('current', 'NORMAL', 'PLAIN', 73, 29, 247, 10, 20, 30, 40)
            """.trimIndent())
            connection.execSQL("INSERT INTO OWNED_ITEM VALUES ('owned-2', 'current', 0, 'map')")
            connection.execSQL("INSERT INTO OWNED_ITEM VALUES ('owned-1', 'current', 1, 'map')")
        }
        schemas.runMigrationsAndValidate(14, listOf(MIGRATION_13_14)).close()
        val database = GameDatabase.open(context, name)
        val expected = try {
            val games = RoomGameRepository(database)
            val before = games.read()!!
            assertEquals(LocationScene(), before.locationScene)
            assertEquals(247L, before.economy.balance)
            assertEquals(73, before.satiety)
            assertEquals(29, before.fatigue)
            assertEquals(listOf(OwnedItem("owned-2", "map"), OwnedItem("owned-1", "map")), before.ownedItems)
            val controller = DefaultGameLocationController(games, AllLocationsAvailable)
            controller.selectLocation(GameLocation.PIER)
            controller.setLighting(LocationLighting.EVENING)
            before.copy(locationScene = LocationScene(GameLocation.PIER, LocationLighting.EVENING))
                .also { assertEquals(it, games.read()) }
        } finally { database.close() }
        val reopened = GameDatabase.open(context, name)
        try { assertEquals(expected, RoomGameRepository(reopened).read()) }
        finally { reopened.close() }
    }
}
