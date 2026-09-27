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
import ru.nksk.lctapp.data.game.local.MIGRATION_15_16
import ru.nksk.lctapp.domain.economy.*
import ru.nksk.lctapp.domain.game.OwnedItem

@RunWith(AndroidJUnit4::class)
class EconomyMigrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val name = "economy-${java.util.UUID.randomUUID()}.db"
    @get:Rule val schemas = MigrationTestHelper(instrumentation = instrumentation,
        databaseClass = GameDatabase::class, driver = BundledSQLiteDriver(), file = context.getDatabasePath(name))
    @After fun cleanup() { context.deleteDatabase(name) }

    @Test fun zeroBalanceIsToppedUpOnce() = migrate(0)
    @Test fun smallBalanceIsToppedUpOnce() = migrate(20)
    @Test fun minimumBalanceIsNotIncreased() = migrate(35)
    @Test fun largerBalanceIsEntirelyUnallocated() = migrate(140)

    private fun migrate(oldBalance: Long) = runBlocking {
        schemas.createDatabase(15).use { connection ->
            connection.execSQL("INSERT INTO ITEM (id, name, description) VALUES ('map', 'Карта', 'Описание')")
            connection.execSQL("""
                INSERT INTO GAME_STATE (id, visual_state, selected_look, satiety, fatigue,
                    balance, planned_needs, planned_wants, planned_savings, planned_reserve,
                    pet_name, pet_color, location_id, location_lighting)
                VALUES ('current', 'NORMAL', 'PLAIN', 73, 29, $oldBalance, 100, 200, 300, 400,
                    'Лис', 'SAND', 'city', 'EVENING')
            """.trimIndent())
            connection.execSQL("INSERT INTO OWNED_ITEM VALUES ('second', 'current', 0, 'map')")
            connection.execSQL("INSERT INTO OWNED_ITEM VALUES ('first', 'current', 1, 'map')")
            connection.execSQL("INSERT INTO EVENT (id, type, title, description, money_delta_on_start) VALUES ('event', 'RANDOM', 'Event', '', -2)")
            connection.execSQL("UPDATE GAME_STATE SET active_event_id = 'event' WHERE id = 'current'")
            connection.execSQL("INSERT INTO ENGINE_STATE (game_state_id, rules_id, revision, day, phase, steps, energy, ate_today, opening_balance, opening_energy) VALUES ('current', 'rules', 42, 3, 'RUNNING', 2, 4, 1, 150, 5)")
            connection.execSQL("INSERT INTO ENGINE_EVENT (id, game_state_id, position, event_id, status) VALUES ('occurrence', 'current', 0, 'event', 'ACTIVE')")
            connection.execSQL("INSERT INTO DAY_JOURNAL VALUES ('receipt', 'current', 0, 'EVENT_START', 'event', -2, 0)")
        }
        schemas.runMigrationsAndValidate(16, listOf(MIGRATION_15_16)).close()
        val db = GameDatabase.open(context, name)
        val saved = try {
            val repository = RoomGameRepository(db)
            val migrated = repository.read()!!
            assertEquals(maxOf(oldBalance, 35), migrated.economy.unallocated)
            assertEquals(maxOf(oldBalance, 35), migrated.economy.balance)
            assertEquals(BudgetPlan(0, 0, 0, 0), migrated.economy.plan)
            assertEquals(BudgetPlanning("migration-16-current", BudgetPlanningReason.MIGRATION,
                BudgetPlanningStage.ALLOCATION, 0, 0, draft = BudgetPlan(0, 0, 0, 0),
                baseAmount = maxOf(oldBalance, 35)), migrated.economy.planning)
            assertEquals(73, migrated.satiety)
            assertEquals(29, migrated.fatigue)
            assertEquals(42L, migrated.engine!!.revision)
            assertEquals(3, migrated.engine!!.day)
            assertEquals("occurrence", migrated.engine!!.currentEvent!!.id)
            assertEquals(-2L, migrated.engine!!.journal.single().moneyDelta)
            assertEquals(150L, migrated.engine!!.openingBalance)
            assertEquals(maxOf(35 - oldBalance, 0), migrated.engine!!.balanceAdjustment)
            assertEquals("Лис", migrated.pet.name)
            assertEquals(listOf(OwnedItem("second", "map"), OwnedItem("first", "map")), migrated.ownedItems)
            repository.update { it.copy(economy = it.economy.copy(
                plan = BudgetPlan(35, 0, 0, 0), unallocated = it.economy.unallocated - 35,
                planning = it.economy.planning!!.copy(revision = 1),
            )) }
        } finally { db.close() }
        val reopened = GameDatabase.open(context, name)
        try { assertEquals(saved, RoomGameRepository(reopened).read()) }
        finally { reopened.close() }
    }
}
