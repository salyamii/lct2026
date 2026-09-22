package ru.nksk.lctapp.onboarding

import androidx.compose.ui.test.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.nksk.lctapp.*
import ru.nksk.lctapp.R
import ru.nksk.lctapp.app.di.GameRepositoryModule
import ru.nksk.lctapp.domain.content.StoryContentRepository
import ru.nksk.lctapp.domain.game.GameRepository
import ru.nksk.lctapp.domain.onboarding.OnboardingDraftRepository
import ru.nksk.lctapp.domain.pet.PetFur

@OptIn(ExperimentalTestApi::class)
@UninstallModules(GameRepositoryModule::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class OnboardingJourneyTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @BindValue @JvmField val repository: GameRepository = TestGameRepository(initial = null)
    @BindValue @JvmField val onboardingDrafts: OnboardingDraftRepository = TestOnboardingDraftRepository()
    @BindValue @JvmField val content: StoryContentRepository = TestStoryContentRepository()
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Test fun selectionValidationAndUnavailableCharactersDoNotStartGame() {
        waitForFox()
        compose.onNodeWithText("Начать приключение").performScrollTo().performClick()
        compose.onNodeWithText("Чтобы начать приключение, выбери персонажа нажав на него").assertIsDisplayed()
        compose.onNodeWithText("Понятно").performClick()
        compose.onNodeWithContentDescription("Сова. Пока недоступна").performScrollTo().performClick()
        compose.onNodeWithText("Персонаж пока недоступен\nОткроется во время приключения").assertIsDisplayed()
        val fox = compose.onNodeWithContentDescription("Лисёнок")
        fox.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        fox.performKeyInput { pressKey(Key.Enter) }
        fox.assertIsSelected()
        compose.runOnIdle { assertNull(runBlocking { repository.read() }) }
    }

    @Test fun entireOnboardingSurvivesRecreationAndCommitsOnlyFromIntroduction() {
        waitForFox()
        compose.onNodeWithContentDescription("Лисёнок").performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.onNodeWithContentDescription("Лисёнок").assertIsSelected()
        compose.onNodeWithText("Начать приключение").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasSetTextAction()).performTextInput("Искорка")
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.onNodeWithText("Песочный").performScrollTo().performClick()
        compose.activityRule.scenario.recreate()
        compose.onNode(hasSetTextAction()).assertTextEquals("Искорка")
        compose.onNodeWithText("Песочный").performScrollTo().assertIsSelected()
        compose.onNodeWithText("Продолжить").performClick()
        compose.onNodeWithTag("accessory_pager").performScrollToIndex(2)
        compose.onNodeWithContentDescription("Искорка: Бандана").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithContentDescription("Искорка: Бандана").assertIsDisplayed()
        compose.runOnIdle { assertNull(runBlocking { repository.read() }) }
        compose.onNodeWithText("Применить").performClick()
        compose.onNodeWithText("Выбери большую цель").assertIsDisplayed()
        compose.onNodeWithText("Выбрать цель").assertIsNotEnabled()
        compose.onNodeWithText("Ночь наблюдений").performScrollTo().performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Ночь наблюдений").performScrollTo().assertIsSelected()
        compose.onNodeWithText("Выбрать цель").performClick()
        compose.onNodeWithText("НОВОЕ ПРИКЛЮЧЕНИЕ НАЧАЛОСЬ").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("НОВОЕ ПРИКЛЮЧЕНИЕ НАЧАЛОСЬ").assertIsDisplayed()
        compose.onNodeWithContentDescription("Назад к выбору цели").performClick()
        compose.onNodeWithContentDescription("Назад к аксессуарам").performClick()
        compose.onNodeWithContentDescription("Искорка: Бандана").assertIsDisplayed()
        compose.onNodeWithText("Применить").performClick()
        compose.onNodeWithText("Выбрать цель").performClick()
        compose.runOnIdle { assertNull(runBlocking { repository.read() }) }
        compose.onNodeWithText("В путь!").performClick()
        compose.waitUntil(10_000) { runBlocking { repository.read() } != null }
        compose.runOnIdle {
            val saved = runBlocking { repository.read() }!!
            assertEquals("figma-stargazing-180-v1", saved.selectedGoalId)
            val pet = saved.pet
            assertEquals("Искорка", pet.name)
            assertEquals(ru.nksk.lctapp.domain.pet.PetColor.SAND, pet.color)
            assertEquals("BANDANA", pet.selectedLookId)
        }
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText(compose.activity.getString(R.string.menu_continue)).assertIsDisplayed()
        compose.onNodeWithText("НОВОЕ ПРИКЛЮЧЕНИЕ НАЧАЛОСЬ").assertDoesNotExist()

    }

    private fun waitForFox() {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription("Лисёнок").fetchSemanticsNodes().isNotEmpty()
        }
    }
}
