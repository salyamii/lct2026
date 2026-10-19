package ru.nksk.lctapp.app.navigation

import androidx.navigation3.runtime.NavKey
import ru.nksk.lctapp.domain.economy.BudgetPlanning
import ru.nksk.lctapp.domain.engine.EventStatus
import ru.nksk.lctapp.domain.engine.GameCatalog
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.feature.goal.navigation.Goal
import ru.nksk.lctapp.feature.menu.navigation.MainMenu
import ru.nksk.lctapp.feature.settings.navigation.Settings

/** Budget writes finish first; returning to gameplay always resumes the missing chapter choice. */
internal fun shouldPresentChapterGoal(
    requiredGoalId: String?,
    planning: BudgetPlanning?,
    destination: NavKey?,
    homeGoalId: String? = null,
): Boolean = requiredGoalId != null && planning == null && destination != Goal && destination != Settings &&
    // Home is an explicit visit to the menu, not completion of the required choice.
    !(destination == MainMenu && homeGoalId == requiredGoalId)

internal fun requiredChapterGoalId(catalog: GameCatalog, game: GameState): String? {
    val event = game.engine?.currentEvent
    // Old saves may still be showing the finale's authored result. Let it finish first.
    if (event?.status == EventStatus.RESULT && catalog.policies[event.eventId]?.finishesStoryAct == true) return null
    return catalog.storyProgress(game).unselectedChapterGoal?.goalId
}
