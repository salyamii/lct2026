package ru.nksk.lctapp.data.game.content

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.app.createInitialGameState
import ru.nksk.lctapp.data.game.newDefinitionsComparedTo
import ru.nksk.lctapp.domain.content.*
import ru.nksk.lctapp.domain.economy.BudgetPlan
import ru.nksk.lctapp.domain.economy.EconomyState
import ru.nksk.lctapp.domain.engine.*
import ru.nksk.lctapp.domain.game.GameRepository
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.minigame.DeedGameKind
import ru.nksk.lctapp.domain.story.StoryDecision

class LorePlateCleaningEventTest {
    private val catalog = bundledGameCatalog()

    @Test fun installationAppendsOnlyTheNewLoreDefinitionAndKeepsTheOriginalChoice() {
        val oldEvent = EventDefinition(LEGACY, EventType.STORY, "Пластина с тем же знаком",
            "Смотритель: «Я не ошибся. Тот же знак». Надпись на пластине почти скрыта налётом — её придётся очистить.",
            null, null, null, 0, null, null)
        val oldChoice = EventChoiceDefinition("$LEGACY:continue", LEGACY, 0, "Очистить пластину",
            0, null, null, GoalImpact.NEUTRAL)
        val installed = catalog.content.copy(
            events = catalog.content.events.filterNot { it.id == CURRENT }.map { if (it.id == LEGACY) oldEvent else it },
            choices = catalog.content.choices.filterNot { it.eventId == CURRENT }.map { if (it.id == oldChoice.id) oldChoice else it },
        )
        val added = catalog.content.newDefinitionsComparedTo(installed)
        assertEquals(oldEvent, catalog.content.events.single { it.id == LEGACY })
        assertEquals(listOf(oldChoice), catalog.content.choices.filter { it.eventId == LEGACY })
        assertEquals(listOf(CURRENT), added.events.map { it.id })
        assertEquals(listOf(PAY, MANUAL), added.choices.map { it.id })
        assertEquals(StoryContent(events = added.events, choices = added.choices), added)
        assertEquals(StoryContent(), catalog.content.newDefinitionsComparedTo(catalog.content))
    }

    @Test fun currentLoreHasDirectMoneyOrEffortChoicesWhileRandomRepairStillHasItsGame() {
        EventFactory(catalog.content, catalog.policies, catalog.meals, catalog.goals, catalog.storyCampaign)
        val policy = catalog.policies.getValue(CURRENT)
        assertEquals(EventType.STORY, catalog.content.events.single { it.id == CURRENT }.type)
        assertEquals(0, policy.energyFor(PAY))
        assertEquals(CampaignBalance.SMALL_WORK, policy.energyFor(MANUAL))
        assertTrue(policy.choiceGameKinds.isEmpty())
        assertEquals(setOf("plate_found", "plate_symbol"), policy.factsByChoiceId[PAY])
        assertEquals(policy.factsByChoiceId[PAY], policy.factsByChoiceId[MANUAL])
        assertEquals("Вернуться позже", catalog.cards.getValue(CURRENT).later)
        assertEquals(setOf(PAY, MANUAL), catalog.cards.getValue(CURRENT).summaryByChoiceId.keys)
        assertNull(catalog.policies.getValue(LEGACY).storyActId)
        assertTrue(catalog.storyCampaign!!.acts.single { CURRENT in it.eventIds }.eventIds.none { it == LEGACY })
        assertEquals(CURRENT, catalog.eventReplacements[LEGACY])
        assertEquals(DeedGameKind.PRECISION,
            catalog.policies.getValue(PLATE_CLEANING).choiceGameKinds["$PLATE_CLEANING:work"])
    }

    @Test fun oldAndNewCompletionsSatisfyTheSameStoryPrerequisitesWithoutRepeatingTheScene() {
        for (choice in listOf("$LEGACY:continue", PAY, MANUAL)) {
            val before = activeState()
            val day = before.engine!!
            val finished = before.copy(story = before.story.copy(activeEventId = null,
                decisions = before.story.decisions + StoryDecision("$OCCURRENCE:decision", choice)),
                engine = day.copy(events = day.events.map { it.copy(status = EventStatus.COMPLETED) }))
            val progress = catalog.storyProgress(finished)
            assertTrue(progress.completed(LEGACY))
            assertTrue(progress.completed(CURRENT))
            assertTrue(progress.facts.containsAll(setOf("plate_found", "plate_symbol")))
            assertTrue(progress.eligible(storyEventId("G1.04")))
            assertFalse(progress.eligible(CURRENT))
            assertEquals(storyEventId("G1.04"), progress.nextEvent())
        }
    }

