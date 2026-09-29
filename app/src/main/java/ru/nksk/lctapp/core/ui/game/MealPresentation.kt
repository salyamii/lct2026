package ru.nksk.lctapp.core.ui.game

import ru.nksk.lctapp.core.ui.components.MealChoiceUiState
import ru.nksk.lctapp.domain.engine.EngineCommand
import ru.nksk.lctapp.domain.engine.GameCatalog
import ru.nksk.lctapp.domain.engine.GameEngine
import ru.nksk.lctapp.domain.game.GameState

/** One projection keeps meal prices, effects and eligibility identical in every food entry. */
internal fun mealChoices(state: GameState, catalog: GameCatalog, engine: GameEngine,
    demoMode: Boolean = false): List<MealChoiceUiState> =
    catalog.mealPolicy.choices(state).map { meal ->
        val effect = catalog.mealPolicy.effects(meal.id)
        val price = catalog.mealPolicy.effectivePrice(meal.id, demoMode)
        val blocked = engine.blockReason(state, EngineCommand.Feed(meal.id), demoMode)
        val label = when {
            meal.price == 0L -> "Бесплатная столовая"
            meal.id == catalog.mealPolicy.basicMeal.id -> "Обычный обед"
            effect.energyRestore >= 2 -> "Праздничный обед"
            else -> "Роскошный обед"
        }
        val description = when {
            effect.exhaustsCurrentEnergy && !demoMode -> "После обеда нужен отдых. Утром сил будет меньше."
            effect.energyRestore >= 2 -> "Поднимет настроение. Восстановит часть сил."
            effect.energyRestore > 0 -> "Поднимет настроение. Восстановит немного сил."
            else -> "Утолит голод."
        }
        MealChoiceUiState(meal.id, label, enabled = blocked == null, consequence = description,
            priceLabel = if (price == 0L) "Бесплатно" else paymentCoinAmount(price))
    }
