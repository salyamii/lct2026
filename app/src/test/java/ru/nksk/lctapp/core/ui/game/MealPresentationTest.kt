package ru.nksk.lctapp.core.ui.game

import kotlinx.coroutines.flow.Flow
import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.app.createInitialGameState
import ru.nksk.lctapp.core.ui.components.PetIndicatorKind
import ru.nksk.lctapp.data.game.content.bundledGameCatalog
import ru.nksk.lctapp.domain.economy.BudgetPlan
import ru.nksk.lctapp.domain.economy.EconomyState
import ru.nksk.lctapp.domain.engine.*
import ru.nksk.lctapp.domain.game.GameRepository
import ru.nksk.lctapp.domain.game.GameState

class MealPresentationTest {
    private val catalog = bundledGameCatalog()
    private val repository = object : GameRepository {
        override fun observe(): Flow<GameState?> = error("Projection must not observe storage")
        override suspend fun read(): GameState? = error("Projection must not read storage")
        override suspend fun initializeIfAbsent(initial: GameState): GameState = error("Projection must not initialize a world")
        override suspend fun update(transform: (GameState) -> GameState): GameState = error("Projection must not write")
    }
    private val engine = GameEngine(repository,
        EventFactory(catalog.content, catalog.policies, catalog.meals, catalog.goals, catalog.storyCampaign), catalog.rules)

    private fun state(coins: Long, energy: Int = catalog.rules.fullEnergy): GameState = createInitialGameState().copy(
        economy = EconomyState(BudgetPlan(0, 0, 0, coins)),
        engine = EngineState(catalog.rules.id, 1, 1, DayPhase.RUNNING, 0, energy, false, null,
            coins, emptyList(), emptyList()),
    )

    @Test fun paidMealEffectsStayVisibleAtFullEnergyAndInGodMode() {
        val current = state(100)
        listOf(false, true).forEach { demoMode ->
            val choices = mealChoices(current, catalog, engine, demoMode).associateBy { it.id }
            assertEquals("Обычный обед", choices.getValue("basic-v1").label)
            assertEquals(if (demoMode) "Бесплатно" else "5 монет", choices.getValue("basic-v1").priceLabel)
            assertNull(choices.getValue("basic-v1").consequence)
            assertEquals(listOf(PetIndicatorKind.SATIETY, PetIndicatorKind.ENERGY),
                choices.getValue("basic-v1").petEffects.map { it.kind })
            assertEquals("Роскошный обед", choices.getValue("luxury-v1").label)
            assertEquals(if (demoMode) "Бесплатно" else "7 монет", choices.getValue("luxury-v1").priceLabel)
            assertNull(choices.getValue("luxury-v1").consequence)
            assertEquals("Праздничный обед", choices.getValue("feast-v1").label)
            assertEquals(if (demoMode) "Бесплатно" else "10 монет", choices.getValue("feast-v1").priceLabel)
            assertNull(choices.getValue("feast-v1").consequence)
            val luxury = choices.getValue("luxury-v1").petEffects
            assertEquals(listOf(PetIndicatorKind.SATIETY, PetIndicatorKind.ENERGY), luxury.map { it.kind })
            assertEquals("Восстановит немного сил", luxury.single { it.kind == PetIndicatorKind.ENERGY }.label)
            val festive = choices.getValue("feast-v1").petEffects
            assertEquals(listOf(PetIndicatorKind.SATIETY, PetIndicatorKind.ENERGY, PetIndicatorKind.MOOD), festive.map { it.kind })
            assertEquals("Восстановит больше сил", festive.single { it.kind == PetIndicatorKind.ENERGY }.label)
            assertEquals("Радость", festive.single { it.kind == PetIndicatorKind.MOOD }.label)
            assertEquals(if (demoMode) "В режиме бога силы не тратятся." else "Силы уже полные.",
                festive.single { it.kind == PetIndicatorKind.ENERGY }.explanation)
        }
        assertEquals(state(100), current)
    }

    @Test fun unavailableMealsKeepShortEffectsAndUseTheExistingDomainGuards() {
        val current = state(0, energy = 2)
        listOf(false, true).forEach { demoMode ->
            val choices = mealChoices(current, catalog, engine, demoMode)
            choices.forEach { meal ->
                assertEquals(engine.blockReason(current, EngineCommand.Feed(meal.id), demoMode) == null, meal.enabled)
                assertFalse(meal.consequence.orEmpty().contains("Не хватает"))
                assertFalse(meal.consequence.orEmpty().contains("заработать"))
            }
            listOf("basic-v1", "luxury-v1", "feast-v1").forEach { mealId ->
                assertEquals(demoMode, choices.first { it.id == mealId }.enabled)
                if (demoMode) assertEquals("Бесплатно", choices.first { it.id == mealId }.priceLabel)
            }
            val luxury = choices.first { it.id == "luxury-v1" }
            assertEquals("Восстановит немного сил",
                luxury.petEffects.single { it.kind == PetIndicatorKind.ENERGY }.label)
            val free = choices.first { it.id == "community-v1" }
            assertEquals("Бесплатно", free.priceLabel)
            assertEquals(if (demoMode) null else "После обеда нужен отдых. Утром сил будет меньше.", free.consequence)
        }
    }

    @Test fun mealCapacityRemainsDistinctWhileActualRestorationIsLimitedByMissingEnergy() {
        val current = state(100, energy = catalog.rules.fullEnergy - 1)
        val festive = mealChoices(current, catalog, engine).single { it.id == "feast-v1" }
        assertEquals("Восстановит больше сил", festive.petEffects.single { it.kind == PetIndicatorKind.ENERGY }.label)
        assertEquals("До полного запаса.", festive.petEffects.single { it.kind == PetIndicatorKind.ENERGY }.explanation)
        val after = (engine.preview(current, EngineCommand.Feed("feast-v1")) as EngineResult.Applied).state
        assertEquals(catalog.rules.fullEnergy, after.engine!!.energy)
        assertEquals(catalog.rules.fullEnergy - 1, current.engine!!.energy)
    }

    @Test fun demoMealOffersKeepDayAndBudgetGuards() {
        val current = state(0)
        val blockedStates = listOf(
            current.copy(engine = null),
            current.copy(engine = current.engine!!.copy(phase = DayPhase.FINISHED)),
            current.copy(economy = EconomyState(BudgetPlan(0, 0, 0, 0), unallocated = 1)),
        )
        blockedStates.forEach { blocked ->
            val choices = mealChoices(blocked, catalog, engine, demoMode = true)
            assertTrue(choices.isNotEmpty())
            assertTrue(choices.all { !it.enabled })
            assertTrue(choices.all { it.priceLabel == "Бесплатно" })
        }
    }
}
