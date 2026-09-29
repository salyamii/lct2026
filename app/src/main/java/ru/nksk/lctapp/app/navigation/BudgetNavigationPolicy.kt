package ru.nksk.lctapp.app.navigation

import androidx.navigation3.runtime.NavKey
import ru.nksk.lctapp.domain.economy.BudgetPlanning
import ru.nksk.lctapp.domain.economy.BudgetPlanningReason
import ru.nksk.lctapp.domain.economy.BudgetPlanningStage
import ru.nksk.lctapp.feature.day.navigation.Day
import ru.nksk.lctapp.feature.economy.navigation.Economy
import ru.nksk.lctapp.feature.menu.navigation.MainMenu
import ru.nksk.lctapp.feature.settings.navigation.Settings
import ru.nksk.lctapp.feature.tasks.navigation.*

/** New onboarding opens persisted INITIAL/ALLOCATION immediately; legacy receipts remain resumable. */
internal fun shouldPresentBudget(
    pending: BudgetPlanning?,
    destination: NavKey?,
    presentedPlanningId: String?,
    confirmedPlanningId: String? = null,
): Boolean {
    // Settings are read-only with respect to the game and remain available during planning.
    if (pending == null || pending.id == confirmedPlanningId || destination == Economy || destination == Settings) return false
    if (destination == MainMenu && pending.reason == BudgetPlanningReason.INITIAL &&
        pending.stage == BudgetPlanningStage.RECEIPT) return false
    return presentedPlanningId != pending.id || destination == Day || destination == Tasks ||
        destination is DeedGame || destination == StarPlates || destination == PriceCheck || destination == Telescope
}
