package ru.nksk.lctapp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import org.junit.Before
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import ru.nksk.lctapp.app.di.GameRepositoryModule
import ru.nksk.lctapp.domain.content.StoryContentRepository
import ru.nksk.lctapp.domain.game.GameRepository
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** App-level smoke: real activity, menu rendering, and the mini-games feature round trip. */
@UninstallModules(GameRepositoryModule::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AppSmokeTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)
    @BindValue @JvmField
    val onboardingDrafts: ru.nksk.lctapp.domain.onboarding.OnboardingDraftRepository =
        ru.nksk.lctapp.TestOnboardingDraftRepository()

    @BindValue @JvmField
    val repository: GameRepository = TestGameRepository()

    @BindValue @JvmField
    val content: StoryContentRepository = TestStoryContentRepository()

    @Before fun waitForMenu() {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Выбрать цель накопления")
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @get:Rule(order = 1)
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
        listOf("Перепутанные находки", "Сверка счетов", "Настрой телескоп").forEach { title ->
            compose.onNodeWithTag("deeds_list").performScrollToNode(hasText(title))
            compose.onNodeWithText(title).assertIsDisplayed()
        }
    }

    @Test
    fun miniGames_openMemoryAndReturnBack() {
        openMiniGames()
        compose.onNodeWithTag("deeds_list").performScrollToNode(hasText("Перепутанные находки"))
        compose.onNodeWithText("Перепутанные находки").performClick()
        compose.onNodeWithContentDescription("Назад").performClick()
        compose.onNodeWithTag("deeds_list").performScrollToNode(hasText("Дела"))
        compose.onNodeWithText("Дела").assertIsDisplayed()
        compose.onNodeWithContentDescription("Назад").performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.menu_continue)).assertIsDisplayed()
    }

    @Test
    fun openMemoryCardSurvivesActivityRecreation() {
        openMiniGames()
        compose.onNodeWithTag("deeds_list").performScrollToNode(hasText("Перепутанные находки"))
        compose.onNodeWithText("Перепутанные находки").performClick()
        compose.onAllNodesWithContentDescription("Рубашка пласта")[0].performClick()
        compose.onAllNodesWithContentDescription("Рубашка пласта").assertCountEquals(15)
        compose.activityRule.scenario.recreate()
        compose.onAllNodesWithContentDescription("Рубашка пласта").assertCountEquals(15)
    }

    private fun openMiniGames() {
        compose.onNode(
            hasText(compose.activity.getString(R.string.menu_tasks)) and hasClickAction(),
        ).assertIsDisplayed().performClick()
    }
}