    @Test fun payingOrCleaningDirectlyGrantsTheSameClueWithOnlyItsOwnCost() = runTest {
        for (choice in listOf(PAY, MANUAL)) {
            val fixture = Fixture(activeState(money = 20, energy = 3))
            fixture.session.prepare()
            val before = fixture.repo.value
            val result = fixture.send(EngineCommand.CompleteEvent(OCCURRENCE, choice))
            assertTrue(result.toString(), result is EngineResult.Applied)
            val after = fixture.repo.value
            assertEquals(if (choice == PAY) 17L else 20L, after.economy.availableBalance)
            assertEquals(if (choice == PAY) 3 else 3 - CampaignBalance.SMALL_WORK, after.engine!!.energy)
            assertEquals(before.engine!!.steps + 1, after.engine!!.steps)
            assertEquals(EventStatus.COMPLETED, after.engine!!.events.single().status)
            assertEquals(choice, after.story.decisions.last().choiceId)
            assertTrue(catalog.storyProgress(after).eligible(storyEventId("G1.04")))
            assertEquals(before.ownedItems, after.ownedItems)
        }
    }

    @Test fun postponingKeepsBothCostsAndCluesUnappliedAndOldOpenCardAdoptsCurrentChoices() = runTest {
        val fixture = Fixture(activeState(eventId = LEGACY))
        val old = fixture.repo.value
        fixture.session.prepare()
        val before = fixture.repo.value
        assertEquals(CURRENT, before.engine!!.currentEvent!!.eventId)
        assertEquals(old.engine!!.currentEvent!!.id, before.engine!!.currentEvent!!.id)
        assertEquals(old.story.decisions, before.story.decisions)
        assertEquals(old.economy, before.economy)
        val result = fixture.send(EngineCommand.PauseEvent(OCCURRENCE))
        assertTrue(result.toString(), result is EngineResult.Applied)
        val after = fixture.repo.value
        assertEquals(EventStatus.PAUSED, after.engine!!.events.single().status)
        assertEquals(before.economy, after.economy)
        assertEquals(before.engine!!.energy, after.engine!!.energy)
        assertEquals(before.engine!!.steps, after.engine!!.steps)
        assertEquals(before.story.decisions, after.story.decisions)
        assertFalse("plate_symbol" in catalog.storyProgress(after).facts)
    }

    private fun activeState(money: Long = 20, energy: Int = 3, eventId: String = CURRENT): GameState {
        val initial = createInitialGameState()
        val dockChoice = catalog.policies.values.flatMap { it.factsByChoiceId.entries }
            .first { "helped_dock" in it.value }.key
        val prior = listOf(dockChoice, "${storyEventId("G1.01")}:continue", "${storyEventId("G1.02")}:continue")
        return initial.copy(selectedGoalId = STARS_GOAL,
            economy = EconomyState(BudgetPlan(0, 0, 0, money), availableBalance = money, savingsBalance = 0),
            story = initial.story.copy(currentDayId = catalog.storyDayId, activeEventId = eventId,
                decisions = prior.mapIndexed { index, id -> StoryDecision("prior-$index", id) }),
            engine = EngineState(catalog.rules.id, 0, 3, DayPhase.RUNNING, 0, energy, true, null, money,
                listOf(EventOccurrence(OCCURRENCE, eventId, EventOrigin.SCHEDULE, EventStatus.ACTIVE)), emptyList()))
    }

    private inner class Fixture(initial: GameState) {
        val repo = MemoryRepository(initial)
        val session = GameSession(repo, object : StoryContentRepository {
            override suspend fun read() = catalog.content
            override suspend fun install(content: StoryContent) = Unit
        }, catalog, initial)
        private var sequence = 0
        suspend fun send(command: EngineCommand) = session.dispatch(
            EngineRequest("lore-plate-${++sequence}", repo.value.engine?.revision, command))
    }

    private class MemoryRepository(initial: GameState) : GameRepository {
        private val state = MutableStateFlow(initial)
        val value get() = state.value
        override fun observe() = state
        override suspend fun read() = value
        override suspend fun initializeIfAbsent(initial: GameState) = value
        override suspend fun update(transform: (GameState) -> GameState) = transform(value).also { state.value = it }
    }

    private companion object {
        const val LEGACY = LEGACY_LORE_PLATE_CLEANING
        const val CURRENT = LORE_PLATE_CLEANING
        const val PAY = "$CURRENT:pay"
        const val MANUAL = "$CURRENT:continue"
        const val OCCURRENCE = "lore-plate-occurrence"
    }
}
