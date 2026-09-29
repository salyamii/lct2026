package ru.nksk.lctapp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.runtime.mutableStateOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import ru.nksk.lctapp.core.ui.theme.LCTAppTheme
import ru.nksk.lctapp.feature.tasks.ui.DeedsScreen
import ru.nksk.lctapp.feature.tasks.ui.DeedsUiState
import ru.nksk.lctapp.feature.tasks.ui.OfferedDeedUiState

class DeedsListTest {
    @get:Rule val compose = createComposeRule()

    @Test fun offscreenOffersAreNotComposedButRemainReachableWithTheirOwnAction() {
        val offers = List(60) { index ->
            OfferedDeedUiState("offer-$index", "Поручение $index", "Помоги мастеру",
                "До 5 монет", "Немного устанет", "Осталось 2 дня", "workshop")
        }
        var selected: String? = null
        compose.setContent {
            LCTAppTheme {
                DeedsScreen(onTraining = {}, onExit = {},
                    state = DeedsUiState(loading = false, offers = offers),
                    onStart = { selected = it })
            }
        }
        compose.onNodeWithText("Поручение 59").assertDoesNotExist()
        compose.onNodeWithTag("deeds_list").performScrollToNode(hasText("Поручение 59"))
        compose.onNodeWithText("Поручение 59").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("offer-59", selected) }
        compose.onNodeWithTag("deeds_list").performScrollToNode(hasText("Дела"))
        compose.onNodeWithText("Дела").assertIsDisplayed()
    }

    @Test fun rejectedStartRemainsVisibleWhenTheListHeadingIsOffscreen() {
        val offers = List(60) { index ->
            OfferedDeedUiState("offer-$index", "Поручение $index", "Помоги мастеру",
                "До 5 монет", "Немного устанет", "Осталось 2 дня", "workshop")
        }
        val state = mutableStateOf(DeedsUiState(loading = false, offers = offers))
        val reason = "Рыжик устал. Сначала нужно отдохнуть."
        compose.setContent {
            LCTAppTheme {
                DeedsScreen(onTraining = {}, onExit = {}, state = state.value,
                    onStart = { state.value = state.value.copy(message = reason) },
                    onDismissMessage = { state.value = state.value.copy(message = null) })
            }
        }
        compose.onNodeWithTag("deeds_list").performScrollToNode(hasText("Поручение 59"))
        compose.onNodeWithText("Поручение 59").performClick()
        compose.onNodeWithText(reason).assertIsDisplayed()
        compose.onNodeWithText("Понятно").performClick()
        compose.onNodeWithText(reason).assertDoesNotExist()
        compose.onNodeWithText("Поручение 59").assertIsDisplayed()
    }
}
