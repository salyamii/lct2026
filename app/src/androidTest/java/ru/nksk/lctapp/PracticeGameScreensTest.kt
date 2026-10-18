package ru.nksk.lctapp

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.nksk.lctapp.core.ui.theme.LCTAppTheme
import ru.nksk.lctapp.domain.minigame.*
import ru.nksk.lctapp.feature.tasks.ui.practice.*

/** Compilation is checked by default. Execution needs an explicit device-test request. */
@RunWith(AndroidJUnit4::class)
class PracticeGameScreensTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun needsCanBeCompletedAndReplayedInAnIsolatedHost() {
        val game = mutableStateOf(PracticeGames.needs())
        compose.setContent { LCTAppTheme {
            NeedWantScreen(game.value, { game.value = game.value.reduce(it) }, {})
        } }
        val choices = game.value.questions.map { if (it.correctChoice == 0) "В ДОРОГУ" else "ДЛЯ РАДОСТИ" }
        choices.forEachIndexed { index, title ->
            compose.onNodeWithText(title).performScrollTo().performClick()
            compose.onNodeWithText(title).assertIsNotEnabled()
            compose.onNodeWithText(if (index == choices.lastIndex) "Посмотреть итог" else "Дальше")
                .performScrollTo().performClick()
        }
        compose.onNodeWithText("Верно с первого раза: 8 из 8").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Ещё раз").performScrollTo().performClick()
        compose.runOnIdle { assertFalse(game.value.finished); assertEquals(0, game.value.correctAnswers) }
    }

    @Test fun discountUsesYesterdayPriceAndWaitsForNext() {
        val game = mutableStateOf(PracticeChoiceState(listOf(
            DiscountQuestion(PracticeItem.BACKPACK, 42, 52, 34),
            DiscountQuestion(PracticeItem.LANTERN, 55, 90, 65),
        )))
        compose.setContent { LCTAppTheme {
            RealDiscountScreen(game.value, { game.value = game.value.reduce(it) }, {})
        } }
        compose.onNodeWithText("Вчера стоил: 42 монет").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("СКИДКА").performScrollTo().performClick()
        compose.onNodeWithText("Экономия: 8 монет", substring = true).performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, game.value.current); assertEquals(1, game.value.correctAnswers) }
        compose.onNodeWithText("Дальше").performScrollTo().performClick()
        compose.onNodeWithText("Вчера стоил: 55 монет").performScrollTo().assertIsDisplayed()
    }

    @Test fun trapAcceptsTheTimelyOptionEvenIfTomorrowCostsLess() {
        val initial = PracticeGames.traps().questions[2]
        val game = mutableStateOf(PracticeChoiceState(listOf(initial)))
        compose.setContent { LCTAppTheme {
            FinancialTrapScreen(game.value, { game.value = game.value.reduce(it) }, {})
        } }
        compose.onNodeWithText("НА СЕГОДНЯ").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(true, game.value.lastCorrect) }
        compose.onNodeWithText("Посмотреть итог").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(game.value.finished) }
    }

    @Test fun extraPurchaseRevealsTheReasonForTheChosenGoal() {
        val game = mutableStateOf(PracticeGames.extraPurchases())
        compose.setContent { LCTAppTheme {
            ExtraPurchaseScreen(game.value, { game.value = game.value.reduce(it) }, {})
        } }
        compose.onNodeWithText("Свечи").performScrollTo().performClick()
        compose.onNodeWithText("Декоративные свечи не помогут", substring = true).performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertEquals(true, game.value.lastCorrect) }
    }

    @Test fun picnicCannotPassByRemovingEveryGameButTheSelectionCanBeCorrected() {
        val game = mutableStateOf(CoinBalanceState.create())
        compose.setContent { LCTAppTheme {
            CoinBalanceScreen(game.value, { game.value = game.value.reduce(it) }, {})
        } }
        compose.onNodeWithText("Понятно").performScrollTo().performClick()
        listOf("Игрушечный парусник", "Булочка", "Медовые леденцы", "Игра с кольцами").forEach {
            compose.onNodeWithText(it).performScrollTo().performClick()
        }
        compose.onNodeWithText("Проверить баланс").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(BalanceVerdict.INCOMPLETE_PLAN, game.value.verdict) }
        compose.onNodeWithText("Выбери ровно одну игру для компании.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Исправить выбор").performScrollTo().performClick()
        compose.onNodeWithText("Игра с кольцами").performScrollTo().performClick()
        compose.onNodeWithText("Проверить баланс").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(BalanceVerdict.BALANCED, game.value.verdict) }
        compose.onNodeWithText("Дальше").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, game.value.current); assertEquals(0, game.value.correctAnswers) }
    }
}
