package ru.nksk.lctapp.data.game

import android.content.Context
import androidx.room3.withWriteTransaction
import androidx.room3.withReadTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import ru.nksk.lctapp.app.createInitialGameState
import ru.nksk.lctapp.data.game.local.GameDatabase
import ru.nksk.lctapp.domain.content.GoalDefinition
import ru.nksk.lctapp.domain.engine.CompletedGoalProject
import ru.nksk.lctapp.domain.engine.DayJournalEntry
import ru.nksk.lctapp.domain.engine.DayJournalKind
import ru.nksk.lctapp.domain.engine.DayPhase
import ru.nksk.lctapp.domain.engine.DeedOffer
import ru.nksk.lctapp.domain.engine.EngineState
import ru.nksk.lctapp.domain.engine.EventOccurrence
import ru.nksk.lctapp.domain.engine.EventOrigin
import ru.nksk.lctapp.domain.engine.EventStatus
import ru.nksk.lctapp.domain.pet.PetAge
import ru.nksk.lctapp.domain.pet.PetColor

@RunWith(AndroidJUnit4::class)
class DebugGameResetTest {
    @Test fun resetRemovesEntireAggregateAndAllowsFreshInitializationAfterReopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "debug-reset-${java.util.UUID.randomUUID()}.db"
        var db = GameDatabase.open(context, name)
        try {
            val before = seedSave(db)
            assertEquals(before, RoomGameRepository(db).read())
            val contentBefore = RoomStoryContentRepository(db).read()
            DebugGameResetRepository(db).reset()
            db.close()
            db = GameDatabase.open(context, name)
            assertNull(RoomGameRepository(db).read())
            val dao = db.gameStateDao()
            assertTrue(dao.readOwnedItems("current").isEmpty())
            assertTrue(dao.readDecisions("current").isEmpty())
            assertTrue(dao.readEngineEvents("current").isEmpty())
            assertTrue(dao.readEngineDeeds("current").isEmpty())
            assertTrue(dao.readMiniGameCompletions("current").isEmpty())
            assertNull(dao.readEngine("current"))
            assertNull(dao.readGoalSelection("current"))
            assertTrue(dao.readCompletedGoalProjects("current").isEmpty())
            assertTrue(dao.readDayJournal("current").isEmpty())
            assertEquals(0L, legacyRows(db))
            assertEquals(contentBefore, RoomStoryContentRepository(db).read())
            DebugGameResetRepository(db).reset() // Also safe before a save exists.
            val initial = createInitialGameState()
            assertEquals(initial, RoomGameRepository(db).initializeIfAbsent(initial))
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun failedResetRollsBackAlreadyDeletedChildren() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "debug-reset-rollback-${java.util.UUID.randomUUID()}.db"
        val db = GameDatabase.open(context, name)
        try {
            val before = seedSave(db)
            val games = RoomGameRepository(db)
            db.withWriteTransaction {
                usePrepared("CREATE TRIGGER reject_debug_reset BEFORE DELETE ON GAME_STATE BEGIN SELECT RAISE(ABORT, 'reset rejected'); END") { it.step() }
            }
            try {
                DebugGameResetRepository(db).reset()
                fail("Reset should fail")
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
            }
            assertEquals(before, games.read())
            assertEquals(1L, legacyRows(db))
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
    private suspend fun seedSave(db: GameDatabase) = savedGame().let { saved ->
        val content = testContent()
        RoomStoryContentRepository(db).install(content.copy(goals = content.goals +
            GoalDefinition("next-goal", "Next project", "Another goal")))
        val before = saved.copy(
            pet = saved.pet.copy(name = "Искорка", age = PetAge.ADULT, color = PetColor.SAND),
            story = saved.story.copy(activeEventId = "earning"),
            selectedGoalId = "next-goal",
            completedGoalProjects = listOf(CompletedGoalProject("d3", "goal")),
            completedMiniGames = setOf("done"),
            engine = EngineState(
                rulesId = "rules", revision = 9, day = 3, phase = DayPhase.RUNNING,
                steps = 2, energy = 4, ateToday = true, nextMorningEnergy = null,
                openingBalance = 300, openingEnergy = 5,
                events = listOf(EventOccurrence("occurrence", "earning", EventOrigin.DEED, EventStatus.RESULT, "offer")),
                deeds = listOf(DeedOffer("offer", "earning", 5)),
                journal = listOf(DayJournalEntry("meal", DayJournalKind.MEAL, "basic-v1", -5)),
            ),
        )
        RoomGameRepository(db).initializeIfAbsent(before)
        db.withWriteTransaction {
            usePrepared("INSERT INTO LEGACY_EXPENSE_STATE VALUES ('current', 2, 'CHOOSING', 7)") { it.step() }
        }
        before
    }

    private suspend fun legacyRows(db: GameDatabase): Long = db.withReadTransaction {
        usePrepared("SELECT COUNT(*) FROM LEGACY_EXPENSE_STATE") { it.step(); it.getLong(0) }
    }
}
