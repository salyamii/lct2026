package ru.nksk.lctapp.feature.menu.ui

import ru.nksk.lctapp.R

/** Values rendered by the menu; this is presentation state, not a saved game. */
data class MainMenuUiState(
    val hunger: Int,
    val fatigue: Int,
    val pet: MainMenuPetUiState,
)

/** Preview-only presentation fixture. Runtime state comes from MainMenuViewModel. */
internal val MainMenuPreviewState = MainMenuUiState(
    hunger = 0,
    fatigue = 0,
    pet = MainMenuPetUiState(R.drawable.menu_ryzhik, R.string.menu_fox_description),
)

/** UI intents are handled by the app host; the menu stays independent of navigation. */
enum class MainMenuAction {
    Gear, Tasks, Goal, Coins, Village, ContinueDay,
}
