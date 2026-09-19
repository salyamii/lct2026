package ru.nksk.lctapp.data.game

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.async
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.nksk.lctapp.app.createInitialGameState
import ru.nksk.lctapp.data.game.local.GameDatabase
import ru.nksk.lctapp.domain.content.*
import ru.nksk.lctapp.domain.economy.BudgetPlan
import ru.nksk.lctapp.domain.game.OwnedItem
import ru.nksk.lctapp.domain.pet.PetVisualState
import ru.nksk.lctapp.domain.story.StoryDecision
import ru.nksk.lctapp.domain.story.StoryState

@RunWith(AndroidJUnit4::class)
class GamePersistenceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: GameDatabase
    private lateinit var games: RoomGameRepository
    private lateinit var content: RoomStoryContentRepository

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder<GameDatabase>(context)
            .setDriver(BundledSQLiteDriver()).build()
        games = RoomGameRepository(db)
        content = RoomStoryContentRepository(db)
    }

    @After fun close() { db.close() }

    @Test fun fullSnapshotSurvivesClosingAndReopeningTheDatabaseFile() = runBlocking {
        db.close()
        val name = "round-trip-${java.util.UUID.randomUUID()}.db"
        try {
            db = GameDatabase.open(context, name)
            games = RoomGameRepository(db)
            content = RoomStoryContentRepository(db)
            content.install(testContent())
            val expected = savedGame()
            games.initializeIfAbsent(expected)
            db.close()
            db = GameDatabase.open(context, name)
            games = RoomGameRepository(db)
            assertEquals(expected, games.read())
            assertEquals(expected, games.initializeIfAbsent(createInitialGameState()))
            assertEquals(listOf("coin", "rope", "coin"), games.read()!!.ownedItems.map { it.itemId })
            assertEquals(listOf("yes", "no", "yes"), games.read()!!.story.decisions.map { it.choiceId })
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun concurrentInitializationDoesNotReplaceExistingState() = runBlocking {
        val initial = createInitialGameState()
        (0..15).map { n -> async {
            games.initializeIfAbsent(initial.copy(economy = initial.economy.copy(balance = n.toLong())))
        } }.awaitAll().let { results -> assertEquals(1, results.distinct().size) }
        val before = games.read()
        games.initializeIfAbsent(initial)
        assertEquals(before, games.read())
    }

    @Test fun concurrentUpdatesReadLatestStateInsideTransaction() = runBlocking {
        games.initializeIfAbsent(createInitialGameState())
        (1..30).map { async {
            games.update { current -> current.copy(economy = current.economy.copy(balance = current.economy.balance + 1)) }
        } }.awaitAll()
        assertEquals(130L, games.read()!!.economy.balance)
    }

    @Test fun invalidChildReferenceRollsBackPetMoneyInventoryAndHistoryTogether() = runBlocking {
        content.install(testContent())
        games.initializeIfAbsent(savedGame())
        val before = games.read()
        expectFailure { games.update { state -> state.copy(
            pet = state.pet.transitionTo(PetVisualState.NORMAL),
            economy = state.economy.copy(balance = 1L),
            ownedItems = listOf(OwnedItem("missing-owner", "missing-item")),
            story = state.story.copy(decisions = emptyList()),
        ) } }
        assertEquals(before, games.read())
    }

    @Test fun flowObservesCompleteOutcomeAndNeverReseedsOnCollection() = runBlocking {
        content.install(testContent())
        games.initializeIfAbsent(savedGame())
        val subscribed = CompletableDeferred<Unit>()
        val observed = async {
            withTimeout(5_000) {
                games.observe().filterNotNull().onEach { subscribed.complete(Unit) }
                    .first { it.economy.balance == 25L }
            }
        }
        withTimeout(5_000) { subscribed.await() }
        games.update { it.copy(
            economy = it.economy.copy(balance = 25L),
            pet = it.pet.transitionTo(PetVisualState.HAPPY),
            ownedItems = emptyList(),
        ) }
        val snapshot = observed.await()
        assertEquals(PetVisualState.HAPPY, snapshot.pet.visualState)
        assertTrue(snapshot.ownedItems.isEmpty())
        assertEquals(25L, games.observe().filterNotNull().first().economy.balance)
    }

    @Test fun scriptCursorRequiresDayAndExistingPositionWhileActiveEventIsIndependent() = runBlocking {
        content.install(testContent())
        games.initializeIfAbsent(createInitialGameState())
        expectFailure { games.update { it.copy(story = StoryState(null, 4, null, emptyList())) } }
        expectFailure { games.update { it.copy(story = StoryState("day", 99, null, emptyList())) } }
        val state = games.update { it.copy(story = StoryState("day", 4, "earning", emptyList())) }
        assertEquals("earning", state.story.activeEventId)
        assertEquals(4, state.story.nextScriptPosition)
        assertEquals("day", games.update { it.copy(story = it.story.copy(nextScriptPosition = null)) }.story.currentDayId)
    }

    @Test fun identicalContentCanBeInstalledAgainButChangedChoiceCannotRewriteHistory() = runBlocking {
        val original = testContent()
        content.install(original)
        content.install(original)
        games.initializeIfAbsent(savedGame())
        expectFailure { content.install(original.copy(choices = original.choices.map {
            if (it.id == "yes") it.copy(goalImpact = GoalImpact.BAD) else it
        })) }
        assertEquals(GoalImpact.GOOD, content.read().choices.single { it.id == "yes" }.goalImpact)
        assertEquals(savedGame(), games.read())
    }

    @Test fun addingAnEffectToAnExistingEventRequiresANewDefinitionIdentity() = runBlocking {
        content.install(testContent())
        expectFailure { content.install(StoryContent(
            eventItemEffects = listOf(EventItemEffect("extra", "random", 99, "coin", ItemOperation.ADD)),
        )) }
        assertEquals(2, content.read().eventItemEffects.size)
    }

    @Test fun rejectedContentBatchLeavesNoPartialReferenceRows() = runBlocking {
        expectFailure { content.install(StoryContent(
            items = listOf(ItemDefinition("new", "New", "")),
            chapters = listOf(ChapterDefinition("bad", "Bad", "absent-goal")),
        )) }
        assertTrue(content.read().items.isEmpty())
    }

    @Test fun storyEventsCannotGiveGoalItemsDirectlyOrThroughChoices() = runBlocking {
        for (viaChoice in listOf(false, true)) {
            val invalid = testContent().let { c -> c.copy(
                events = c.events.map { if (it.id == "random") it.copy(type = EventType.STORY) else it },
                eventItemEffects = if (viaChoice) emptyList() else c.eventItemEffects,
                choiceItemEffects = if (viaChoice) listOf(ChoiceItemEffect("gift", "yes", 0, "coin", ItemOperation.ADD)) else emptyList(),
            ) }
            expectFailure { content.install(invalid) }
            assertTrue(content.read().events.isEmpty())
        }
    }

    @Test fun newGoalRequirementCannotRetroactivelyMakeStoryRewardInvalid() = runBlocking {
        val base = testContent().copy(
            requiredItems = emptyList(),
            events = testContent().events.map { if (it.id == "random") it.copy(type = EventType.STORY) else it },
        )
        content.install(base)
        expectFailure { content.install(StoryContent(
            goals = listOf(GoalDefinition("new-goal", "Another", "")),
            requiredItems = listOf(GoalRequiredItem("new-goal", "coin")),
        )) }
        assertFalse(content.read().goals.any { it.id == "new-goal" })
    }

    @Test fun orderedRepeatedEffectsAndAbsentEffectsAreRetained() = runBlocking {
        content.install(testContent())
        val restored = content.read()
        assertEquals(listOf(4, 8), restored.schedule.map { it.position })
        assertEquals(listOf("random", "random"), restored.schedule.map { it.eventId })
        assertEquals(listOf("coin", "coin"), restored.eventItemEffects.map { it.itemId })
        assertFalse(restored.eventItemEffects.any { it.eventId == "earning" })
        assertEquals(listOf("yes", "no"), restored.choices.map { it.id })
    }

    @Test fun allPetStatesAndLooksPersistWithoutChangingIndependentParameters() = runBlocking {
        games.initializeIfAbsent(createInitialGameState())
        for (state in PetVisualState.entries) for (look in listOf("PLAIN", "BANDANA", "BACKPACK", "GLASSES", "HAT", "backend:new-look")) {
            games.update { it.copy(pet = it.pet.copy(visualState = state, selectedLookId = look), satiety = -7, fatigue = 1234) }
            assertEquals(state, games.read()!!.pet.visualState)
            assertEquals(look, games.read()!!.pet.selectedLookId)
            assertEquals(-7, games.read()!!.satiety)
            assertEquals(1234, games.read()!!.fatigue)
        }
    }

    private suspend fun expectFailure(block: suspend () -> Unit) {
        try { block() } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            return
        }
        fail("Expected invalid operation to fail")
    }
}

