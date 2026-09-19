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
import ru.nksk.lctapp.data.game.local.MIGRATION_4_5
import ru.nksk.lctapp.domain.content.ItemCategory
import ru.nksk.lctapp.domain.content.ItemDefinition
import ru.nksk.lctapp.domain.content.StoryContent
import ru.nksk.lctapp.domain.game.OwnedItem

@RunWith(AndroidJUnit4::class)
class InventoryMigrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val name = "inventory-${java.util.UUID.randomUUID()}.db"
    @get:Rule val schemas = MigrationTestHelper(
        instrumentation = instrumentation, databaseClass = GameDatabase::class,
        driver = BundledSQLiteDriver(), file = context.getDatabasePath(name),
    )
    @After fun cleanup() { context.deleteDatabase(name) }

    @Test fun migrationPreservesPricesAsUnknownAndEveryOwnedOccurrence() = runBlocking {
        schemas.createDatabase(4).use { connection ->
            connection.execSQL("INSERT INTO ITEM VALUES ('map', 'Карта', 'Старинная карта')")
            connection.execSQL("""
                INSERT INTO GAME_STATE (id, visual_state, selected_look, satiety, fatigue, hunger,
                balance, planned_needs, planned_wants, planned_savings, planned_reserve)
                VALUES ('current', 'NORMAL', 'BACKPACK', 17, 29, 20, 247, 10, 20, 30, 40)
            """.trimIndent())
            connection.execSQL("INSERT INTO OWNED_ITEM VALUES ('owned-2', 'current', 0, 'map')")
            connection.execSQL("INSERT INTO OWNED_ITEM VALUES ('owned-1', 'current', 1, 'map')")
        }
        schemas.runMigrationsAndValidate(5, listOf(MIGRATION_4_5)).close()
        val database = GameDatabase.open(context, name)
        val accessory = ItemDefinition("cosmetic-42", "Бандана", "Красная бандана", ItemCategory.ACCESSORY, 40)
        try {
            val games = RoomGameRepository(database)
            val saved = games.read()!!
            assertEquals(listOf(OwnedItem("owned-2", "map"), OwnedItem("owned-1", "map")), saved.ownedItems)
            assertEquals(247L, saved.economy.balance)
            assertEquals(20, saved.hunger)
            assertEquals(17, saved.satiety)
            assertEquals(29, saved.fatigue)
            val content = RoomStoryContentRepository(database)
            assertEquals(ItemDefinition("map", "Карта", "Старинная карта"), content.read().items.single())
            content.install(StoryContent(items = listOf(accessory)))
            games.update { it.copy(ownedItems = it.ownedItems + OwnedItem("new", accessory.id)) }
        } finally { database.close() }
        val reopened = GameDatabase.open(context, name)
        try {
            assertEquals(accessory, RoomStoryContentRepository(reopened).read().items.single { it.id == accessory.id })
            assertEquals(3, RoomGameRepository(reopened).read()!!.ownedItems.size)
        } finally { reopened.close() }
    }
}
