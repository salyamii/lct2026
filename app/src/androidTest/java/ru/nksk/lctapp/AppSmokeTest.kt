package ru.nksk.lctapp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** App-level smoke: real activity, menu rendering, and the mini-games feature round trip. */
@RunWith(AndroidJUnit4::class)
class AppSmokeTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun launch_rendersMenu() {
        compose.onNodeWithText(compose.activity.getString(R.string.menu_continue)).assertIsDisplayed()
    }

    @Test
    fun recreation_rendersMenuAgain() {
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText(compose.activity.getString(R.string.menu_continue)).assertIsDisplayed()
    }

    @Test
    fun miniGames_hubListsThreeGames() {
        openMiniGames()
        compose.onNodeWithText("Дела").assertIsDisplayed()
        compose.onNodeWithText("Звёздные пласты").assertIsDisplayed()
        compose.onNodeWithText("Сверка счетов").assertIsDisplayed()
        compose.onNodeWithText("Настрой телескоп").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun miniGames_openMemoryAndReturnBack() {
        openMiniGames()
        compose.onNodeWithText("Звёздные пласты").performClick()
        compose.onNodeWithContentDescription("Назад").performClick()
        compose.onNodeWithText("Дела").assertIsDisplayed()
        compose.onNodeWithContentDescription("Назад").performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.menu_continue)).assertIsDisplayed()
    }

    private fun openMiniGames() {
        compose.onNode(
            hasText(compose.activity.getString(R.string.menu_tasks)) and hasClickAction(),
        ).assertIsDisplayed().performClick()
    }
}
