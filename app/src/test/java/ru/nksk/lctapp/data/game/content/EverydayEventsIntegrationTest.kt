package ru.nksk.lctapp.data.game.content

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.app.createInitialGameState
import ru.nksk.lctapp.domain.content.*
import ru.nksk.lctapp.domain.economy.*
import ru.nksk.lctapp.domain.engine.*
import ru.nksk.lctapp.domain.game.*
import ru.nksk.lctapp.domain.pet.PetCosmetics
import ru.nksk.lctapp.domain.pet.PetVisualState

class EverydayEventsIntegrationTest {
    private val catalog = bundledGameCatalog()

    @Test fun completeCatalogValidatesAndContainsAllAuthoredUnexpectedSituations() {
        EventFactory(catalog.content, catalog.policies, catalog.meals, catalog.goals, catalog.storyCampaign)
        assertEquals(25, catalog.dailyEventPool.count { catalog.policies.getValue(it).scheduling.kind == EverydayEventKind.UNEXPECTED })
        assertFalse("Legacy unconditional resin must not be re-offered", "figma-2313-2-v1" in catalog.dailyEventPool)
        val newGame = createInitialGameState()
        assertFalse(catalog.storyProgress(newGame).eligible("figma-2313-2-v2"))
        assertFalse(catalog.storyProgress(newGame).eligible("figma-2320-50-v2"))
        assertEquals(listOf("figma-2164-139-v2"), catalog.policies.filterValues { it.requiresPetHelp }.keys.toList())
        // Installed content definitions remain unchanged; only the runtime policy supplies the pose.
        assertNull(catalog.content.events.single { it.id == "figma-2164-139-v2" }.petStateOnStart)
    }

    @Test fun zeroMoneyBillCanWaitWithoutFakeCompletionAndStillAllowsEarningToday() = runTest {
        val bill = "figma-2164-139-v2"
        val deed = catalog.deedPool.first()
        val f = Fixture(state(bill, 0).let { it.copy(engine = it.engine!!.copy(events = it.engine!!.events +
            EventOccurrence("next-job", deed, EventOrigin.SCHEDULE, EventStatus.PENDING))) })
        assertEquals(EngineResult.Blocked(BlockReason.InsufficientMoney(12)), f.send(EngineCommand.CompleteEvent("active", "$bill:pay")))
        assertTrue(f.send(EngineCommand.PauseEvent("active")) is EngineResult.Applied)
        assertEquals(PetVisualState.NEEDS_HELP, f.repo.value.pet.visualState)
        assertEquals(EventStatus.CARRIED_ACTIVE, f.repo.value.engine!!.events.first().status)
        assertEquals(0L, f.repo.value.economy.balance)
        assertTrue(f.repo.value.story.decisions.isEmpty())
        assertTrue(f.send(EngineCommand.OpenNextEvent) is EngineResult.Applied)
        assertEquals(deed, f.repo.value.engine!!.currentEvent!!.eventId)
        assertEquals(PetVisualState.NEEDS_HELP, f.repo.value.pet.visualState)
        assertTrue(f.repo.value.engine!!.deeds.isNotEmpty())
    }

    @Test fun paidCareResolvesTheOpenedHealthCardAndChargesExactlyItsExistingPrice() = runTest {
        val bill = "figma-2164-139-v2"
        val before = state(bill, 20)
        val f = Fixture(before)
        assertEquals(PetVisualState.NEEDS_HELP, PetEventCondition.forPresentation(before, catalog.policies).visualState)
        assertTrue(f.send(EngineCommand.CompleteEvent("active", "$bill:pay")) is EngineResult.Applied)
        assertEquals(PetVisualState.NORMAL, f.repo.value.pet.visualState)
        assertEquals(8L, f.repo.value.economy.availableBalance)
        assertEquals(5, f.repo.value.engine!!.energy)
        assertEquals(EventStatus.COMPLETED, f.repo.value.engine!!.events.single().status)
        assertEquals(before.pet.selectedLookId, f.repo.value.pet.selectedLookId)
        assertEquals(before.pet.color, f.repo.value.pet.color)
    }

    @Test fun purchasedAccessoryCanBeEquippedButUnownedAccessoryCannot() = runTest {
        val event = "figma-2654-50-purchase-v2"
        val f = Fixture(state(event, 100))
        assertTrue(f.send(EngineCommand.SetPetLook("HAT", "PLAIN")) is EngineResult.Blocked)
        assertTrue(f.send(EngineCommand.CompleteEvent("active", "$event:buy")) is EngineResult.Applied)
        assertEquals(75L, f.repo.value.economy.availableBalance)
        assertTrue(PetCosmetics.canEquip(f.repo.value, "HAT"))
        val step = f.repo.value.engine!!.steps
        assertTrue(f.send(EngineCommand.SetPetLook("HAT", "PLAIN")) is EngineResult.Applied)
        assertEquals("HAT", f.repo.value.pet.selectedLookId)
        assertEquals(step, f.repo.value.engine!!.steps)
        assertFalse(catalog.storyProgress(f.repo.value).eligible(event))
    }

    @Test fun bakeryPurchaseFeedsWithoutRestoringEnergyEvenWhenHungerWouldBlockOtherActions() = runTest {
        val event = "figma-2654-2-purchase-v2"
        val f = Fixture(state(event, 100).let { it.copy(engine = it.engine!!.copy(ateToday = false, steps = 3, energy = 2)) })
        assertTrue(f.send(EngineCommand.CompleteEvent("active", "$event:buy")) is EngineResult.Applied)
        assertTrue(f.repo.value.engine!!.ateToday)
        assertEquals(2, f.repo.value.engine!!.energy)
        assertEquals(94L, f.repo.value.economy.availableBalance)
    }

    private fun state(eventId: String, money: Long) = createInitialGameState().let { initial -> initial.copy(
        pet = initial.pet.copy(selectedLookId = "PLAIN"), selectedGoalId = null,
        economy = EconomyState(BudgetPlan(money, 0, 0, 0), availableBalance = money, savingsBalance = 0),
        story = initial.story.copy(currentDayId = catalog.storyDayId, activeEventId = eventId),
        engine = EngineState(catalog.rules.id, 0, 3, DayPhase.RUNNING, 0, 5, true, null, money,
            listOf(EventOccurrence("active", eventId, EventOrigin.SCHEDULE, EventStatus.ACTIVE)), emptyList()),
    ) }

    private inner class Fixture(initial: GameState) {
        val repo = MemoryRepository(initial)
        val session = GameSession(repo, object : StoryContentRepository {
            override suspend fun read() = catalog.content
            override suspend fun install(content: StoryContent) = Unit
        }, catalog, initial)
        private var sequence = 0
        suspend fun send(command: EngineCommand) = session.dispatch(EngineRequest("event-${++sequence}", repo.value.engine?.revision, command))
    }
    private class MemoryRepository(initial: GameState) : GameRepository {
        private val state = MutableStateFlow(initial)
        val value get() = state.value
        override fun observe() = state
        override suspend fun read() = value
        override suspend fun initializeIfAbsent(initial: GameState) = value
        override suspend fun update(transform: (GameState) -> GameState): GameState = transform(value).also { state.value = it }
    }
}
