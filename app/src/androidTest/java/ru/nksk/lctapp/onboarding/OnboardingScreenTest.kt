package ru.nksk.lctapp.onboarding

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.test.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.nksk.lctapp.app.*
import ru.nksk.lctapp.feature.onboarding.ui.*

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class OnboardingScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun blankNameIsValidatedOnlyOnSubmitAndDoneClearsFocus() {
        val state = mutableStateOf(CustomizationUiState())
        var submitted = 0
        compose.setContent { Profile(state, onContinue = { submitted++ }) }
        compose.onNodeWithText("Введи имя спутника").assertDoesNotExist()
        compose.onNodeWithText("Продолжить").assertIsEnabled().performClick()
        compose.onNodeWithText("Введи имя спутника").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, submitted) }
        compose.onNode(hasSetTextAction()).performTextInput("Искорка")
        compose.onNodeWithText("Введи имя спутника").assertDoesNotExist()
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.onNode(hasSetTextAction()).assertIsNotFocused()
        compose.onNodeWithText("Продолжить").performClick()
        compose.runOnIdle { assertEquals(1, submitted); assertEquals("Искорка", state.value.name) }
    }

    @Test fun externalStateRecomposesNameFurAndTemperament() {
        val state = mutableStateOf(CustomizationUiState(name = "Рыжик"))
        compose.setContent { Profile(state) }
        compose.runOnIdle { state.value = CustomizationUiState("Искорка", CharacterTemperament.Joyful, CharacterFur.Sand) }
        compose.onNode(hasSetTextAction()).assertTextEquals("Искорка")
        compose.onNodeWithContentDescription("Образ спутника: Песочный").assertIsDisplayed()
        compose.onNodeWithText("Радостный", substring = false).performScrollTo().assertIsSelected()
        compose.onNodeWithText("Медный").performScrollTo().performClick().assertIsSelected()
        compose.runOnIdle { assertEquals(CharacterFur.Copper, state.value.fur) }
    }

    @Test fun resizingKeepsSelectionAndSceneStaysPinnedWhileFormScrolls() {
        val size = mutableStateOf(DpSize(400.dp, 500.dp))
        val state = mutableStateOf(CustomizationUiState("Искорка", CharacterTemperament.Confident, CharacterFur.Russet))
        compose.setContent { Profile(state, size.value) }
        val scene = compose.onNodeWithContentDescription("Образ спутника: Тёмно-рыжий")
        val before = scene.fetchSemanticsNode().boundsInRoot
        compose.onNodeWithText("Тёмно-рыжий", substring = false).performScrollTo()
        assertEquals(before, scene.fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle { size.value = DpSize(900.dp, 500.dp) }
        compose.onNode(hasSetTextAction()).assertTextEquals("Искорка")
        compose.onNodeWithText("Уверенный", substring = false).assertIsSelected()
        compose.onNodeWithText("Тёмно-рыжий", substring = false).performScrollTo().assertIsSelected()
    }

    @Test fun savedProfileAndValidationRestoreAfterCompositionRecreation() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            val saver = listSaver<CustomizationUiState, String>(
                save = { listOf(it.name, it.temperament.name, it.fur.name) },
                restore = { CustomizationUiState(it[0], CharacterTemperament.valueOf(it[1]), CharacterFur.valueOf(it[2])) })
            val state = rememberSaveable(stateSaver = saver) { mutableStateOf(CustomizationUiState()) }
            Profile(state)
        }
        compose.onNodeWithText("Продолжить").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Введи имя спутника").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performTextInput("Искорка")
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.onNodeWithText("Песочный").performScrollTo().performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNode(hasSetTextAction()).assertTextEquals("Искорка")
        compose.onNodeWithText("Песочный").performScrollTo().assertIsSelected()
    }

    @Test fun swipingPreviewsItemsKeepsHintHeightAndRejectsLockedApply() {
        val state = mutableStateOf(AccessoryCustomizationUiState())
        var applied = 0
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(400.dp, 900.dp))) {
                val art = customizationArtwork()
                AccessoryCustomizationScreen(state.value, art, accessoryArtwork(art),
                    { state.value = state.value.copy(accessory = it) }, {}, { applied++ })
            }
        }
        val scene = compose.onNodeWithContentDescription("Рыжик: Рюкзак").fetchSemanticsNode().boundsInRoot
        val hint = compose.onNodeWithTag("accessory_hint").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithText("Применить").assertIsEnabled()
        compose.onNodeWithTag("accessory_pager").performScrollToIndex(3)
        compose.onNodeWithContentDescription("Рыжик: Фонарик").assertIsDisplayed()
        compose.onNodeWithText("Применить").assertIsNotEnabled().performClick()
        compose.runOnIdle { assertEquals(0, applied) }
        assertEquals(hint, compose.onNodeWithTag("accessory_hint").fetchSemanticsNode().boundsInRoot)
        assertEquals(scene, compose.onNodeWithContentDescription("Рыжик: Фонарик").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithText("Фонарик", substring = false).assertHasNoClickAction()
        compose.onNodeWithTag("accessory_pager").performTouchInput {
            // Move one page in a multi-card viewport, not the entire viewport width.
            swipe(center, Offset(center.x + width * .22f, center.y), durationMillis = 500)
        }
        compose.onNodeWithContentDescription("Рыжик: Бандана").assertIsDisplayed()
        compose.onNodeWithText("Применить").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, applied) }
    }

    @Test fun savingBlocksButtonsAndFailedSaveCanBeRetried() {
        val saving = mutableStateOf(true)
        var starts = 0
        var backs = 0
        compose.setContent {
            val art = customizationArtwork()
            AdventureIntroductionScreen(art, art.copper, introductionIcons(), { backs++ }, { starts++ },
                saving = saving.value, saveFailed = !saving.value)
        }
        compose.onNodeWithText("Сохраняем…").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Назад к аксессуарам").assertIsNotEnabled()
        compose.runOnIdle { saving.value = false }
        compose.onNodeWithText("Не удалось сохранить настройки. Попробуй ещё раз.").assertIsDisplayed()
        compose.onNodeWithText("Начать приключение").performTouchInput { click() }
        compose.onNodeWithContentDescription("Назад к аксессуарам").performClick()
        compose.runOnIdle { assertEquals(1, starts); assertEquals(1, backs) }
    }
}

@OptIn(ExperimentalTestApi::class)
@Composable
private fun Profile(state: MutableState<CustomizationUiState>, size: DpSize = DpSize(400.dp, 800.dp), onContinue: () -> Unit = {}) {
    DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(size)) {
        MaterialTheme {
            CustomizationScreen(state.value, customizationArtwork(),
                { state.value = state.value.copy(name = it) },
                { state.value = state.value.copy(temperament = it) },
                { state.value = state.value.copy(fur = it) }, {}, onContinue)
        }
    }
}
