package ru.nksk.lctapp.domain.pet

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.domain.economy.*
import ru.nksk.lctapp.domain.game.*
import ru.nksk.lctapp.domain.story.StoryState
import ru.nksk.lctapp.domain.content.StoryContent
import ru.nksk.lctapp.domain.engine.*

class PetCosmeticsTest {
    private val state = GameState(PetState("PLAIN", PetVisualState.NORMAL), EconomyState(BudgetPlan(100, 0, 0, 0)),
        StoryState(null, null, null, emptyList()), 0, 0, emptyList())
    @Test fun purchaseUnlockIsDerivedFromOwnershipIncludingTheImmutableLegacyCap() {
        assertFalse(PetCosmetics.canEquip(state, "HAT"))
        for (id in listOf("figma-2164-2-explorer-cap-v1", "cosmetic-explorer-hat-v2")) {
            val bought = state.copy(ownedItems = listOf(OwnedItem("receipt", id)))
            assertTrue(PetCosmetics.canEquip(bought, "HAT"))
            for (age in PetAge.entries) for (color in PetColor.entries)
                assertTrue(PetCosmetics.canEquip(bought.copy(pet = bought.pet.copy(age = age, color = color)), "HAT"))
        }
        assertFalse(PetCosmetics.canEquip(state, "unmapped-future-look"))
        assertTrue(PetCosmetics.canEquip(state, "PLAIN"))
    }

    @Test fun equipRequiresOwnershipAndCurrentSelectionButNeverChargesAgainOrChangesAge() = runTest {
        val repository = object : GameRepository {
            val current = MutableStateFlow(state.copy(pet = state.pet.copy(age = PetAge.ADULT, color = PetColor.SAND)))
            override fun observe() = current
            override suspend fun read() = current.value
            override suspend fun initializeIfAbsent(initial: GameState) = current.value
            override suspend fun update(transform: (GameState) -> GameState) = transform(current.value).also { current.value = it }
        }
        val engine = GameEngine(repository, EventFactory(StoryContent(), emptyMap(), listOf(MealDefinition("food", 5, null))),
            EngineRules("looks", 5, 3, 1))
        assertTrue(engine.dispatch(EngineRequest("not-owned", null, EngineCommand.SetPetLook("HAT", "PLAIN"))) is EngineResult.Blocked)
        repository.update { it.copy(ownedItems = listOf(OwnedItem("bought", "figma-2164-2-explorer-cap-v1"))) }
        val before = repository.read()
        assertTrue(engine.dispatch(EngineRequest("equip", null, EngineCommand.SetPetLook("HAT", "PLAIN"))) is EngineResult.Applied)
        assertEquals(before.copy(pet = before.pet.copy(selectedLookId = "HAT")), repository.read())
        assertTrue(engine.dispatch(EngineRequest("stale", null, EngineCommand.SetPetLook("PLAIN", "PLAIN"))) is EngineResult.Blocked)
        assertTrue(engine.dispatch(EngineRequest("unequip", null, EngineCommand.SetPetLook("PLAIN", "HAT"))) is EngineResult.Applied)
        assertEquals(before, repository.read())
    }
}
