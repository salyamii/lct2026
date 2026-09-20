package ru.nksk.lctapp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
    @BindValue @JvmField val content: StoryContentRepository = TestStoryContentRepository()

    @Test fun goalShortcutOpensTheSelectionScreenAndBackPreservesTheGame() {
        verifyGoalEntry(fromHeader = false, recreate = false)
    }

    @Test fun goalHeaderOpensTheSameScreenAndRestoresWithTheMenuUnderneath() {
        verifyGoalEntry(fromHeader = true, recreate = true)
    }

    private fun verifyGoalEntry(fromHeader: Boolean, recreate: Boolean) {
        awaitText("Выбрать большую цель")
        val before = runBlocking { repository.read() }
        val label = if (fromHeader) "Выбрать большую цель" else compose.activity.getString(R.string.menu_goal)
        compose.onNode(hasText(label) and hasClickAction()).assertIsDisplayed().performClick()
        awaitText("Большие цели")
        compose.onNodeWithText("Ночь наблюдений").assertIsDisplayed()
        compose.onNodeWithText("Частей: 4 · всего 180 монет").assertIsDisplayed()
        assertEquals(before, runBlocking { repository.read() })

        if (recreate) {
            compose.activityRule.scenario.recreate()
            awaitText("Большие цели")
            compose.onNodeWithText("Ночь наблюдений").assertIsDisplayed()
            compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        } else {
            compose.onNodeWithText("Назад").performClick()
        }
        awaitText("Выбрать большую цель")
        assertEquals(before, runBlocking { repository.read() })
    }

    private fun awaitText(text: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(text).assertIsDisplayed()
    }
}
