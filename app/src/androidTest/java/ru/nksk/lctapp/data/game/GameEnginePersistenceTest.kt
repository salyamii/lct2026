package ru.nksk.lctapp.data.game

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.nksk.lctapp.app.createInitialGameState
import ru.nksk.lctapp.data.game.local.GameDatabase
import ru.nksk.lctapp.domain.content.*
import ru.nksk.lctapp.domain.engine.*
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.game.OwnedItem
import ru.nksk.lctapp.data.game.content.bundledGameCatalog
import ru.nksk.lctapp.domain.location.GameLocation
import ru.nksk.lctapp.domain.location.LocationLighting
import ru.nksk.lctapp.domain.location.LocationScene

@RunWith(AndroidJUnit4::class)
class GameEnginePersistenceTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val name = "engine-${java.util.UUID.randomUUID()}.db"
    private lateinit var db: GameDatabase
    private lateinit var games: RoomGameRepository
    private lateinit var engine: GameEngine
    private var sequence = 0

    @Before fun setUp() = runBlocking {
        db = GameDatabase.open(context, name)
        games = RoomGameRepository(db)
        RoomStoryContentRepository(db).install(catalog())
        games.initializeIfAbsent(createInitialGameState().let { it.copy(economy = it.economy.withTotalBalance(100)) })
        engine = newEngine()
    }

    @After fun close() { db.close(); context.deleteDatabase(name) }

    @Test fun bundledSessionInstallsIdempotentlyAndResumesTheRealIntroduction() = runBlocking {
        games.update { it.copy(locationScene = LocationScene(GameLocation.CITY, LocationLighting.EVENING)) }
        val catalog = bundledGameCatalog()
        val session = GameSession(games, RoomStoryContentRepository(db), catalog, createInitialGameState())
        session.prepare()
        val before = games.read()!!
        assertTrue(session.dispatch(request(session.selectGoalCommand(before, catalog.goals.first().goalId))) is EngineResult.Applied)
        val planning = checkNotNull(games.read()!!.economy.planning)
        assertTrue(session.dispatch(request(EngineCommand.ConfirmBudget(planning.id, planning.revision))) is EngineResult.Applied)
        val result = session.dispatch(request(checkNotNull(session.advanceCommand(games.read()!!)))) as EngineResult.Applied
        val intro = result.state.engine!!.currentEvent!!
        assertEquals(catalog.introductionId, intro.eventId)
        assertEquals(100L, result.state.economy.balance)
        assertEquals(LocationScene(GameLocation.CITY, LocationLighting.EVENING), result.state.locationScene)
        val choiceId = catalog.content.choices.single { it.eventId == intro.eventId }.id
        val travelRequest = request(EngineCommand.Choose(intro.id, choiceId))
        val chosen = session.dispatch(travelRequest) as EngineResult.Applied
        assertEquals(LocationScene(GameLocation.OBSERVATORY, LocationLighting.EVENING), chosen.state.locationScene)
        reopen()
        val restored = GameSession(games, RoomStoryContentRepository(db), catalog, createInitialGameState())
        restored.prepare()
        assertEquals(chosen.state, games.read())
        assertEquals(catalog.content.events.toSet(), RoomStoryContentRepository(db).read().events
            .filter { stored -> catalog.content.events.any { it.id == stored.id } }.toSet())
        assertTrue(restored.dispatch(request(EngineCommand.AcknowledgeResult(intro.id))) is EngineResult.Applied)
        assertEquals(100L, games.read()!!.economy.balance)
        val movedLater = games.update { it.copy(locationScene = it.locationScene.copy(location = GameLocation.PIER)) }
        assertTrue(restored.dispatch(travelRequest) is EngineResult.Applied)
        assertEquals(movedLater, games.read())
    }

    @Test fun pausedAndCarriedActiveEventsRoundTripWithoutLosingTheirIdentity() = runBlocking {
        send(EngineCommand.BeginDay("day", listOf("event", "job", "event", "event")))
        val opened = send(EngineCommand.OpenNextEvent)
        val id = opened.engine!!.currentEvent!!.id
        var state = send(EngineCommand.PauseEvent(id))
        reopen()
        assertEquals(state, games.read())
        assertTrue(state.story.decisions.isEmpty())
        send(EngineCommand.Feed("basic"))
        games.update { it.copy(engine = it.engine!!.copy(energy = 0)) }
        state = send(EngineCommand.FinishDay)
        assertEquals(EventStatus.CARRIED_ACTIVE, state.engine!!.events.first().status)
        reopen()
        assertEquals(state, games.read())
        state = send(EngineCommand.BeginDay("day", listOf("event", "job", "event", "event")))
        assertEquals(EventStatus.PAUSED, state.engine!!.events.first().status)
        reopen()
        assertEquals(state, games.read())
        state = send(EngineCommand.OpenNextEvent)
        assertEquals(id, state.engine!!.currentEvent!!.id)
        state = send(EngineCommand.Choose(id, "event-choice"))
        assertEquals(99L, state.economy.balance)
        assertEquals(1, state.story.decisions.size)
    }

    @Test fun offerActiveDeedAndSavedResultSurviveReopeningWithoutDuplicateReward() = runBlocking {
        send(EngineCommand.BeginDay("day", listOf("job", "event", "event", "event")))
        var state = send(EngineCommand.OpenNextEvent)
        val offer = state.engine!!.deeds.single()
        assertEquals(1, offer.expiresDay)
        reopen()
        assertEquals(state, games.read())
        send(EngineCommand.AcknowledgeResult(state.engine!!.currentEvent!!.id))
        state = send(EngineCommand.StartDeed(offer.id))
        reopen()
        assertEquals(state, games.read())
        val occurrenceId = state.engine!!.currentEvent!!.id
        state = send(EngineCommand.Choose(occurrenceId, "job-choice"))
        assertEquals(110L, state.economy.balance)
        reopen()
        assertEquals(state, games.read())
        assertEquals(EngineResult.Blocked(BlockReason.InvalidEventAction), engine.dispatch(request(EngineCommand.Choose(occurrenceId, "job-choice"))))
        send(EngineCommand.AcknowledgeResult(occurrenceId))
        assertEquals(110L, games.read()!!.economy.balance)
        assertEquals(EngineResult.Blocked(BlockReason.DeedUnavailable), engine.dispatch(request(EngineCommand.StartDeed(offer.id))))
    }

    @Test fun concurrentCommandsCommitOnlyOneWholeOutcome() = runBlocking {
        send(EngineCommand.BeginDay("day", List(4) { "event" }))
        val state = send(EngineCommand.OpenNextEvent)
        val request = request(EngineCommand.Choose(state.engine!!.currentEvent!!.id, "event-choice"))
        val results = (1..8).map { async { engine.dispatch(request) } }.awaitAll()
        assertEquals(1, results.count { it is EngineResult.Applied })
        val saved = games.read()!!
        assertEquals(103L, saved.economy.balance)
        assertEquals(1, saved.story.decisions.size)
        assertEquals(EventStatus.RESULT, saved.engine!!.currentEvent!!.status)
        assertEquals(saved, games.observe().filterNotNull().first())
    }

    @Test fun invalidEngineReferenceRollsBackMoneyInventoryAndEngineTogether() = runBlocking {
        val before = send(EngineCommand.BeginDay("day", List(4) { "event" }))
        try {
            games.update { it.copy(
                economy = it.economy.withTotalBalance(1),
                ownedItems = listOf(OwnedItem("new-owned", "item")),
                engine = it.engine!!.copy(events = it.engine!!.events.mapIndexed { index, event ->
                    if (index == 0) event.copy(eventId = "missing-definition") else event
                }),
            ) }
            fail("Broken reference must roll back the aggregate")
        } catch (_: androidx.sqlite.SQLiteException) { }
        assertEquals(before, games.read())
        reopen()
        assertEquals(before, games.read())
    }

    @Test fun catalogCanAddNewItemAndEventIdentitiesWithoutErasingTheCurrentRun() = runBlocking {
        val before = send(EngineCommand.BeginDay("day", List(4) { "event" }))
        val addition = StoryContent(
            items = listOf(ItemDefinition("backend-cosmetic", "A new accessory", "Can also be used by an event")),
            events = listOf(event("backend-event", EventType.RANDOM)),
            choices = listOf(choice("backend-event", 0)),
        )
        val content = RoomStoryContentRepository(db)
        content.install(addition)
        assertEquals(before, games.read())
        assertTrue(content.read().items.any { it.id == "backend-cosmetic" })
        val factory = EventFactory(content.read(), mapOf("backend-event" to EventPolicy(0, setOf("backend-cosmetic"))), emptyList())
        assertEquals("backend-event", factory.create("backend-event", "new-occurrence").eventId)
    }

    private suspend fun request(command: EngineCommand) = EngineRequest("request-${++sequence}", games.read()!!.engine?.revision, command)
    private suspend fun send(command: EngineCommand): GameState {
        val result = engine.dispatch(request(command))
        assertTrue("$result", result is EngineResult.Applied)
        return (result as EngineResult.Applied).state
    }
    private suspend fun newEngine() = GameEngineProvider(games, RoomStoryContentRepository(db)).create(
        EngineRules("instrumented-rules", 5, 4, 1),
        mapOf("event" to EventPolicy(0), "job" to EventPolicy(1)),
        listOf(MealDefinition("basic", 4, null)),
    )
    private suspend fun reopen() {
        db.close()
        db = GameDatabase.open(context, name)
        games = RoomGameRepository(db)
        engine = newEngine()
    }
    private fun event(id: String, type: EventType) = EventDefinition(id, type, id, "", null, null, null, 0, null, null)
    private fun choice(id: String, money: Long) = EventChoiceDefinition("$id-choice", id, 0, "Continue", money, null, null, GoalImpact.NEUTRAL)
    private fun catalog() = StoryContent(
        goals = listOf(GoalDefinition("goal", "Goal", "")),
        chapters = listOf(ChapterDefinition("chapter", "Chapter", "goal")),
        days = listOf(GameDayDefinition("day", "chapter", 1)),
        items = listOf(ItemDefinition("item", "Item", "")),
        events = listOf(event("event", EventType.RANDOM), event("job", EventType.EARNING)),
        choices = listOf(choice("event", 3), choice("job", 10)),
    )
}
