package ru.nksk.lctapp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import ru.nksk.lctapp.core.ui.theme.LCTAppTheme
import ru.nksk.lctapp.feature.gear.ui.GearItemUiState
import ru.nksk.lctapp.feature.gear.ui.GearContentPage
import ru.nksk.lctapp.feature.gear.ui.GearItemDetails
import ru.nksk.lctapp.feature.gear.ui.GearLoadState
import ru.nksk.lctapp.feature.gear.ui.GearScreen
import ru.nksk.lctapp.feature.gear.ui.GearUiState

class GearScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun emptyInventoryExplainsBothSectionsAndBackRemainsAvailable() {
        var backs = 0
        compose.setContent {
            LCTAppTheme { GearScreen(GearLoadState.Ready(GearUiState(emptyList(), emptyList())), { backs++ }, {}) }
        }
        compose.onNodeWithText("Сюжетные предметы").assertIsDisplayed()
        compose.onNodeWithText("Здесь будут твои находки").assertIsDisplayed()
        compose.onNodeWithText("Аксессуары").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Пока нет аксессуаров").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Назад").performClick()
        assertEquals(1, backs)
    }

    @Test fun ownedCardsShowPriceAndImageDescription() {
        compose.setContent {
            LCTAppTheme {
                GearScreen(GearLoadState.Ready(GearUiState(
                    listOf(GearItemUiState("map-1", "Карта", "Старинный пергамент", 50)),
                    listOf(GearItemUiState("hat-1", "Шляпа", "Шляпа с пером", null)),
                )), {}, {})
            }
        }
        compose.onNodeWithText("Карта").assertIsDisplayed()
        compose.onNodeWithText("Старинный пергамент").assertIsDisplayed()
        compose.onNodeWithText("50 монет").assertIsDisplayed()
        compose.onNodeWithText("Шляпа").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Цена не указана").performScrollTo().assertIsDisplayed()
    }

    @Test fun errorOffersRetryWithoutClaimingInventoryIsEmpty() {
        var retries = 0
        compose.setContent { LCTAppTheme { GearScreen(GearLoadState.Error, {}, { retries++ }) } }
        compose.onNodeWithText("Пока нет аксессуаров").assertDoesNotExist()
        compose.onNodeWithText("Повторить").performClick()
        assertEquals(1, retries)
    }

    @Test fun equipmentFailureIsVisibleWithoutChangingTheOpenPage() {
        var message by mutableStateOf<Int?>(null)
        var errorText = ""
        val item = GearItemUiState("owned-hat", "Кепка", "Кепка исследователя", null,
            lookId = "HAT", pages = listOf(
                GearContentPage("first", "Снаружи", null, "Вид снаружи"),
                GearContentPage("second", "Детали", null, "Внутренняя отделка кепки"),
            ))
        compose.setContent {
            errorText = stringResource(R.string.gear_equip_error)
            LCTAppTheme {
                GearItemDetails(item, "second", {}, {}, {}, busy = false, actionMessage = message)
            }
        }
        compose.onNodeWithText("Внутренняя отделка кепки").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { message = R.string.gear_equip_error }
        compose.onNodeWithText(errorText).assertIsDisplayed()
        compose.onNodeWithText("Внутренняя отделка кепки").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Вид снаружи").assertDoesNotExist()
    }
}
