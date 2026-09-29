package ru.nksk.lctapp

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.nksk.lctapp.core.ui.theme.LCTAppTheme
import ru.nksk.lctapp.feature.settings.ui.*
import ru.nksk.lctapp.domain.backend.CloudRestorePreview

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun missingBackendDoesNotDisableTheShowCodeButton() {
        var action: SettingsAction? = null
        compose.setContent { LCTAppTheme {
            SettingsScreen(SettingsUiState(loading = false, profileId = ProfileId), { action = it }, {}, {})
        } }
        compose.onNodeWithText("Показать код для родителей").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(SettingsAction.CreateParentCode, action) }
        compose.onNodeWithText("Инструменты разработчика").assertDoesNotExist()
    }

    @Test fun localQrRemainsVisibleWithNoConfiguredServer() {
        compose.setContent { LCTAppTheme {
            SettingsScreen(readyState(), {}, {}, {})
        } }
        compose.onNodeWithContentDescription("Код для подключения родителя").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Родитель сможет подключиться, когда заработает сервер.")
            .performScrollTo().assertIsDisplayed()
    }

    @Test fun qrCanBeSharedOfflineWithoutRetryingRegistration() {
        var shares = 0
        compose.setContent { LCTAppTheme {
            SettingsScreen(readyState(), { error("Sharing must not request registration") }, {}, {},
                onShareCode = { shares++ })
        } }
        compose.onNodeWithText("Поделиться QR-кодом").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, shares) }
    }

    @Test fun registrationFailureKeepsTheQrAndOffersOnlyRegistrationRetry() {
        var action: SettingsAction? = null
        compose.setContent { LCTAppTheme {
            SettingsScreen(readyState().copy(backendConfigured = true,
                registrationStatus = ProfileRegistrationStatus.ERROR), { action = it }, {}, {})
        } }
        compose.onNodeWithContentDescription("Код для подключения родителя").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Повторить подключение").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(SettingsAction.RetryRegistration, action) }
    }

    @Test fun profileCanBeCopiedOnCompactScreensWithLargeText() {
        var copied: String? = null
        compose.setContent { LCTAppTheme {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(320.dp, 568.dp)) then
                DeviceConfigurationOverride.FontScale(1.5f)) {
                SettingsScreen(SettingsUiState(loading = false, profileId = ProfileId), {}, {}, { copied = it })
            }
        } }
        compose.onNodeWithText("Скопировать номер").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(ProfileId, copied) }
        compose.onNodeWithText("Скопировано").assertIsDisplayed()
    }

    @Test fun settingsGearIsAvailableWithoutADebugSlot() {
        var opened = 0
        compose.setContent { LCTAppTheme { SettingsGearButton(onClick = { opened++ }) } }
        compose.onNodeWithContentDescription("Настройки").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, opened) }
    }

    @Test fun longPressOpensParentsWithoutAlsoOpeningSettings() {
        var settings = 0
        var parents = 0
        compose.setContent { LCTAppTheme {
            SettingsGearButton(onClick = { settings++ }, onLongClick = { parents++ })
        } }
        compose.onNodeWithContentDescription("Настройки").performTouchInput { longClick() }
        compose.runOnIdle {
            assertEquals(0, settings)
            assertEquals(1, parents)
        }
        compose.onNodeWithContentDescription("Настройки").performClick()
        compose.runOnIdle {
            assertEquals(1, settings)
            assertEquals(1, parents)
        }
    }

    @Test fun cloudRestoreRequiresAnExplicitChoiceAfterShowingWhatWillBeReplaced() {
        var action: SettingsAction? = null
        compose.setContent { LCTAppTheme {
            SettingsScreen(readyState().copy(backendConfigured = true,
                cloud = CloudSettingsUiState(restorePreview = CloudRestorePreview("copy-1", "Лис", 4, 21L, 8L))),
                { action = it }, {}, {})
        } }
        compose.onNodeWithText("Сохранение из облака заменит текущий мир, его журнал и прогресс на этом устройстве.")
            .assertIsDisplayed()
        compose.runOnIdle { assertEquals(null, action) }
        compose.onNodeWithText("Восстановить эту игру").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(SettingsAction.ConfirmCloudRestore("copy-1"), action) }
    }

    @Test fun restoringDisablesBothConfirmationActions() {
        compose.setContent { LCTAppTheme {
            SettingsScreen(readyState().copy(backendConfigured = true,
                cloud = CloudSettingsUiState(operation = CloudSettingsOperation.RESTORE,
                    restorePreview = CloudRestorePreview("copy-1", "Лис", 4, 21L, 8L))),
                { error("Restore controls must be blocked") }, {}, {})
        } }
        compose.onNodeWithText("Восстановить эту игру").assertIsNotEnabled()
        compose.onNodeWithText("Оставить текущую игру").assertIsNotEnabled()
    }

    @Test fun diagnosticsCanBeSavedBeforeProfileLoadsWithoutCloudSetup() {
        var requests = 0
        compose.setContent { LCTAppTheme {
            SettingsScreen(SettingsUiState(), {}, {}, {}, onDownloadDiagnostics = { requests++ })
        } }
        compose.onNodeWithText("Скачать журнал").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, requests) }
    }

    @Test fun diagnosticsPickerPreventsRepeatedDownloadTaps() {
        compose.setContent { LCTAppTheme {
            SettingsScreen(SettingsUiState(), {}, {}, {}, pickingDiagnostics = true,
                onDownloadDiagnostics = { error("The picker is already open") })
        } }
        compose.onNodeWithText("Скачать журнал").performScrollTo().assertIsNotEnabled()
    }

    @Test fun diagnosticsWritePreventsRepeatedDownloadAndShowsItsProgress() {
        compose.setContent { LCTAppTheme {
            SettingsScreen(SettingsUiState(diagnostics = DiagnosticsUiState(saving = true)), {}, {}, {},
                onDownloadDiagnostics = { error("The export is already running") })
        } }
        compose.onNodeWithText("Скачать журнал").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Сохраняем журнал…").performScrollTo().assertIsDisplayed()
    }

    @Test fun completedDiagnosticsExportShowsConfirmation() {
        compose.setContent { LCTAppTheme {
            SettingsScreen(SettingsUiState(diagnostics = DiagnosticsUiState(result = DiagnosticsExportResult.SAVED)), {}, {}, {})
        } }
        compose.onNodeWithText("Журнал сохранён.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Скачать журнал").assertIsEnabled()
    }

    private fun readyState() = SettingsUiState(loading = false, profileId = ProfileId,
        codeStatus = ParentCodeStatus.READY, qr = ParentLinkQrMatrix(21, List(21 * 21) { true }))
    private companion object { const val ProfileId = "ad64c0e4-2731-4c1e-9fc9-bac812fd5995" }
}
