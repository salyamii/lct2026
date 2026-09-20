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
import ru.nksk.lctapp.data.game.content.bundledGameCatalog
import ru.nksk.lctapp.data.game.local.GameDatabase
import ru.nksk.lctapp.data.game.local.MIGRATION_9_10
import ru.nksk.lctapp.domain.engine.CompletedGoalProject
import ru.nksk.lctapp.domain.story.StoryDecision

@RunWith(AndroidJUnit4::class)
class StoryProjectPersistenceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val name = "story-project-${java.util.UUID.randomUUID()}.db"
    @get:Rule val schemas = MigrationTestHelper(instrumentation = instrumentation,
        databaseClass = GameDatabase::class, driver = BundledSQLiteDriver(), file = context.getDatabasePath(name))
    @After fun cleanup() { context.deleteDatabase(name) }

    @Test fun migrationFromNineAddsNoAchievementsAndPreservesExistingSave() = runBlocking {
        schemas.createDatabase(9).use { connection ->
            connection.execSQL("""
                INSERT INTO GAME_STATE (id, visual_state, selected_look, satiety, fatigue,
                    balance, planned_needs, planned_wants, planned_savings, planned_reserve, pet_name, pet_age)
                VALUES ('current', 'HAPPY', 'custom-look', 17, 30, 247, 10, 20, 30, 40, 'Тоша', 'CUB')
            """.trimIndent())
            connection.execSQL("INSERT INTO GOAL VALUES ('goal', 'Goal', 'Description')")
            connection.execSQL("INSERT INTO GOAL_SELECTION VALUES ('current', 'goal')")
            connection.execSQL("INSERT INTO ITEM VALUES ('map', 'Map', 'Description', 'STORY', 24)")
            connection.execSQL("INSERT INTO OWNED_ITEM VALUES ('second', 'current', 0, 'map')")
            connection.execSQL("INSERT INTO OWNED_ITEM VALUES ('first', 'current', 1, 'map')")
            connection.execSQL("INSERT INTO MINI_GAME_COMPLETION VALUES ('old-attempt', 'current')")
            connection.execSQL("INSERT INTO ENGINE_STATE VALUES ('current', 'rules', 42, 3, 'RUNNING', 2, 4, 1, 3, 255)")
        }
        schemas.runMigrationsAndValidate(10, listOf(MIGRATION_9_10)).use { connection ->
            connection.prepare("PRAGMA foreign_key_check").use { assertFalse(it.step()) }
        }
        var database = GameDatabase.open(context, name)
        try {
            val saved = RoomGameRepository(database).read()!!
            assertTrue(saved.completedGoalProjects.isEmpty())
            assertEquals("goal", saved.selectedGoalId)
            assertEquals("Тоша", saved.pet.name)
            assertEquals("custom-look", saved.pet.selectedLookId)
            assertEquals(247L, saved.economy.balance)
            assertEquals(listOf(10L, 20L, 30L, 40L), saved.economy.plan.let { listOf(it.needs, it.wants, it.savings, it.reserve) })
            assertEquals(17, saved.satiety)
            assertEquals(30, saved.fatigue)
            assertEquals(listOf("second", "first"), saved.ownedItems.map { it.id })
            assertEquals(setOf("old-attempt"), saved.completedMiniGames)
            assertEquals(42L, saved.engine!!.revision)
            assertEquals(3, saved.engine!!.nextMorningEnergy)
            database.close()
            database = GameDatabase.open(context, name)
            assertEquals(saved, RoomGameRepository(database).read())
        } finally { database.close() }
    }

    @Test fun finaleProjectAssociationsSurviveReopenAndInvalidGoalRollsBackTheAggregate() = runBlocking {
        val catalog = bundledGameCatalog()
        var database = GameDatabase.open(context, name)
        try {
            RoomStoryContentRepository(database).install(catalog.content)
            var games = RoomGameRepository(database)
            games.initializeIfAbsent(createInitialGameState())
            val saved = games.update { initial ->
                initial.copy(story = initial.story.copy(decisions = listOf(
                    StoryDecision("second-name", "campaign-choice-v1:G1.12:continue"),
                    StoryDecision("first-name", "campaign-choice-v1:G2.12:continue"),
                )), completedGoalProjects = listOf(
                    CompletedGoalProject(catalog.goals[1].goalId, "second-name"),
                    CompletedGoalProject(catalog.goals[0].goalId, "first-name"),
                ))
            }
            assertEquals(saved, games.observe().first())
            database.close()
            database = GameDatabase.open(context, name)
            games = RoomGameRepository(database)
            assertEquals(saved, games.read())
            assertEquals(saved, games.initializeIfAbsent(createInitialGameState()))
            try {
                games.update { it.copy(economy = it.economy.copy(balance = 0),
                    completedGoalProjects = it.completedGoalProjects.map { project -> project.copy(goalId = "missing-goal") }) }
                fail("Unknown project must roll back balance and all children")
            } catch (_: androidx.sqlite.SQLiteException) { }
            assertEquals(saved, games.read())
            assertEquals(saved, games.observe().first())
        } finally { database.close() }
    }
}