internal fun savedGame() = createInitialGameState().let { initial -> initial.copy(
    pet = initial.pet.copy(selectedLookId = "HAT", visualState = PetVisualState.UPSET),
    satiety = 17,
    fatigue = 29,
    economy = initial.economy.copy(balance = 3_000_000_000L, plan = BudgetPlan(5, 6, 7, 8)),
    story = StoryState("day", 4, "random", listOf(
        StoryDecision("d3", "yes"), StoryDecision("d1", "no"), StoryDecision("d2", "yes"),
    )),
    ownedItems = listOf(OwnedItem("i3", "coin"), OwnedItem("i1", "rope"), OwnedItem("i2", "coin")),
) }

internal fun testContent() = StoryContent(
    goals = listOf(GoalDefinition("goal", "Journey", "Gather supplies")),
    items = listOf(ItemDefinition("coin", "Coin", "Keepsake"), ItemDefinition("rope", "Rope", "Useful")),
    requiredItems = listOf(GoalRequiredItem("goal", "coin"), GoalRequiredItem("goal", "rope")),
    chapters = listOf(ChapterDefinition("chapter", "Start", "goal")),
    days = listOf(GameDayDefinition("day", "chapter", 1)),
    schedule = listOf(ScheduledEvent("s2", "day", 8, "random"), ScheduledEvent("s1", "day", 4, "random")),
    events = listOf(
        EventDefinition("random", EventType.RANDOM, "Rain", "It rains", 10, 80, PetVisualState.WORRIED, -5, null, null),
        EventDefinition("earning", EventType.EARNING, "Work", "Help out", null, null, null, 0, null, null),
    ),
    choices = listOf(
        EventChoiceDefinition("no", "random", 8, "Wait", 0, null, null, GoalImpact.NEUTRAL),
        EventChoiceDefinition("yes", "random", 4, "Repair", -12, null, PetVisualState.HAPPY, GoalImpact.GOOD),
    ),
    eventItemEffects = listOf(
        EventItemEffect("e2", "random", 8, "coin", ItemOperation.ADD),
        EventItemEffect("e1", "random", 4, "coin", ItemOperation.ADD),
    ),
    choiceItemEffects = listOf(ChoiceItemEffect("c1", "no", 0, "rope", ItemOperation.REMOVE)),
)
