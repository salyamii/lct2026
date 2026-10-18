package ru.nksk.lctapp.domain.engine

import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.domain.content.EventChoiceDefinition
import ru.nksk.lctapp.domain.content.EventDefinition
import ru.nksk.lctapp.domain.content.EventType
import ru.nksk.lctapp.domain.content.GoalImpact
import ru.nksk.lctapp.domain.content.StoryContent
import ru.nksk.lctapp.domain.economy.BudgetPlan
import ru.nksk.lctapp.domain.economy.EconomyOperations
import ru.nksk.lctapp.domain.economy.EconomyState
import ru.nksk.lctapp.domain.economy.SpendPart
import ru.nksk.lctapp.domain.economy.SpendingKind
import ru.nksk.lctapp.domain.economy.BudgetSection
import ru.nksk.lctapp.domain.game.GameRepository
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.pet.PetState
import ru.nksk.lctapp.domain.pet.PetVisualState
import ru.nksk.lctapp.domain.story.StoryState

class FoodChoiceSpendingTest {
    @Test fun foodOnAnOptionalPurchaseCardUsesNeedsButNonFoodStillCannot() {
        val before = GameState(PetState("plain", PetVisualState.NORMAL), EconomyState(BudgetPlan(35, 0, 0, 0)),
            StoryState(null, null, "offer", emptyList()), 0, 0, emptyList(),
            EngineState("food-choice", 1, 1, DayPhase.RUNNING, 0, 5, false, null, 35,
                listOf(EventOccurrence("offer:1", "offer", EventOrigin.SCHEDULE, EventStatus.ACTIVE)), emptyList()))
        val request = EngineRequest("buy", 1, EngineCommand.CompleteEvent("offer:1", "buy"))
        val food = engine(before, feedsPet = true)
        assertEquals(SpendingKind.FEEDING, food.choiceSpendingKind("buy"))
        assertEquals(listOf(SpendPart(BudgetSection.NEEDS, 6)),
            EconomyOperations.quote(before.economy, 6, food.choiceSpendingKind("buy")).parts)
        assertNull(food.blockReason(before, request.command))
        val after = food.transition(before, request)
        assertEquals(29L, after.economy.availableBalance)
        assertEquals(BudgetPlan(29, 0, 0, 0), after.economy.plan)
        assertEquals(0L, after.economy.savingsBalance)
        assertTrue(after.engine!!.ateToday)

        val nonFood = engine(before, feedsPet = false)
        assertEquals(SpendingKind.WANT, nonFood.choiceSpendingKind("buy"))
        assertEquals(BlockReason.InsufficientMoney(6), nonFood.blockReason(before, request.command))
        assertFalse(EconomyOperations.quote(before.economy, 6, nonFood.choiceSpendingKind("buy")).affordable)
        assertEquals(BudgetPlan(35, 0, 0, 0), before.economy.plan)
    }

    private fun engine(before: GameState, feedsPet: Boolean): GameEngine {
        val content = StoryContent(
            events = listOf(EventDefinition("offer", EventType.WANT, "Предложение", "Купить или пройти мимо?",
                null, null, null, 0, null, null)),
            choices = listOf(
                EventChoiceDefinition("buy", "offer", 0, "Купить", -6, null, null, GoalImpact.NEUTRAL),
                EventChoiceDefinition("pass", "offer", 1, "Пройти мимо", 0, null, null, GoalImpact.NEUTRAL)),
        )
        val repository = object : GameRepository {
            override fun observe() = flowOf(before)
            override suspend fun read() = before
            override suspend fun initializeIfAbsent(initial: GameState) = before
            override suspend fun update(transform: (GameState) -> GameState): GameState = error("Pure preview cannot write")
        }
        return GameEngine(repository, EventFactory(content,
            mapOf("offer" to EventPolicy(0, feedsPetChoiceIds = if (feedsPet) setOf("buy") else emptySet())),
            listOf(MealDefinition("meal", 5, null))), EngineRules("food-choice", 5, 3, 1))
    }
}
