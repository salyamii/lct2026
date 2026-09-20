package ru.nksk.lctapp.app

import ru.nksk.lctapp.domain.economy.BudgetPlan
import ru.nksk.lctapp.domain.economy.EconomyState
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.pet.PetState
import ru.nksk.lctapp.domain.pet.PetVisualState
import ru.nksk.lctapp.domain.story.StoryState

/** Technical new-save fixture, not weekly income, authored content, or pet parameter limits. */
internal fun createInitialGameState(): GameState = GameState(
    pet = PetState(selectedLookId = "PLAIN", visualState = PetVisualState.NORMAL),
    economy = EconomyState(balance = 100L, plan = BudgetPlan(0, 0, 0, 0)),
    story = StoryState(null, null, null, emptyList()),
    satiety = 0,
    fatigue = 0,
    ownedItems = emptyList(),
)
