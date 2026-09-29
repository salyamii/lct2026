package ru.nksk.lctapp.domain.engine

import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.domain.economy.BudgetPlan
import ru.nksk.lctapp.domain.economy.EconomyState
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.pet.PetState
import ru.nksk.lctapp.domain.pet.PetVisualState
import ru.nksk.lctapp.domain.story.StoryState

class MealPolicyTest {
    private val basic = MealDefinition("basic", 5, null)
    private val free = MealDefinition("community", 0, null, nextMorningEnergy = 3)
    private val special = MealDefinition("special", 8, null)
    private val policy = MealPolicy(listOf(free, special, basic))

    @Test fun ordinaryPriceAndFallbackDoNotDependOnTheOrderOfCatalogMeals() {
        assertEquals(basic, policy.basicMeal)
        assertEquals(free, policy.freeMeal)
        assertEquals(listOf(special, basic), policy.choices(state(5)))
        assertEquals(listOf(free, special, basic), policy.choices(state(4)))
        assertFalse(policy.canOfferFreeMeal(state(5)))
        assertTrue(policy.canOfferFreeMeal(state(4)))
        assertEquals(35L, policy.foodRequirement(state(4)))
    }

    @Test fun freeMealPromptRequiresAnUnfedRunningDayAndARealFallback() {
        val hungry = state(0)
        assertFalse(policy.canOfferFreeMeal(hungry.copy(engine = null)))
        assertFalse(policy.canOfferFreeMeal(hungry.copy(engine = hungry.engine!!.copy(ateToday = true))))
        assertFalse(policy.canOfferFreeMeal(hungry.copy(engine = hungry.engine!!.copy(phase = DayPhase.FINISHED))))
        assertFalse(MealPolicy(listOf(basic)).canOfferFreeMeal(hungry))
        assertTrue(policy.canOfferFreeMeal(hungry))
    }

    @Test fun foodRequirementUsesTheSamePaidPriceBeforeTheFirstDayAndAtWeekEnd() {
        val initial = state(50)
        assertEquals(35L, policy.foodRequirement(initial.copy(engine = null)))
        val lastDay = initial.copy(engine = initial.engine!!.copy(day = 7))
        assertEquals(5L, policy.foodRequirement(lastDay))
        assertEquals(0L, policy.foodRequirement(lastDay.copy(engine = lastDay.engine!!.copy(ateToday = true))))
        assertEquals(35L, policy.foodRequirement(initial.copy(engine = initial.engine!!.copy(day = 8))))
    }

    @Test fun ordinaryFoodSpendsTheNeedsBudgetWithoutRefillingEnergyOrClearingAnExistingMorningLimit() {
        val before = state(12).let { it.copy(engine = it.engine!!.copy(energy = 2, nextMorningEnergy = 3)) }
        val after = policy.apply(before, basic.id, 5)
        val day = checkNotNull(after.engine)
        assertEquals(7L, after.economy.availableBalance)
        assertEquals(7L, after.economy.plan.needs)
        assertEquals(before.economy.savingsBalance, after.economy.savingsBalance)
        assertTrue(day.ateToday)
        assertEquals(2, day.energy)
        assertEquals(3, day.nextMorningEnergy)
        assertEquals(before.pet, after.pet)
        assertFalse(policy.effects(basic.id).exhaustsCurrentEnergy)
    }

    @Test fun freeFoodKeepsMoneyAndEndsCurrentEnergyWithTheAuthoredNextMorningLimit() {
        val before = state(3)
        val after = policy.apply(before, free.id, 5)
        val day = checkNotNull(after.engine)
        assertEquals(before.economy, after.economy)
        assertTrue(day.ateToday)
        assertEquals(0, day.energy)
        assertEquals(3, day.nextMorningEnergy)
        assertTrue(policy.effects(free.id).exhaustsCurrentEnergy)
        assertEquals(3, policy.effects(free.id).nextMorningEnergy)
        assertEquals(before.story, after.story)
        assertEquals(before.ownedItems, after.ownedItems)
    }

    @Test fun upgradedMealsRestoreCurrentEnergyAndMoodWithoutChangingTheNextMorningLimit() {
        for ((price, restoration) in listOf(7L to 1, 10L to 2)) {
            val meal = MealDefinition("upgraded-$price", price, PetVisualState.HAPPY, energyRestore = restoration)
            val before = state(20).let { it.copy(engine = it.engine!!.copy(energy = 1, nextMorningEnergy = 3)) }
            val after = MealPolicy(listOf(basic, meal, free)).apply(before, meal.id, 5)
            assertEquals(20 - price, after.economy.availableBalance)
            assertEquals(1 + restoration, after.engine!!.energy)
            assertEquals(3, after.engine!!.nextMorningEnergy)
            assertEquals(PetVisualState.HAPPY, after.pet.visualState)
            assertTrue(after.engine!!.ateToday)
            assertEquals(before.story, after.story)
        }
    }

    @Test fun restoringFoodCannotRaiseTheDailyMaximum() {
        val meal = MealDefinition("feast", 10, PetVisualState.HAPPY, energyRestore = 2)
        val meals = MealPolicy(listOf(basic, meal))
        for (energy in listOf(4, 5)) {
            val before = state(20).let { it.copy(engine = it.engine!!.copy(energy = energy)) }
            val after = meals.apply(before, meal.id, 5)
            assertEquals(5, after.engine!!.energy)
            assertNull(after.engine!!.nextMorningEnergy)
        }
    }

    private fun state(coins: Long) = GameState(
        PetState("PLAIN", PetVisualState.NORMAL), EconomyState(BudgetPlan(coins, 0, 0, 0)),
        StoryState(null, null, null, emptyList()), 0, 0, emptyList(),
        engine = EngineState("rules", 0, 1, DayPhase.RUNNING, 0, 5, false, null, coins, emptyList(), emptyList()),
    )
}
