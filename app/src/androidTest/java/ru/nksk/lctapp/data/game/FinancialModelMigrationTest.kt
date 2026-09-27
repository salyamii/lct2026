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
import ru.nksk.lctapp.data.game.local.MIGRATION_16_17
import ru.nksk.lctapp.domain.economy.BudgetPlan
import ru.nksk.lctapp.domain.economy.BudgetPlanningReason
import ru.nksk.lctapp.domain.game.OwnedItem
import ru.nksk.lctapp.domain.history.AuditType

@RunWith(AndroidJUnit4::class)
class FinancialModelMigrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val name = "finance-17-${java.util.UUID.randomUUID()}.db"
    @get:Rule val schemas = MigrationTestHelper(instrumentation = instrumentation,
        databaseClass = GameDatabase::class, driver = BundledSQLiteDriver(), file = context.getDatabasePath(name))
    @After fun cleanup() { context.deleteDatabase(name) }

    @Test fun realSavingsAndActiveDraftArePreservedWithoutTopupOrInventedConfirmation() = runBlocking {
        schemas.createDatabase(16).use { connection ->
            connection.execSQL("INSERT INTO ITEM (id, name, description) VALUES ('map', 'Карта', '')")
            connection.execSQL("INSERT INTO GOAL VALUES ('stars', 'Звёзды', '')")
            connection.execSQL("""
                INSERT INTO GAME_STATE(id, visual_state, selected_look, satiety, fatigue,
                    unallocated, needs, wants, savings, reserve, pet_name, pet_color)
                VALUES ('current', 'NORMAL', 'PLAIN', 73, 29, 3, 5, 7, 19, 2, 'Лис', 'SAND')
            """.trimIndent())
            connection.execSQL("INSERT INTO OWNED_ITEM VALUES ('second', 'current', 0, 'map')")
            connection.execSQL("INSERT INTO OWNED_ITEM VALUES ('first', 'current', 1, 'map')")
            connection.execSQL("INSERT INTO GOAL_SELECTION VALUES ('current', 'stars')")
            connection.execSQL("INSERT INTO BUDGET_PLANNING VALUES ('current', 'draft-original', 'MANUAL', 'ALLOCATION', 0, 8)")
            connection.execSQL("""
                INSERT INTO ENGINE_STATE(game_state_id, rules_id, revision, day, phase, steps, energy,
                    ate_today, opening_balance, opening_energy, balance_adjustment)
                VALUES ('current', 'rules', 42, 3, 'RUNNING', 2, 4, 1, 50, 5, 7)
            """.trimIndent())
            connection.execSQL("INSERT INTO DAY_JOURNAL VALUES ('receipt', 'current', 0, 'MEAL', 'food', -5, 0)")
        }
        schemas.runMigrationsAndValidate(17, listOf(MIGRATION_16_17)).close()
        val db = GameDatabase.open(context, name)
        val expected = try {
            val repository = RoomGameRepository(db)
            val saved = repository.read()!!
            assertEquals(17L, saved.economy.availableBalance)
            assertEquals(19L, saved.economy.savingsBalance)
            assertEquals(36L, saved.economy.balance)
            assertEquals(BudgetPlan(5, 7, 19, 2), saved.economy.plan)
            val planning = saved.economy.planning!!
            assertEquals("draft-original", planning.id)
            assertEquals(8L, planning.revision)
            assertEquals(BudgetPlanningReason.MANUAL, planning.reason)
            assertEquals(BudgetPlan(5, 7, 0, 2), planning.draft)
            assertEquals(17L, planning.baseAmount)
            assertEquals(42L, saved.engine!!.revision)
            assertEquals(50L, saved.engine!!.openingBalance)
            assertEquals(7L, saved.engine!!.balanceAdjustment)
            assertEquals(-5L, saved.engine!!.journal.single().moneyDelta)
            assertEquals(listOf(OwnedItem("second", "map"), OwnedItem("first", "map")), saved.ownedItems)
            assertTrue(saved.financial.plans.isEmpty())
            assertTrue(saved.financial.currentPeriod!!.imported)
            assertEquals("stars", saved.financial.currentPeriod!!.goalId)
            assertEquals(3, saved.financial.currentPeriod!!.startedDay)
            assertFalse(saved.financial.currentPeriod!!.independentlySaved)
            val upgradedHistory = repository.readHistory()
            assertEquals(listOf(AuditType.IMPORTED_BASELINE, AuditType.TECHNICAL_UPDATE), upgradedHistory.map { it.type })
            assertEquals(saved, upgradedHistory.last().before)
            assertEquals(saved, upgradedHistory.last().after)
            assertEquals(upgradedHistory, repository.exportSnapshot().history)
            saved
        } finally { db.close() }
        val reopened = GameDatabase.open(context, name)
        try { assertEquals(expected, RoomGameRepository(reopened).read()) } finally { reopened.close() }
    }
}
