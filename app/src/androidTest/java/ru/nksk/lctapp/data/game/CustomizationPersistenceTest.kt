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
import ru.nksk.lctapp.app.createInitialGameState
import ru.nksk.lctapp.data.game.local.GameDatabase
import ru.nksk.lctapp.data.game.local.MIGRATION_12_13
import ru.nksk.lctapp.data.game.local.MIGRATION_14_15
import ru.nksk.lctapp.data.game.content.bundledGameCatalog
import ru.nksk.lctapp.data.game.content.STARS_GOAL
import ru.nksk.lctapp.domain.engine.GameSession
import ru.nksk.lctapp.domain.onboarding.*
import ru.nksk.lctapp.domain.pet.*

@RunWith(AndroidJUnit4::class)
class CustomizationPersistenceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val name = "customization-${java.util.UUID.randomUUID()}.db"
    @get:Rule val schemas = MigrationTestHelper(
        instrumentation = instrumentation, databaseClass = GameDatabase::class,
        driver = BundledSQLiteDriver(), file = context.getDatabasePath(name),
    )
    @After fun cleanup() { context.deleteDatabase(name) }

    @Test fun migrationPreservesLegacySaveAndRepeatedItems() = runBlocking {
        schemas.createDatabase(12).use { connection ->
            connection.execSQL("""
                INSERT INTO GAME_STATE (id, visual_state, selected_look, satiety, fatigue,
                    balance, planned_needs, planned_wants, planned_savings, planned_reserve, pet_name, pet_age, pet_color)
                VALUES ('current', 'HAPPY', 'BACKPACK', 17, 30, 247, 10, 20, 30, 40, 'Искорка', 'ADULT', 'SAND')
            """.trimIndent())
            connection.execSQL("INSERT INTO MINI_GAME_COMPLETION VALUES ('done', 'current')")
            connection.execSQL("INSERT INTO ITEM VALUES ('map', 'Map', 'Description', 'STORY', 15)")
            connection.execSQL("INSERT INTO OWNED_ITEM VALUES ('second', 'current', 0, 'map')")
            connection.execSQL("INSERT INTO OWNED_ITEM VALUES ('first', 'current', 1, 'map')")
        }
        schemas.runMigrationsAndValidate(13, listOf(MIGRATION_12_13)).close()
        val db = GameDatabase.open(context, name)
        try {
            val saved = RoomGameRepository(db).read()!!
            assertNull(saved.pet.temperament)
            assertEquals("Искорка", saved.pet.name)
            assertEquals(PetAge.ADULT, saved.pet.age)
            assertEquals(PetColor.SAND, saved.pet.color)
            assertEquals(PetVisualState.HAPPY, saved.pet.visualState)
            assertEquals("BACKPACK", saved.pet.selectedLookId)
            assertEquals(247L, saved.economy.balance)
            assertEquals(17, saved.satiety)
            assertEquals(30, saved.fatigue)
            assertEquals(listOf("second", "first"), saved.ownedItems.map { it.id })
            assertEquals(setOf("done"), saved.completedMiniGames)
            assertNull(RoomOnboardingDraftRepository(db).read())
        } finally { db.close() }
    }

    @Test fun migrationFromEmptyV12CreatesCompleteDraftTable() = runBlocking {
        schemas.createDatabase(12).close()
        schemas.runMigrationsAndValidate(13, listOf(MIGRATION_12_13)).use { connection ->
            connection.execSQL("INSERT INTO ONBOARDING_DRAFT (id, name, temperament, fur) VALUES ('current', 'Искорка', 'Joyful', 'Sand')")
        }
        val db = GameDatabase.open(context, name)
        try {
            val draft = RoomOnboardingDraftRepository(db).read()!!
            assertEquals("Искорка", draft.profile.name)
            assertEquals(PetFur.Sand, draft.profile.fur)
            assertEquals(OnboardingStep.Profile, draft.step)
            assertEquals("BACKPACK", draft.accessoryId)
            assertNull(RoomGameRepository(db).read())
        } finally { db.close() }
    }

    @Test fun draftResumesAndCompletionCommitsProfileWithoutReplacingSave() = runBlocking {
        val profile = PetCustomization("Искорка", PetTemperament.Joyful, PetFur.Russet)
        var db = GameDatabase.open(context, name)
        try {
            RoomOnboardingDraftRepository(db).save(OnboardingDraft(profile, OnboardingStep.Introduction, "BANDANA"))
            assertNull(RoomGameRepository(db).read())
        } finally { db.close() }
        db = GameDatabase.open(context, name)
        val initial = createInitialGameState().copy(pet = profile.toPetState("BANDANA"))
        try {
            assertEquals(OnboardingDraft(profile, OnboardingStep.Introduction, "BANDANA"), RoomOnboardingDraftRepository(db).read())
            RoomGameRepository(db).initializeIfAbsent(initial)
            assertNull(RoomOnboardingDraftRepository(db).read())
            // Completion and late text edits cannot overwrite/recreate the now-completed onboarding.
            RoomGameRepository(db).initializeIfAbsent(createInitialGameState())
            RoomOnboardingDraftRepository(db).save(OnboardingDraft(PetCustomization(name = "Поздний ввод")))
            assertNull(RoomOnboardingDraftRepository(db).read())
        } finally { db.close() }
        db = GameDatabase.open(context, name)
        try {
            val games = RoomGameRepository(db)
            assertEquals(initial, games.read())
            games.update { it.copy(pet = it.pet.transitionTo(PetVisualState.HAPPY)) }
            assertEquals(profile.toPetState("BANDANA").transitionTo(PetVisualState.HAPPY), games.read()!!.pet)
        } finally { db.close() }
    }

    @Test fun goalBriefingDraftSurvivesReopenWithProfileAccessoryAndGoal() = runBlocking {
        val draft = OnboardingDraft(
            PetCustomization("Искорка", PetTemperament.Joyful, PetFur.Sand),
            OnboardingStep.GoalBriefing, "BANDANA", STARS_GOAL,
        )
        var db = GameDatabase.open(context, name)
        try {
            RoomOnboardingDraftRepository(db).save(draft)
        } finally { db.close() }
        db = GameDatabase.open(context, name)
        try {
            assertEquals(draft, RoomOnboardingDraftRepository(db).read())
            assertNull(RoomGameRepository(db).read())
        } finally { db.close() }
    }

    @Test fun oldIntroductionMigratesToGoalSelectionWithoutChangingProfile() = runBlocking {
        schemas.createDatabase(14).use { connection ->
            connection.execSQL("INSERT INTO ONBOARDING_DRAFT VALUES ('current', 'Искорка', 'Joyful', 'Sand', 'INTRODUCTION', 'BANDANA')")
        }
        schemas.runMigrationsAndValidate(15, listOf(MIGRATION_14_15)).close()
        val db = GameDatabase.open(context, name)
        try {
            assertEquals(OnboardingDraft(PetCustomization("Искорка", PetTemperament.Joyful, PetFur.Sand),
                OnboardingStep.GoalSelection, "BANDANA"), RoomOnboardingDraftRepository(db).read())
            assertNull(RoomGameRepository(db).read())
        } finally { db.close() }
    }

    @Test fun goalMigrationPreservesExistingGameAndRepeatedItems() = runBlocking {
        schemas.createDatabase(14).use { connection ->
            connection.execSQL("""
                INSERT INTO GAME_STATE (id, visual_state, selected_look, satiety, fatigue,
                    balance, planned_needs, planned_wants, planned_savings, planned_reserve, pet_name, pet_age, pet_color)
                VALUES ('current', 'HAPPY', 'BANDANA', 17, 30, 247, 10, 20, 30, 40, 'Искорка', 'ADULT', 'SAND')
            """.trimIndent())
            connection.execSQL("INSERT INTO GOAL VALUES ('old-goal', 'Old goal', 'Description')")
            connection.execSQL("INSERT INTO GOAL_SELECTION VALUES ('current', 'old-goal')")
            connection.execSQL("INSERT INTO ITEM VALUES ('map', 'Map', 'Description', 'STORY', 15)")
            connection.execSQL("INSERT INTO OWNED_ITEM VALUES ('second', 'current', 0, 'map')")
            connection.execSQL("INSERT INTO OWNED_ITEM VALUES ('first', 'current', 1, 'map')")
        }
        schemas.runMigrationsAndValidate(15, listOf(MIGRATION_14_15)).close()
        val db = GameDatabase.open(context, name)
        try {
            val saved = RoomGameRepository(db).read()!!
            assertEquals("old-goal", saved.selectedGoalId)
            assertEquals(247L, saved.economy.balance)
            assertEquals("Искорка", saved.pet.name)
            assertEquals(PetAge.ADULT, saved.pet.age)
            assertEquals(PetColor.SAND, saved.pet.color)
            assertEquals("BANDANA", saved.pet.selectedLookId)
            assertEquals(PetVisualState.HAPPY, saved.pet.visualState)
            assertEquals(17, saved.satiety)
            assertEquals(30, saved.fatigue)
            assertEquals(listOf("second", "first"), saved.ownedItems.map { it.id })
        } finally { db.close() }
    }

    @Test fun selectedGoalResumesAndCommitsWithProfileThenRemovesDraft() = runBlocking {
        val draft = OnboardingDraft(PetCustomization("Искорка", fur = PetFur.Sand),
            OnboardingStep.Introduction, "BANDANA", STARS_GOAL)
        var db = GameDatabase.open(context, name)
        try { RoomOnboardingDraftRepository(db).save(draft) } finally { db.close() }
        db = GameDatabase.open(context, name)
        try {
            assertEquals(draft, RoomOnboardingDraftRepository(db).read())
            val games = RoomGameRepository(db)
            val session = GameSession(games, RoomStoryContentRepository(db), bundledGameCatalog(), createInitialGameState())
            session.prepare(draft.profile.toPetState(draft.accessoryId), draft.goalId)
            assertNull(RoomOnboardingDraftRepository(db).read())
            assertEquals(STARS_GOAL, games.read()!!.selectedGoalId)
            assertNull(games.read()!!.engine)
        } finally { db.close() }
        db = GameDatabase.open(context, name)
        try {
            val saved = RoomGameRepository(db).read()!!
            assertEquals(STARS_GOAL, saved.selectedGoalId)
            assertEquals(draft.profile.toPetState("BANDANA"), saved.pet)
            assertNull(RoomOnboardingDraftRepository(db).read())
        } finally { db.close() }
    }

    @Test fun failedInitializationRollsBackGameAndKeepsDraft() = runBlocking {
        val db = GameDatabase.open(context, name)
        try {
            val profile = PetCustomization(fur = PetFur.Sand)
            val drafts = RoomOnboardingDraftRepository(db)
            val games = RoomGameRepository(db)
            drafts.save(OnboardingDraft(profile, OnboardingStep.Introduction, "BANDANA"))
            val invalid = createInitialGameState().copy(
                pet = profile.toPetState("BANDANA"),
                selectedGoalId = "unknown-goal",
            )
            var failed = false
            try { games.initializeIfAbsent(invalid) } catch (_: Exception) { failed = true }
            assertTrue(failed)
            assertNull(games.read())
            assertEquals(OnboardingDraft(profile, OnboardingStep.Introduction, "BANDANA"), drafts.read())
        } finally { db.close() }
    }
}
