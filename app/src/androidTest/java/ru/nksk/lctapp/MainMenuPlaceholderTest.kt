package ru.nksk.lctapp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainMenuPlaceholderTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun allButtonsRemainOnTheMenuWithoutChangingProgress() {
        fun label(id: Int) = hasText(compose.activity.getString(id))
        val coins = hasContentDescription(
            compose.activity.getString(R.string.menu_coins_accessibility, 100),
        )
        val labels = listOf(
            label(R.string.menu_current_goal),
            coins,
            label(R.string.menu_gear),
            label(R.string.menu_tasks),
            label(R.string.menu_goal),
            label(R.string.menu_village),
            label(R.string.menu_continue),
        )
        labels.forEach { label ->
            compose.onNode(label and hasClickAction()).assertIsDisplayed().performClick()
            compose.onNode(isDialog()).assertDoesNotExist()
            compose.onNodeWithText(compose.activity.getString(R.string.menu_current_goal))
                .assertIsDisplayed()
            compose.onNodeWithText(compose.activity.getString(R.string.menu_goal_progress, 0, 4))
                .assertIsDisplayed()
            compose.onNode(coins).assertIsDisplayed()
            labels.forEach { visibleLabel ->
                compose.onNode(visibleLabel and hasClickAction()).assertIsDisplayed()
            }
        }
    }
}
