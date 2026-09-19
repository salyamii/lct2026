package ru.nksk.lctapp.feature.menu.ui

import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.engine.DayPhase
import ru.nksk.lctapp.core.ui.game.energyDescription

internal fun GameState.toMainMenuUiState(fullEnergy: Int = 5): MainMenuUiState = MainMenuUiState(
    coins = economy.balance,
    // The existing adventure counter is a display fixture, not savings or decision arithmetic.
    completedGoals = 0,
    totalGoals = 4,
    pet = pet.toMainMenuPetUiState(),
    dayStatus = engine?.let { "День ${it.day} · ${energyDescription(it.energy, fullEnergy)} · " + if (it.ateToday) "Сыт" else "Ещё не ел" },
    continueLabel = engine?.let { when {
        it.phase == DayPhase.FINISHED -> "Итоги дня"
        it.currentEvent != null -> "Вернуться к событию"
        it.phase == DayPhase.READY_TO_END || it.energy == 0 -> "Закончить день"
        else -> "Продолжить день"
    } },
    canFeed = engine?.let { it.phase != DayPhase.FINISHED && !it.ateToday && it.steps > 0 } == true,
)
