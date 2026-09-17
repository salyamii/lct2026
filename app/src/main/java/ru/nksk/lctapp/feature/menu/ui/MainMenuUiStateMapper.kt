package ru.nksk.lctapp.feature.menu.ui

import ru.nksk.lctapp.domain.game.GameState

internal fun GameState.toMainMenuUiState(): MainMenuUiState = MainMenuUiState(
    coins = economy.balance,
    // The existing adventure counter is a display fixture, not savings or decision arithmetic.
    completedGoals = 0,
    totalGoals = 4,
    pet = pet.appearance.toMainMenuPetUiState(),
)
