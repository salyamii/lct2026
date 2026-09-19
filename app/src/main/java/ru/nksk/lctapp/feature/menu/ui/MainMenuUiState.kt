package ru.nksk.lctapp.feature.menu.ui

import ru.nksk.lctapp.R

/** Values rendered by the menu; this is presentation state, not a saved game. */
data class MainMenuUiState(
    val coins: Long,
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
)

/** Preview-only presentation fixture. Runtime state comes from MainMenuViewModel. */
internal val MainMenuPreviewState = MainMenuUiState(
    coins = 100L,
    completedGoals = 0,
    totalGoals = 4,
    pet = MainMenuPetUiState(R.drawable.menu_ryzhik, R.string.menu_fox_description),
)

/** UI intents are handled by the app host; the menu stays independent of navigation. */
enum class MainMenuAction {
    Gear, Tasks, Goal, Coins, Village, ContinueDay, Feed,
}
