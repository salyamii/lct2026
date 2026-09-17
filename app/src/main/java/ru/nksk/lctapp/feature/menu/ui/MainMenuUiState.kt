package ru.nksk.lctapp.feature.menu.ui

/** Values rendered by the menu; this is presentation state, not a saved game. */
data class MainMenuUiState(
    val coins: Int,
    val completedGoals: Int,
    val totalGoals: Int,
)

/** Design fixture until game data is implemented; these are not starting economy rules. */
internal val MainMenuDemoState = MainMenuUiState(
    coins = 100,
    completedGoals = 0,
    totalGoals = 4,
)

/** UI intents are handled by the app host; the menu stays independent of navigation. */
enum class MainMenuAction {
    Gear, Tasks, Goal, Coins, Village, ContinueDay,
}
