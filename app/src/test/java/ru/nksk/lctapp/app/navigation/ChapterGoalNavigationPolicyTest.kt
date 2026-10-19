package ru.nksk.lctapp.app.navigation

import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.app.createInitialGameState
import ru.nksk.lctapp.data.game.content.bundledGameCatalog
import ru.nksk.lctapp.domain.economy.*
import ru.nksk.lctapp.domain.engine.*
import ru.nksk.lctapp.domain.game.OwnedItem
import ru.nksk.lctapp.domain.story.StoryDecision
import ru.nksk.lctapp.feature.day.navigation.Day
import ru.nksk.lctapp.feature.goal.navigation.Goal
import ru.nksk.lctapp.feature.menu.navigation.MainMenu
import ru.nksk.lctapp.feature.settings.navigation.Settings
import ru.nksk.lctapp.feature.tasks.navigation.Tasks

class ChapterGoalNavigationPolicyTest {
    private val catalog = bundledGameCatalog()
    private val acts = checkNotNull(catalog.storyCampaign).acts

    @Test fun everyNewChapterRequiresItsOwnChoiceButFirstAndCompletedCampaignDoNot() {
        assertNull(requiredChapterGoalId(catalog, afterFinales(0)))
        for (completed in 1 until acts.size) {
            val game = afterFinales(completed)
            assertEquals(acts[completed].goalId, requiredChapterGoalId(catalog, game))
            assertEquals(acts[completed].goalId, requiredChapterGoalId(catalog,
                game.copy(selectedGoalId = acts.first().goalId)))
        }
        assertNull(requiredChapterGoalId(catalog, afterFinales(acts.size)))
    }

    @Test fun restoredChoiceSurvivesAndBuyingItsPartDoesNotRequireAnotherChapterChoice() {
        val goal = catalog.goals.single { it.goalId == acts[1].goalId }
        val selected = afterFinales(1).copy(selectedGoalId = goal.goalId, selectedSavingItemId = goal.itemIds.first())
        assertNull(requiredChapterGoalId(catalog, selected))
        val bought = selected.copy(selectedSavingItemId = null,
            ownedItems = selected.ownedItems + OwnedItem("purchase", goal.itemIds.first()))
        assertNull(requiredChapterGoalId(catalog, bought))
    }

    @Test fun unfinishedFinaleResultIsAcknowledgedBeforeOpeningTheNextChoice() {
        val game = afterFinales(1)
        val finale = acts.first().finaleId
        val shown = game.copy(story = game.story.copy(activeEventId = finale), engine = EngineState(
            catalog.rules.id, 1, 3, DayPhase.RUNNING, 1, 4, true, null, 100,
            listOf(EventOccurrence("finale", finale, EventOrigin.SCHEDULE, EventStatus.RESULT)), emptyList()))
        assertNull(requiredChapterGoalId(catalog, shown))
        val day = checkNotNull(shown.engine)
        val acknowledged = shown.copy(story = shown.story.copy(activeEventId = null),
            engine = day.copy(events = day.events.map { it.copy(status = EventStatus.COMPLETED) }))
        assertEquals(acts[1].goalId, requiredChapterGoalId(catalog, acknowledged))
    }

    @Test fun budgetHasPriorityAndGameplayCannotSkipTheChoiceOnRestore() {
        val goalId = checkNotNull(acts[1].goalId)
        val budget = BudgetPlanning("weekly", BudgetPlanningReason.WEEKLY, BudgetPlanningStage.ALLOCATION, 100)
        assertFalse(shouldPresentChapterGoal(goalId, budget, Day))
        assertFalse(shouldPresentChapterGoal(goalId, null, Goal))
        assertFalse(shouldPresentChapterGoal(goalId, null, Settings))
        assertTrue(shouldPresentChapterGoal(goalId, null, MainMenu))
        assertTrue(shouldPresentChapterGoal(goalId, null, Day))
        assertTrue(shouldPresentChapterGoal(goalId, null, Tasks))
        assertFalse(shouldPresentChapterGoal(null, null, Day))
    }

    @Test fun explicitHomeAllowsOnlyTheMenuAndOnlyForTheSameMissingGoal() {
        val goalId = checkNotNull(acts[1].goalId)
        assertFalse(shouldPresentChapterGoal(goalId, null, MainMenu, homeGoalId = goalId))
        assertTrue(shouldPresentChapterGoal(goalId, null, Day, homeGoalId = goalId))
        assertTrue(shouldPresentChapterGoal(goalId, null, Tasks, homeGoalId = goalId))
        assertTrue(shouldPresentChapterGoal(acts[2].goalId, null, MainMenu, homeGoalId = goalId))
    }

    private fun afterFinales(count: Int) = createInitialGameState().let { initial ->
        initial.copy(selectedGoalId = null, selectedSavingItemId = null, story = initial.story.copy(
            decisions = acts.take(count).mapIndexed { index, act ->
                StoryDecision("finale-$index", catalog.content.choices.first { it.eventId == act.finaleId }.id)
            }))
    }
}
