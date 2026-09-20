package ru.nksk.lctapp.domain.engine

import ru.nksk.lctapp.domain.content.ItemDefinition
import ru.nksk.lctapp.domain.content.StoryContent
import ru.nksk.lctapp.domain.game.GameState

/** Open catalog identities; selection, ownership and story decisions stay in the saved aggregate. */
data class GoalCampaign(
    val goalId: String,
    val introductionEventId: String,
    val itemIds: List<String>,
    val legacyAcceptanceChoiceIds: Set<String> = emptySet(),
    val availableAfterProjects: Int = 0,
    val scene: String? = null,
    val requiredCompletedGoalIds: Set<String> = emptySet(),
)

fun List<GoalCampaign>.selectedGoal(state: GameState): GoalCampaign? {
    state.selectedGoalId?.let { id -> return firstOrNull { it.goalId == id } }
    // The old introduction already recorded accepting this goal. Never replay or rewrite that history.
    return firstOrNull { goal -> state.completedGoalProjects.none { it.goalId == goal.goalId } &&
        state.story.decisions.any { it.choiceId in goal.legacyAcceptanceChoiceIds } }
}

fun GoalCampaign.isAvailable(state: GameState): Boolean =
    state.completedGoalProjects.none { it.goalId == goalId } && state.completedGoalProjects.size >= availableAfterProjects &&
        state.completedGoalProjects.map { it.goalId }.toSet().containsAll(requiredCompletedGoalIds)

/** Historical association between a passed chapter finale and the personal project collected for it. */
data class CompletedGoalProject(val goalId: String, val decisionId: String)

data class GoalProgress(val items: List<ItemDefinition>, val ownedItemIds: Set<String>) {
    val boughtCount: Int get() = items.count { it.id in ownedItemIds }
    val isCollected: Boolean get() = items.isNotEmpty() && boughtCount == items.size
    val totalPrice: Long get() = items.sumOf { checkNotNull(it.priceCoins) }
    val remainingPrice: Long get() = items.filterNot { it.id in ownedItemIds }.sumOf { checkNotNull(it.priceCoins) }
}

fun GoalCampaign.progress(state: GameState, content: StoryContent) = GoalProgress(
    itemIds.map { id -> content.items.first { it.id == id } }, state.ownedItems.map { it.itemId }.toSet(),
)

/** Advisory amount, never a reserved wallet. Recalculated again inside the purchase transaction. */
fun foodCostUntilWeekEnd(state: GameState, mealPrice: Long): Long {
    val day = state.engine ?: return 7 * mealPrice
    val mealsLeft = 7 - (day.day - 1) % 7 - if (day.ateToday) 1 else 0
    return Math.multiplyExact(mealsLeft.toLong(), mealPrice)
}
