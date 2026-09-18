package ru.nksk.lctapp

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.nksk.lctapp.app.navigation.LctNavHost
import ru.nksk.lctapp.core.ui.theme.LCTAppTheme

@RunWith(AndroidJUnit4::class)
class NavigationRestorationTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun allFeatureRoutesRestoreFromSavedStateWithMenuUnderneath() {
        val restoration = StateRestorationTester(compose)
        val repository = TestGameRepository()
        restoration.setContent { LCTAppTheme { LctNavHost(gameRepository = repository) } }
        fun label(id: Int) = hasText(compose.activity.getString(id))
        val actions = listOf(
            label(R.string.menu_gear) to R.string.menu_gear,
            label(R.string.menu_tasks) to R.string.menu_tasks,
            label(R.string.menu_goal) to R.string.menu_goal,
            hasContentDescription(compose.activity.getString(R.string.menu_coins_accessibility, 100)) to
                R.string.menu_coins,
            label(R.string.menu_village) to R.string.menu_village,
            label(R.string.menu_continue) to R.string.menu_continue,
        )

        actions.forEach { (action, title) ->
            compose.onNode(action and hasClickAction()).performClick()
            compose.onNodeWithText(compose.activity.getString(R.string.navigation_back)).assertIsDisplayed()

            restoration.emulateSavedInstanceStateRestore()

            compose.onNodeWithText(compose.activity.getString(title)).assertIsDisplayed()
            compose.onNodeWithText(compose.activity.getString(R.string.navigation_back))
                .assertIsDisplayed().performClick()
            compose.onNodeWithText(compose.activity.getString(R.string.menu_current_goal))
                .assertIsDisplayed()
        }
    }
}
