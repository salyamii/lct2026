package ru.nksk.lctapp.feature.tasks.ui

import ru.nksk.lctapp.domain.economy.BudgetSection
import ru.nksk.lctapp.domain.economy.EconomyOperations
import ru.nksk.lctapp.domain.engine.EngineCommand
import ru.nksk.lctapp.domain.engine.GameCatalog
import ru.nksk.lctapp.domain.engine.selectedGoal
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.history.AuditEntry
import ru.nksk.lctapp.domain.history.AuditType
import ru.nksk.lctapp.domain.pet.renderPetText
import ru.nksk.lctapp.core.ui.game.eventSceneBackground

internal data class DeedRewardOutcome(
    val requestId: String,
    val earned: Long,
    val goalId: String,
    val itemId: String,
    val itemTitle: String,
    val sceneRes: Int,
    val itemPrice: Long,
)

internal data class DeedRewardUiState(
    val earned: Long,
    val goalTitle: String,
    val maximum: Long,
    val reserveBalance: Long,
    val savingsBalance: Long,
    val sceneRes: Int,
    val goalPrice: Long,
    val editing: Boolean = false,
    val input: String = "",
    val deposited: Long = 0,
)

/** The reward belongs to one committed deed, even if a retry returns a later world. */
internal fun deedRewardOutcome(receipt: AuditEntry, occurrenceId: String, catalog: GameCatalog): DeedRewardOutcome? {
    if (receipt.type != AuditType.COMMAND) return null
    val request = receipt.request ?: return null
    val command = request.command as? EngineCommand.CompleteDeed ?: return null
    if (request.demoMode || command.occurrenceId != occurrenceId) return null
    val before = receipt.before ?: return null
    val after = receipt.after ?: return null
    val earned = after.economy.balance - before.economy.balance
    if (earned <= 0) return null
    val goal = catalog.goals.selectedGoal(after) ?: return null
    val itemId = after.selectedSavingItemId?.takeIf { it in goal.itemIds } ?: return null
    if (after.ownedItems.any { it.itemId == itemId }) return null
    val item = catalog.content.items.firstOrNull { it.id == itemId && it.priceCoins != null } ?: return null
    val eventId = after.engine?.events?.firstOrNull { it.id == occurrenceId }?.eventId ?: return null
    val scene = eventSceneBackground(eventId, catalog.cards[eventId]?.scene)
    return DeedRewardOutcome(request.id, earned, goal.goalId, itemId,
        renderPetText(item.name, after.pet.name), scene, checkNotNull(item.priceCoins))
}

internal fun DeedRewardOutcome.transferMaximum(game: GameState, catalog: GameCatalog): Long {
    if (catalog.goals.selectedGoal(game)?.goalId != goalId || game.selectedSavingItemId != itemId ||
        game.ownedItems.any { it.itemId == itemId }) return 0
    val economy = game.economy
    val food = catalog.mealPolicy.foodRequirement(game)
    val maximum = minOf(earned, economy.plan.reserve, (economy.availableBalance - food).coerceAtLeast(0))
    if (maximum <= 0) return 0
    val quote = EconomyOperations.depositQuote(economy, maximum)
    return maximum.takeIf { quote.affordable && quote.parts.all { it.section == BudgetSection.RESERVE } } ?: 0
}
