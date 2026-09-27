package ru.nksk.lctapp.domain.pet

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.domain.content.StoryContent
import ru.nksk.lctapp.domain.economy.BudgetPlan
import ru.nksk.lctapp.domain.economy.EconomyState
import ru.nksk.lctapp.domain.engine.*
import ru.nksk.lctapp.domain.game.*
import ru.nksk.lctapp.domain.story.StoryState

class StarterAccessoryOwnershipTest {
    private fun state(look: String) = GameState(
        PetState(look, PetVisualState.NORMAL), EconomyState(BudgetPlan(35, 20, 35, 10)),
        StoryState(null, null, null, emptyList()), 0, 0, emptyList(),
    )

    @Test fun onlyTheSelectedStarterIsAddedAndExistingOccurrencesStayInOrder() {
        val previous = listOf(OwnedItem("map-2", "map"), OwnedItem("map-1", "map"))
        for (look in listOf("BANDANA", "BACKPACK")) {
            val before = state(look).copy(ownedItems = previous)
            val after = before.withStarterAccessoryOwnership()
            assertEquals(previous, after.ownedItems.take(2))
            assertEquals(before, after.copy(ownedItems = previous))
            assertEquals(look, PetCosmetics.forItem(after.ownedItems.last().itemId)?.lookId)
            assertEquals(after, after.withStarterAccessoryOwnership())
            assertTrue(PetCosmetics.canEquip(after, look))
            assertFalse(PetCosmetics.canEquip(after, if (look == "BANDANA") "BACKPACK" else "BANDANA"))
        }
    }

    @Test fun noAccessoryAndShopLooksNeverGrantAStarterOrAPurchase() {
        for (look in listOf("PLAIN", "HAT", "GLASSES", "unmapped")) {
            val before = state(look)
            assertEquals(before, before.withStarterAccessoryOwnership())
            assertFalse(PetCosmetics.canEquip(before, "BANDANA"))
            assertFalse(PetCosmetics.canEquip(before, "BACKPACK"))
        }
    }

    @Test fun ownershipSurvivesRemovalAndReequipWithoutChangingMoneyOrIdentity() = runTest {
        val initial = state("BANDANA").withStarterAccessoryOwnership()
        val repository = object : GameRepository {
            val saved = MutableStateFlow(initial)
            override fun observe() = saved
            override suspend fun read() = saved.value
            override suspend fun initializeIfAbsent(initial: GameState) = saved.value
            override suspend fun update(transform: (GameState) -> GameState) = transform(saved.value).also { saved.value = it }
        }
        val engine = GameEngine(repository, EventFactory(StoryContent(), emptyMap(), listOf(MealDefinition("food", 5, null))),
            EngineRules("starter", 5, 3, 1))
        assertTrue(engine.dispatch(EngineRequest("remove", null, EngineCommand.SetPetLook("PLAIN", "BANDANA"))) is EngineResult.Applied)
        assertEquals(initial.copy(pet = initial.pet.copy(selectedLookId = "PLAIN")), repository.read())
        assertTrue(engine.dispatch(EngineRequest("reequip", null, EngineCommand.SetPetLook("BANDANA", "PLAIN"))) is EngineResult.Applied)
        assertEquals(initial, repository.read())
        assertTrue(engine.dispatch(EngineRequest("other", null, EngineCommand.SetPetLook("BACKPACK", "BANDANA"))) is EngineResult.Blocked)
        assertEquals(initial, repository.read())
    }
}
