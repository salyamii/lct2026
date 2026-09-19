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
import ru.nksk.lctapp.data.game.local.MIGRATION_6_7
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
        schemas.createDatabase(6).use { connection ->
            connection.execSQL("""
                INSERT INTO GAME_STATE (id, visual_state, selected_look, satiety, fatigue,
                    balance, planned_needs, planned_wants, planned_savings, planned_reserve)
                VALUES ('current', 'HAPPY', 'BACKPACK', 17, 30, 247, 10, 20, 30, 40)
            """.trimIndent())
            connection.execSQL("INSERT INTO MINI_GAME_COMPLETION VALUES ('done', 'current')")
            connection.execSQL("INSERT INTO ITEM VALUES ('map', 'Map', 'Description', 'STORY', 15)")
            connection.execSQL("INSERT INTO OWNED_ITEM VALUES ('second', 'current', 0, 'map')")
            connection.execSQL("INSERT INTO OWNED_ITEM VALUES ('first', 'current', 1, 'map')")
        }
        schemas.runMigrationsAndValidate(7, listOf(MIGRATION_6_7)).close()
        val db = GameDatabase.open(context, name)
        try {
            val saved = RoomGameRepository(db).read()!!
            assertNull(saved.pet.customization)
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

    @Test fun migrationFromEmptyV6CreatesCompleteDraftTable() = runBlocking {
        schemas.createDatabase(6).close()
        schemas.runMigrationsAndValidate(7, listOf(MIGRATION_6_7)).use { connection ->
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
            RoomOnboardingDraftRepository(db).save(OnboardingDraft(profile, OnboardingStep.Accessories, "BANDANA"))
            assertNull(RoomGameRepository(db).read())
        } finally { db.close() }
        db = GameDatabase.open(context, name)
        val initial = createInitialGameState().copy(pet = PetState("BANDANA", PetVisualState.NORMAL, profile))
        try {
            assertEquals(OnboardingDraft(profile, OnboardingStep.Accessories, "BANDANA"), RoomOnboardingDraftRepository(db).read())
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
            assertEquals(profile, games.read()!!.pet.customization)
        } finally { db.close() }
    }

    @Test fun failedInitializationRollsBackGameAndKeepsDraft() = runBlocking {
        val db = GameDatabase.open(context, name)
        try {
            val profile = PetCustomization(fur = PetFur.Sand)
            val drafts = RoomOnboardingDraftRepository(db)
            val games = RoomGameRepository(db)
            drafts.save(OnboardingDraft(profile, OnboardingStep.Accessories, "BANDANA"))
            val invalid = createInitialGameState().copy(
                pet = PetState("BANDANA", PetVisualState.NORMAL, profile),
                ownedItems = listOf(ru.nksk.lctapp.domain.game.OwnedItem("missing", "unknown-item")),
            )
            var failed = false
            try { games.initializeIfAbsent(invalid) } catch (_: Exception) { failed = true }
            assertTrue(failed)
            assertNull(games.read())
            assertEquals(OnboardingDraft(profile, OnboardingStep.Accessories, "BANDANA"), drafts.read())
        } finally { db.close() }
    }
}
