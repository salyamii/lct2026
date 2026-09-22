package ru.nksk.lctapp.feature.menu.ui

import ru.nksk.lctapp.domain.pet.PetState
import ru.nksk.lctapp.domain.pet.PetVisualState

/** Values rendered by the menu; this is presentation state, not a saved game. */
data class MainMenuUiState(
    val coins: Long,
    val backgroundRes: Int = ru.nksk.lctapp.R.drawable.menu_village,
    val completedGoals: Int,
    val totalGoals: Int,
    val pet: MainMenuPetUiState,
    val dayStatus: String? = null,
    val continueLabel: String? = null,
    val canFeed: Boolean = false,
    val busy: Boolean = false,
    val notice: String? = null,
    val mealPrice: Long? = null,
    val showFreeMeal: Boolean = false,
    val goalTitle: String = "Выбрать большую цель",
    val budget: MenuBudgetUiState? = null,
)

/** Actual article balances once economy is connected; null keeps the current runtime HUD. */
data class MenuBudgetUiState(
    val needs: Long,
    val wants: Long,
    val savings: Long,
    val reserve: Long,
)

/** Preview-only presentation fixture. Runtime state comes from MainMenuViewModel. */
internal val MainMenuPreviewState = MainMenuUiState(
    coins = 140L,
    budget = MenuBudgetUiState(needs = 40, wants = 30, savings = 50, reserve = 20),
    completedGoals = 0,
    totalGoals = 4,
    pet = PetState("PLAIN", PetVisualState.NORMAL).toMainMenuPetUiState(),
)

/** UI intents are handled by the app host; the menu stays independent of navigation. */
enum class MainMenuAction {
    Gear, Tasks, Goal, Coins, Village, ContinueDay, Feed,
}
