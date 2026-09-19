package ru.nksk.lctapp.feature.menu.ui

import ru.nksk.lctapp.domain.game.GameState

internal fun GameState.toMainMenuUiState(): MainMenuUiState = MainMenuUiState(
    hunger = satiety,
    fatigue = fatigue,
    pet = pet.appearance.toMainMenuPetUiState(),
)
