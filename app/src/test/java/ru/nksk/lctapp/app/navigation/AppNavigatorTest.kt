package ru.nksk.lctapp.app.navigation

import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Test

class AppNavigatorTest {
    private data object Home : NavKey
    private data class Detail(val id: String) : NavKey

    @Test fun budgetConfirmationReturnsToItsChapterReviewAndStoryResumesWithoutMenuHop() {
        val day = Detail("day")
        val practice = Detail("chapter-practice")
        val budget = Detail("budget")
        val stack = mutableListOf<NavKey>(Home, day, practice, budget)
        val navigator = AppNavigator(stack)
        navigator.finishBudget(budget, practice)
        assertEquals(listOf(Home, day, practice), stack)
        navigator.returnToOrReplace(practice, day)
        assertEquals(listOf(Home, day), stack)
        navigator.finishBudget(budget, practice)
        assertEquals(listOf(Home, day), stack)
    }

    @Test fun completedReviewIsReplacedWhenStoryWasOpenedFromTheMenu() {
        val practice = Detail("chapter-practice")
        val day = Detail("day")
        val stack = mutableListOf<NavKey>(Home, practice)
        AppNavigator(stack).returnToOrReplace(practice, day)
        assertEquals(listOf(Home, day), stack)
    }

    @Test fun earlierBudgetMovesAboveTheChapterReviewWithoutDroppingItOrDuplicatingKeys() {
        val budget = Detail("budget")
        val savings = Detail("savings")
        val goal = Detail("goal")
        val practice = Detail("chapter-practice")
        val stack = mutableListOf<NavKey>(Home, budget, savings, goal, practice)
        val navigator = AppNavigator(stack)
        navigator.moveToTop(practice, budget)
        assertEquals(listOf(Home, savings, goal, practice, budget), stack)
        navigator.moveToTop(practice, budget)
        assertEquals(1, stack.count { it == budget })
        navigator.finishBudget(budget, practice)
        assertEquals(listOf(Home, savings, goal, practice), stack)
    }

    @Test fun goalAndSavingsCrossLinksReuseTheOriginalEntry() {
        val goal = Detail("goal")
        val savings = Detail("savings")
        val stack = mutableListOf<NavKey>(Home, goal)
        val navigator = AppNavigator(stack)
        repeat(3) {
            navigator.navigateToExisting(goal, savings)
            navigator.navigateToExisting(savings, goal)
            assertEquals(listOf(Home, goal), stack)
        }
        navigator.goBack(goal)
        assertEquals(listOf(Home), stack)
    }

    @Test fun returningToExistingBudgetIgnoresStaleCrossLinkCallbacks() {
        val budget = Detail("budget")
        val savings = Detail("savings")
        val goal = Detail("goal")
        val stack = mutableListOf<NavKey>(Home, budget, savings, goal)
        val navigator = AppNavigator(stack)
        navigator.navigateToExisting(savings, budget)
        assertEquals(listOf(Home, budget, savings, goal), stack)
        navigator.navigateToExisting(goal, budget)
        assertEquals(listOf(Home, budget), stack)
        navigator.navigateToExisting(budget, budget)
        assertEquals(listOf(Home, budget), stack)
    }

    @Test fun startingAGameReplacesItsProposalAndIgnoresTheOldCallback() {
        val proposal = Detail("day")
        val game = Detail("game")
        val stack = mutableListOf<NavKey>(Home, proposal)
        val navigator = AppNavigator(stack)
        navigator.replace(proposal, game)
        navigator.replace(proposal, Detail("duplicate"))
        navigator.replace(Home, Detail("cannot-replace-root"))
        assertEquals(listOf(Home, game), stack)
        navigator.returnToRoot(game)
        navigator.replace(Home, proposal)
        assertEquals(listOf(Home), stack)
    }

    @Test fun finishingFromANestedScreenReturnsDirectlyToTheRoot() {
        val tasks = Detail("tasks")
        val game = Detail("game")
        val stack = mutableListOf<NavKey>(Home, tasks, game)
        AppNavigator(stack).returnToRoot(game)
        assertEquals(listOf(Home), stack)
    }

