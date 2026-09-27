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

    @Test fun entireOnboardingRestoresChoicesAndStartsFirstAllocation() {
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
        compose.onNodeWithText("Впереди большое приключение").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Впереди большое приключение").assertIsDisplayed()
        compose.runOnIdle { assertNull(runBlocking { repository.read() }) }
        compose.onNodeWithText("Дальше").performClick()
        compose.onNodeWithText("Выбери цель накопления").assertIsDisplayed()
        compose.onNodeWithText("Начать с этого").assertIsNotEnabled()
        compose.onNodeWithText("Карта звёзд").performScrollTo().performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Карта звёзд").performScrollTo().assertIsSelected()
        compose.onNodeWithText("Начать с этого").performClick()
        compose.onNodeWithText("На что оставим монеты?").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("На что оставим монеты?").assertIsDisplayed()
        compose.runOnIdle { assertNull(runBlocking { repository.read() }) }
        compose.onNodeWithContentDescription("Назад к выбору цели").performClick()
        compose.onNodeWithContentDescription("Назад к началу приключения").performClick()
        compose.onNodeWithContentDescription("Назад к аксессуарам").performClick()
        compose.onNodeWithContentDescription("Искорка: Бандана").assertIsDisplayed()
        compose.onNodeWithText("Применить").performClick()
        compose.onNodeWithText("Дальше").performClick()
        compose.onNodeWithText("Начать с этого").performClick()
        compose.runOnIdle { assertNull(runBlocking { repository.read() }) }
        compose.onNodeWithText("Распределить монеты").performClick()
        compose.waitUntil(10_000) { runBlocking { repository.read() } != null }
        compose.runOnIdle {
            val saved = runBlocking { repository.read() }!!
            assertEquals("figma-stargazing-180-v1", saved.selectedGoalId)
            assertEquals("stargazing-star-map-v1", saved.selectedSavingItemId)
            assertEquals(100L, saved.economy.balance)
            assertEquals(100L, saved.economy.unallocated)
            assertEquals(ru.nksk.lctapp.domain.economy.BudgetPlanningReason.INITIAL, saved.economy.planning!!.reason)
            assertEquals(ru.nksk.lctapp.domain.economy.BudgetPlanningStage.ALLOCATION, saved.economy.planning!!.stage)
            val pet = saved.pet
            assertEquals("Искорка", pet.name)
            assertEquals(ru.nksk.lctapp.domain.pet.PetColor.SAND, pet.color)
            assertEquals("BANDANA", pet.selectedLookId)
        }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Первый план").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Первый план").assertIsDisplayed()
        compose.onNodeWithText("Первый бюджет").assertDoesNotExist()
        compose.onNodeWithText("Распределить монеты").assertDoesNotExist()
        compose.onNodeWithText("НОВОЕ ПРИКЛЮЧЕНИЕ НАЧАЛОСЬ").assertDoesNotExist()
        compose.onNodeWithText("Запомнить план").assertIsNotEnabled()

        // The first saved screen is already allocation; recreation must not show a receipt or credit coins again.
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Первый план").assertIsDisplayed()
        compose.onNodeWithText("Первый бюджет").assertDoesNotExist()
        compose.onNodeWithText("Запомнить план").assertIsNotEnabled()
        compose.runOnIdle {
            val saved = runBlocking { repository.read() }!!
            assertEquals(100L, saved.economy.availableBalance)
            assertEquals(100L, saved.economy.unallocated)
            assertEquals(0L, saved.economy.savingsBalance)
            assertEquals(ru.nksk.lctapp.domain.economy.BudgetPlanningStage.ALLOCATION, saved.economy.planning!!.stage)
        }

        setAllocation("Нужно", 35)
        compose.waitUntil(10_000) { runBlocking { repository.read() }?.economy?.displayPlan?.needs == 35L }
        compose.activityRule.scenario.recreate()
        compose.onNodeWithContentDescription("Нужно: 35 монет. Ввести сумму").assertExists()
        compose.onNodeWithText("Запомнить план").assertIsNotEnabled()

        // System Back cannot leave a budget with unallocated coins.
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Заверши распределение").assertIsDisplayed()
        compose.onNodeWithText("Распредели оставшиеся 65 монет, чтобы продолжить.").assertIsDisplayed()
        compose.onNodeWithText("Распределить").performClick()
        compose.onNodeWithContentDescription("Назад").performClick()
        compose.onNodeWithText("Заверши распределение").assertIsDisplayed()
        compose.onNodeWithText("Распределить").performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Первый план").assertIsDisplayed()
        compose.onNodeWithText("Первый бюджет").assertDoesNotExist()
        compose.onNodeWithContentDescription("Нужно: 35 монет. Ввести сумму").assertExists()
        compose.runOnIdle {
            val saved = runBlocking { repository.read() }!!
            assertEquals(100L, saved.economy.balance)
            assertEquals(65L, saved.economy.unallocated)
            assertNull(saved.engine)
        }
        setAllocation("Хочу", 20)
        compose.waitUntil(10_000) { runBlocking { repository.read() }?.economy?.displayPlan?.wants == 20L }
        setAllocation("В копилку", 20)
        compose.waitUntil(10_000) { runBlocking { repository.read() }?.economy?.displayPlan?.savings == 20L }
        setAllocation("Запас", 25)
        compose.waitUntil(10_000) { runBlocking { repository.read() }?.economy?.unallocated == 0L }
        compose.onNodeWithText("Запомнить план").assertIsEnabled()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntil(10_000) { runBlocking { repository.read() }?.economy?.planning == null }
        compose.onNodeWithText("Монетки").assertIsDisplayed()
        compose.runOnIdle {
            val saved = runBlocking { repository.read() }!!
            assertEquals(ru.nksk.lctapp.domain.economy.BudgetPlan(35, 20, 20, 25), saved.economy.plan)
            assertEquals(100L, saved.economy.balance)
            assertEquals(100L, saved.economy.availableBalance)
            assertEquals(0L, saved.economy.savingsBalance)
            assertNull(saved.engine)
        }
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Монетки").assertIsDisplayed()
        compose.onNodeWithText("Первый план").assertDoesNotExist()
    }

    private fun setAllocation(article: String, amount: Long) {
        compose.onNodeWithContentDescription("$article: 0 монет. Ввести сумму")
            .performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement(amount.toString())
        compose.onNodeWithText("Готово").performClick()
    }

    private fun waitForFox() {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription("Лисёнок").fetchSemanticsNodes().isNotEmpty()
        }
    }
}
