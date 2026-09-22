package ru.nksk.lctapp.domain.minigame

import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.domain.economy.BudgetPlan
import ru.nksk.lctapp.domain.economy.EconomyState
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.engine.EngineState
import ru.nksk.lctapp.domain.engine.DayPhase
import ru.nksk.lctapp.domain.pet.PetState
import ru.nksk.lctapp.domain.pet.PetVisualState
import ru.nksk.lctapp.domain.story.StoryState

class MiniGameCostsTest {
    private val initial = GameState(
        PetState("BACKPACK", PetVisualState.NORMAL), EconomyState(BudgetPlan(0, 0, 0, 100)),
        StoryState(null, null, null, emptyList()), 17, 0, emptyList(),
    )

    @Test fun successAddsBothCostsAndPreservesOtherGameData() {
        val result = MiniGameKind.MEMORY.complete(initial, "attempt")
        assertEquals(37, result.satiety)
        assertEquals(30, result.fatigue)
        assertEquals(initial.economy, result.economy)
        assertEquals(initial.pet, result.pet)
        assertEquals(initial.story, result.story)
    }

    @Test fun repeatedResultDoesNotChargeAgainEvenAtLimit() {
        val result = MiniGameKind.PRICE_QUIZ.complete(initial.copy(fatigue = 50, satiety = 80), "same")
        assertEquals(100, result.fatigue)
        assertEquals(100, result.satiety)
        assertEquals(result, MiniGameKind.PRICE_QUIZ.complete(result, "same"))
    }

    @Test fun eachGameChecksBothCostsIncludingExactBoundary() {
        listOf(MiniGameKind.MEMORY to 30, MiniGameKind.PRICE_QUIZ to 50, MiniGameKind.TELESCOPE to 40).forEach { (kind, cost) ->
            assertTrue(kind.canPlay(initial.copy(fatigue = 100 - cost, satiety = 80)))
            assertFalse(kind.canPlay(initial.copy(fatigue = 101 - cost, satiety = 80)))
            assertFalse(kind.canPlay(initial.copy(satiety = 81)))
        }
    }

    @Test fun legacyCostsCannotAlsoChargeAnActiveEngineDay() {
        val active = initial.copy(engine = EngineState("rules", 0, 1, DayPhase.RUNNING,
            0, 5, false, null, 100, emptyList(), emptyList()))
        assertFalse(MiniGameKind.MEMORY.canPlay(active))
        try {
            MiniGameKind.MEMORY.complete(active, "new-attempt")
            fail("Day completion must use GameEngine")
        } catch (_: MiniGameUnavailableException) { }
        assertEquals(17, active.satiety)
        assertEquals(5, active.engine!!.energy)
        assertTrue(active.completedMiniGames.isEmpty())
    }

    @Test fun latestStatePreventsOverdraft() {
        val afterFirst = MiniGameKind.TELESCOPE.complete(initial.copy(fatigue = 40), "first")
        try {
            MiniGameKind.MEMORY.complete(afterFirst, "second")
            fail("Must reject a result that exceeds the current limit")
        } catch (_: MiniGameUnavailableException) { }
        assertEquals(80, afterFirst.fatigue)
        assertEquals(setOf("first"), afterFirst.completedMiniGames)
    }
}
