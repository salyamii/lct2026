package ru.nksk.lctapp.feature.menu.ui

import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.engine.DayPhase
import ru.nksk.lctapp.domain.engine.GameCatalog
import ru.nksk.lctapp.domain.engine.selectedGoal
import ru.nksk.lctapp.domain.engine.progress
import ru.nksk.lctapp.core.ui.game.energyDescription
import ru.nksk.lctapp.domain.pet.renderPetText

internal fun GameState.toMainMenuUiState(fullEnergy: Int = 5, catalog: GameCatalog? = null): MainMenuUiState {
    val goal = catalog?.goals?.selectedGoal(this)
    val progress = goal?.progress(this, catalog.content)
    return MainMenuUiState(
    coins = economy.balance,
    completedGoals = progress?.boughtCount ?: 0,
    totalGoals = progress?.items?.size ?: 0,
    goalTitle = goal?.let { selected -> renderPetText(catalog.content.goals.first { it.id == selected.goalId }.title, pet.name) }
        ?: if (catalog?.storyProgress(this)?.campaignComplete == true) "История завершена" else "Выбрать большую цель",
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
}
