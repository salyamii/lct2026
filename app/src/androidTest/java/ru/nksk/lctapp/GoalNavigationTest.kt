package ru.nksk.lctapp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.nksk.lctapp.app.di.GameRepositoryModule
import ru.nksk.lctapp.domain.content.StoryContentRepository
import ru.nksk.lctapp.domain.game.GameRepository

@UninstallModules(GameRepositoryModule::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class GoalNavigationTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    @BindValue @JvmField val repository: GameRepository = TestGameRepository()
    @BindValue @JvmField val drafts: ru.nksk.lctapp.domain.onboarding.OnboardingDraftRepository = TestOnboardingDraftRepository()
    @BindValue @JvmField val content: StoryContentRepository = TestStoryContentRepository()

    @Test fun goalShortcutOpensTheCurrentGoalAndBackPreservesTheGame() {
        verifyGoalEntry(fromHeader = false, recreate = false)
    }

    @Test fun goalHeaderOpensTheSameScreenAndRestoresWithTheMenuUnderneath() {
        verifyGoalEntry(fromHeader = true, recreate = true)
    }

    @Test fun detailOpenedFromOtherGoalsReturnsToThatListAfterRecreation() {
        awaitText("Выбрать цель накопления")
        val before = runBlocking { repository.read() }
        compose.onNode(hasText(compose.activity.getString(R.string.menu_goal)) and hasClickAction()).performClick()
        awaitText("Цели")
        compose.onNodeWithText("Другие цели").performClick()
        awaitGoalList()
        compose.onAllNodesWithText("Открыть цель")[0].performClick()
        awaitText("Ночь наблюдений")
        compose.activityRule.scenario.recreate()
        awaitText("Ночь наблюдений")
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        awaitGoalList()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        awaitText("Цели")
        compose.onNodeWithText("Ночь наблюдений").assertIsDisplayed()
        compose.onNodeWithContentDescription("Назад").performClick()
        awaitText("Выбрать цель накопления")
        assertEquals(before, runBlocking { repository.read() })
    }

    @Test fun futureGoalPreviewRestoresListPositionAndToolbarBackOpensCurrentGoal() {
        awaitText("Выбрать цель накопления")
        val before = runBlocking { repository.read() }
        compose.onNode(hasText(compose.activity.getString(R.string.menu_goal)) and hasClickAction()).performClick()
        awaitText("Цели")
        compose.onNodeWithText("Другие цели").performClick()
        val list = compose.onNodeWithTag("goal_project_list")
        list.performScrollToIndex(2)
        val preview = compose.onNode(hasText("Посмотреть цель") and
            hasAnyAncestor(hasTestTag("goal_project_campaign-researcher-home-v1")))
        preview.performScrollTo().assertIsDisplayed()
        val offset = list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        preview.performClick()
        awaitText("Цели")
        compose.activityRule.scenario.recreate()
        awaitText("Цели")
        compose.onNodeWithContentDescription("Назад").performClick()
        list.assertIsDisplayed()
        assertEquals(offset, list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value(), 0.01f)
        preview.assertIsDisplayed()
        compose.onNodeWithContentDescription("Назад").performClick()
        awaitText("Цели")
        compose.onNodeWithText("Ночь наблюдений").assertIsDisplayed()
        assertEquals(before, runBlocking { repository.read() })
    }

    private fun verifyGoalEntry(fromHeader: Boolean, recreate: Boolean) {
        awaitText("Выбрать цель накопления")
        val before = runBlocking { repository.read() }
        val label = if (fromHeader) "Выбрать цель накопления" else compose.activity.getString(R.string.menu_goal)
        compose.onNode(hasText(label) and hasClickAction()).assertIsDisplayed().performClick()
        awaitText("Цели")
        compose.onNodeWithText("Ночь наблюдений").assertIsDisplayed()
        compose.onNodeWithText("Карта звёзд").assertIsDisplayed()
        assertEquals(before, runBlocking { repository.read() })

        if (recreate) {
            compose.activityRule.scenario.recreate()
            awaitText("Цели")
            compose.onNodeWithText("Ночь наблюдений").assertIsDisplayed()
            compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        } else {
            compose.onNodeWithContentDescription("Назад").performClick()
        }
        awaitText("Выбрать цель накопления")
        assertEquals(before, runBlocking { repository.read() })
    }

    private fun awaitGoalList() {
        compose.waitUntil(10_000) { compose.onAllNodes(hasScrollToIndexAction()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Открыть цель"))
        compose.onAllNodesWithText("Открыть цель")[0].assertIsDisplayed()
    }

    private fun awaitText(text: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(text).assertIsDisplayed()
    }
}
