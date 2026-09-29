package ru.nksk.lctapp.feature.menu.ui

import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.engine.PetEventCondition
import ru.nksk.lctapp.domain.engine.DayPhase
import ru.nksk.lctapp.domain.engine.GameCatalog
import ru.nksk.lctapp.domain.engine.selectedGoal
import ru.nksk.lctapp.domain.engine.progress
import ru.nksk.lctapp.core.ui.game.energyDescription
import ru.nksk.lctapp.domain.pet.renderPetText

internal fun GameState.toMainMenuUiState(fullEnergy: Int = 5, catalog: GameCatalog? = null,
    demoMode: Boolean = false): MainMenuUiState {
    val goal = catalog?.goals?.selectedGoal(this)
    val progress = goal?.progress(this, catalog.content)
    val campaignComplete = catalog?.storyProgress(this)?.campaignComplete == true
    return MainMenuUiState(
    coins = economy.availableBalance,
    canRestartCampaign = campaignComplete,
    budget = MenuBudgetUiState(economy.displayPlan.needs, economy.displayPlan.wants, economy.displayPlan.savings, economy.displayPlan.reserve,
        available = economy.availableBalance, actualSavings = economy.savingsBalance,
        unallocated = economy.availableBalance - economy.displayPlan.total),
    backgroundRes = ru.nksk.lctapp.core.ui.location.locationArtwork(locationScene),
    completedGoals = progress?.boughtCount ?: 0,
    totalGoals = progress?.items?.size ?: 0,
    goalTitle = goal?.let { selected -> renderPetText(catalog.content.goals.first { it.id == selected.goalId }.title, pet.name) }
        ?: if (campaignComplete) "История завершена" else "Выбрать цель накопления",
    pet = (catalog?.let { PetEventCondition.forPresentation(this, it.policies) } ?: pet).toMainMenuPetUiState(),
    dayStatus = engine?.let { "День ${it.day}: ${if (demoMode) "режим бога" else energyDescription(it.energy, fullEnergy).lowercase()}, " + if (it.ateToday) "сыт" else "ещё не ел" },
    continueLabel = engine?.let { when {
        economy.planning != null -> "Распределить монеты"
        it.phase == DayPhase.FINISHED -> "Итоги дня"
        it.currentEvent != null -> "Вернуться к событию"
        it.energy == 0 && !demoMode -> "Закончить день"
        catalog?.storyProgress(this)?.goalReadyForStory == true -> "Выполнить цель"
        it.phase == DayPhase.READY_TO_END -> "Закончить день"
        else -> "Продолжить день"
    } },
    canFeed = economy.planning == null && engine?.let { it.phase != DayPhase.FINISHED && !it.ateToday } == true,
)
}
