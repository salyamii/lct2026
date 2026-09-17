package ru.nksk.lctapp.app

import ru.nksk.lctapp.domain.economy.EconomyState
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.pet.PetLook
import ru.nksk.lctapp.domain.pet.PetState
import ru.nksk.lctapp.domain.pet.PetVisualState
import ru.nksk.lctapp.domain.story.StoryState

/** Temporary starting data for the menu integration; recreated without loading a saved game. */
internal fun createInitialGameState(): GameState = GameState(
    pet = PetState(selectedLook = PetLook.BACKPACK, visualState = PetVisualState.NORMAL),
    economy = EconomyState(
        balance = 100L,
        budgetAllocations = emptyList(),
        savingsGoal = null,
        reserve = 0L,
        expenses = emptyList(),
    ),
    story = StoryState(
        currentChapterId = null,
        currentEventId = null,
        decisions = emptyList(),
    ),
)
