package ru.nksk.lctapp.app.navigation

import androidx.navigation3.runtime.NavKey
import ru.nksk.lctapp.domain.economy.BudgetPlanning
import ru.nksk.lctapp.domain.economy.BudgetPlanningReason
import ru.nksk.lctapp.domain.economy.BudgetPlanningStage
import ru.nksk.lctapp.feature.day.navigation.Day
import ru.nksk.lctapp.feature.economy.navigation.Economy
import ru.nksk.lctapp.feature.menu.navigation.MainMenu
import ru.nksk.lctapp.feature.tasks.navigation.*

/** Initial onboarding ends in the menu; entering gameplay still requires a budget. */
internal fun shouldPresentBudget(
    pending: BudgetPlanning?,
    destination: NavKey?,
    presentedPlanningId: String?,
): Boolean {
    if (pending == null || destination == Economy) return false
    if (destination == MainMenu && pending.reason == BudgetPlanningReason.INITIAL &&
        pending.stage == BudgetPlanningStage.RECEIPT) return false
    return presentedPlanningId != pending.id || destination == Day || destination == Tasks ||
        destination is DeedGame || destination == StarPlates || destination == PriceCheck || destination == Telescope
}
