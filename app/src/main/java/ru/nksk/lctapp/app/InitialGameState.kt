package ru.nksk.lctapp.app

import ru.nksk.lctapp.domain.economy.*
import ru.nksk.lctapp.domain.economy.EconomyState
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.pet.PetState
import ru.nksk.lctapp.domain.pet.PetVisualState
import ru.nksk.lctapp.domain.story.StoryState

/** Technical new-save fixture, not weekly income, authored content, or pet parameter limits. */
internal fun createInitialGameState(): GameState = GameState(
    pet = PetState(selectedLookId = "PLAIN", visualState = PetVisualState.NORMAL),
    economy = EconomyState(plan = BudgetPlan(0, 0, 0, 0), unallocated = 100L,
        planning = BudgetPlanning("initial", BudgetPlanningReason.INITIAL, BudgetPlanningStage.RECEIPT, 100L)),
    story = StoryState(null, null, null, emptyList()),
    satiety = 0,
    fatigue = 0,
    ownedItems = emptyList(),
)