    @Test fun staleCompletionCannotCloseAnotherScreenOrRemoveTheRoot() {
        val stack = mutableListOf<NavKey>(Home, Detail("current"))
        val navigator = AppNavigator(stack)
        navigator.returnToRoot(Detail("old"))
        assertEquals(listOf(Home, Detail("current")), stack)
        navigator.returnToRoot(Detail("current"))
        navigator.returnToRoot(Home)
        assertEquals(listOf(Home), stack)
    }

    @Test
    fun twoDifferentMenuCallbacksOnlyOpenTheFirstDestination() {
        val stack = mutableListOf<NavKey>(Home)
        val navigator = AppNavigator(stack)
        val openFirst = { navigator.navigate(source = Home, destination = Detail("first")) }
        val openSecond = { navigator.navigate(source = Home, destination = Detail("second")) }

        openFirst()
        openSecond()

        assertEquals(listOf(Home, Detail("first")), stack)
    }

    @Test
    fun repeatedTapsDoNotDuplicateTheCurrentDestination() {
        val stack = mutableListOf<NavKey>(Home)
        val navigator = AppNavigator(stack)

        navigator.navigate(source = Home, destination = Detail("first"))
        navigator.navigate(source = Home, destination = Detail("first"))
        navigator.goBack(source = Detail("first"))

        assertEquals(listOf(Home), stack)
    }

    @Test
    fun differentArgumentsCreateDistinctEntriesAndBackRestoresThePreviousOne() {
        val stack = mutableListOf<NavKey>(Home)
        val navigator = AppNavigator(stack)

        navigator.navigate(source = Home, destination = Detail("first"))
        navigator.navigate(source = Detail("first"), destination = Detail("second"))
        assertEquals(listOf(Home, Detail("first"), Detail("second")), stack)

        navigator.goBack(source = Detail("second"))
        assertEquals(listOf(Home, Detail("first")), stack)
    }

    @Test
    fun backAtRootNeverRemovesTheStartDestination() {
        val stack = mutableListOf<NavKey>(Home)
        val navigator = AppNavigator(stack)

        repeat(3) { navigator.goBack() }

        assertEquals(listOf(Home), stack)
    }

    @Test
    fun navigatingToTheCurrentDestinationKeepsASingleEntry() {
        val stack = mutableListOf<NavKey>(Home, Detail("first"))
        val navigator = AppNavigator(stack)

        navigator.navigate(source = Detail("first"), destination = Detail("first"))

        assertEquals(listOf(Home, Detail("first")), stack)
    }

    @Test
    fun staleFeatureBackCannotPopANewerDestination() {
        val stack = mutableListOf<NavKey>(Home, Detail("first"))
        val navigator = AppNavigator(stack)
        val backFromFirst = { navigator.goBack(source = Detail("first")) }

        navigator.navigate(source = Detail("first"), destination = Detail("second"))
        backFromFirst()

        assertEquals(listOf(Home, Detail("first"), Detail("second")), stack)
    }

    @Test
    fun repeatedFeatureBackOnlyRemovesItsOwnEntry() {
        val stack = mutableListOf<NavKey>(Home, Detail("first"), Detail("second"))
        val navigator = AppNavigator(stack)
        val backFromSecond = { navigator.goBack(source = Detail("second")) }

        backFromSecond()
        backFromSecond()

        assertEquals(listOf(Home, Detail("first")), stack)
    }

    @Test
    fun featureBackAtRootNeverRemovesTheStartDestination() {
        val stack = mutableListOf<NavKey>(Home)
        val navigator = AppNavigator(stack)

        navigator.goBack(source = Home)

        assertEquals(listOf(Home), stack)
    }

    @Test
    fun systemBackRemovesTheCurrentDestination() {
        val stack = mutableListOf<NavKey>(Home, Detail("first"), Detail("second"))
        val navigator = AppNavigator(stack)

        navigator.goBack()

        assertEquals(listOf(Home, Detail("first")), stack)
    }
}
