package ru.nksk.lctapp.domain.engine

import ru.nksk.lctapp.domain.economy.EconomyOperations
import ru.nksk.lctapp.domain.economy.SpendingKind
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.pet.PetVisualState

data class MealEffects(
    val exhaustsCurrentEnergy: Boolean,
    val nextMorningEnergy: Int?,
    val visualStateAfter: PetVisualState?,
    val energyRestore: Int = 0,
)

/** Shared authored food policy for commands, financial advice and every entry to feeding. */
class MealPolicy(meals: List<MealDefinition>) {
    private val definitions = meals.toList()
    private val byId = definitions.associateBy { it.id }

    init { require(byId.size == definitions.size) { "Duplicate meal identity" } }

    /** Catalog order is presentation, not the price of ordinary food or the fallback threshold. */
    val basicMeal: MealDefinition get() = requireNotNull(definitions.filter { it.price > 0 }.minByOrNull { it.price }) {
        "Ordinary food requires a paid meal definition"
    }
    val freeMeal: MealDefinition? get() = definitions.firstOrNull { it.price == 0L }

    fun meal(id: String): MealDefinition = requireNotNull(byId[id]) { "Unknown meal: $id" }

    /** The food UI keeps paid choices visible when unaffordable so their guarded action can explain why. */
    fun choices(state: GameState): List<MealDefinition> = definitions.filter {
        it.price > 0 || state.economy.availableBalance < basicMeal.price
    }

    fun canOfferFreeMeal(state: GameState): Boolean = freeMeal != null &&
        state.engine?.let { !it.ateToday && it.phase != DayPhase.FINISHED } == true &&
        state.economy.availableBalance < basicMeal.price

    fun foodRequirement(state: GameState): Long = foodCostUntilWeekEnd(state, basicMeal.price)

    fun effects(mealId: String): MealEffects = meal(mealId).let {
        MealEffects(it.price == 0L, it.nextMorningEnergy, it.visualStateAfter, it.energyRestore)
    }

    /** Running-day and command/revision guards remain in GameEngine; this is its pure food outcome. */
    internal fun apply(state: GameState, mealId: String, fullEnergy: Int): GameState {
        val day = checkNotNull(state.engine)
        val meal = meal(mealId)
        val effects = effects(mealId)
        require(effects.nextMorningEnergy == null || effects.nextMorningEnergy <= fullEnergy)
        val economy = EconomyOperations.spend(state.economy, meal.price, SpendingKind.FEEDING)
        return state.copy(economy = economy,
            pet = effects.visualStateAfter?.let { state.pet.transitionTo(it) } ?: state.pet,
            engine = day.copy(ateToday = true,
                energy = when {
                    effects.exhaustsCurrentEnergy -> 0
                    effects.energyRestore == 0 -> day.energy
                    else -> (day.energy.toLong() + effects.energyRestore).coerceAtMost(fullEnergy.toLong()).toInt()
                },
                nextMorningEnergy = effects.nextMorningEnergy ?: day.nextMorningEnergy),
        )
    }
}
