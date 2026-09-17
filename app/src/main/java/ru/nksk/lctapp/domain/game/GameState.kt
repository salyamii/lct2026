package ru.nksk.lctapp.domain.game

import ru.nksk.lctapp.domain.economy.EconomyState
import ru.nksk.lctapp.domain.pet.PetState
import ru.nksk.lctapp.domain.story.StoryState

/** Domain snapshot; persistence and screen-specific UiState are separate representations. */
data class GameState(
    val pet: PetState,
    val economy: EconomyState,
    val story: StoryState,
)
