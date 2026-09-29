package ru.nksk.lctapp.feature.settings.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.components.*
import ru.nksk.lctapp.core.ui.theme.AdventureNight
import ru.nksk.lctapp.core.ui.theme.AdventureLime

@Composable
internal fun SettingsGearButton(onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    Box(Modifier.size(48.dp).clip(CircleShape).combinedClickable(
        role = Role.Button,
        onClick = onClick,
        onLongClick = onLongClick,
        onLongClickLabel = stringResource(R.string.parents_open_mode),
    ), contentAlignment = Alignment.Center) {
        Icon(painterResource(R.drawable.settings_gear), "Настройки", Modifier.size(20.dp), tint = Color.White)
    }
}

@Composable
internal fun SettingsScreen(state: SettingsUiState, onAction: (SettingsAction) -> Unit, onBack: () -> Unit,
    onCopyProfile: (String) -> Unit, debugButton: (@Composable () -> Unit)? = null,
    onShareCode: () -> Unit = {}, sharingCode: Boolean = false, shareError: Boolean = false,
    onDownloadDiagnostics: () -> Unit = {}, pickingDiagnostics: Boolean = false,
    onRepeatTutorial: (() -> Unit)? = null) {
    Column(Modifier.fillMaxSize().background(AdventureNight).safeDrawingPadding().background(GamePaper)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onBack, Modifier.size(48.dp)) {
                Icon(painterResource(R.drawable.menu_chevron), "Назад", Modifier.size(20.dp).rotate(180f), tint = GameInk)
            }
            AdventureHeading("Настройки")
        }
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            onRepeatTutorial?.let { repeat ->
                SettingsCard {
                    Text("Обучение", color = GameInk, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    AdventureBody("Подсказки о кнопках и возможностях главного меню.")
                    AdventurePrimaryButton("Повторить обучение", repeat)
                }
            }
            SoundSettingsCard(state.sound, onAction)
            DemoSettingsCard(state.demo, onAction)
            DiagnosticsSettingsCard(state.diagnostics, pickingDiagnostics, onDownloadDiagnostics)
            SettingsCard {
                Text("Для родителей", color = GameInk, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                AdventureBody("Покажите код родителю, чтобы он мог видеть прогресс и присылать подарки.")
                if (state.loading) {
                    GameLoadingIndicator(Modifier.fillMaxWidth(), size = 40.dp)
                    AdventureBody("Открываем настройки…")
                } else if (state.profileError) {
                    AdventureBody("Не удалось открыть профиль. Попробуйте ещё раз.")
                    AdventurePrimaryButton("Повторить", { onAction(SettingsAction.RetryProfile) })
                } else {
                    ParentCodeContent(state, onAction, onShareCode, sharingCode, shareError)
                }
            }
            state.profileId?.let { profileId ->
                var copied by rememberSaveable(profileId) { mutableStateOf(false) }
                SettingsCard {
                    Text("Номер устройства", color = GameInk, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(profileId, color = GameInk, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
                    OutlinedButton(onClick = { onCopyProfile(profileId); copied = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = GameInk)) {
                        Text(if (copied) "Скопировано" else "Скопировать номер")
                    }
                }
            }
            CloudSettingsCard(state.cloud,
                configured = state.backendConfigured && !state.loading && !state.profileError,
                onAction = onAction)
            debugButton?.let {
                SettingsCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Инструменты разработчика", Modifier.weight(1f), color = GameInk,
                            style = MaterialTheme.typography.titleMedium)
                        Surface(shape = CircleShape, color = AdventureNight) { it() }
                    }
                }
            }
        }
    }
    state.cloud.restorePreview?.let { preview ->
        CloudRestoreDialog(preview, busy = state.cloud.busy, onAction = onAction)
    }
}

@Composable
private fun DemoSettingsCard(state: DemoSettingsUiState, onAction: (SettingsAction) -> Unit) {
    SettingsCard {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .toggleable(value = state.enabled == true, enabled = state.canChange, role = Role.Switch,
                onValueChange = { onAction(SettingsAction.SetDemoModeEnabled(it)) }),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Режим бога", color = GameInk, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                AdventureBody("Для показа: питомец не устаёт, все предметы и еда бесплатны.")
            }
            Switch(checked = state.enabled == true, onCheckedChange = null, enabled = state.canChange,
                colors = SwitchDefaults.colors(checkedTrackColor = AdventureLime, checkedThumbColor = GameInk,
                    uncheckedTrackColor = GamePaper, uncheckedThumbColor = GameInk,
                    uncheckedBorderColor = GameInk.copy(alpha = .35f)))
        }
        AdventureBody("Прогресс и полученные предметы остаются в этой игре после выключения режима.")
        if (state.loading || state.saving) {
            AdventureBody(if (state.saving) "Сохраняем…" else "Открываем настройку режима…")
        }
        state.error?.let { error ->
            AdventureBody(if (error == DemoSettingsError.READ) "Не удалось прочитать настройку режима. Попробуйте ещё раз."
                else "Не удалось сохранить настройку режима. Попробуйте ещё раз.")
            GameActionButton("Повторить", { onAction(SettingsAction.RetryDemoMode) }, enabled = !state.saving,
                style = GameActionStyle.SECONDARY)
        }
    }
}

@Composable
private fun DiagnosticsSettingsCard(state: DiagnosticsUiState, picking: Boolean, onDownload: () -> Unit) {
    SettingsCard {
        Text("Журнал ошибок", color = GameInk, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        AdventureBody("Сохрани журнал, чтобы передать его для разбора ошибок.")
        GameActionButton("Скачать журнал", onDownload, enabled = !picking && !state.saving)
        val message = when {
            picking -> "Выбираем, куда сохранить журнал…"
            state.saving -> "Сохраняем журнал…"
            state.result == DiagnosticsExportResult.SAVED -> "Журнал сохранён."
            state.result == DiagnosticsExportResult.EXPORT_FAILED -> "Не удалось сохранить журнал. Выбери другое место и попробуй ещё раз."
            state.result == DiagnosticsExportResult.PICKER_FAILED -> "Не удалось открыть выбор файла. Попробуй ещё раз."
            else -> null
        }
        message?.let {
            Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                color = GameInk, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SoundSettingsCard(state: SoundSettingsUiState, onAction: (SettingsAction) -> Unit) {
    SettingsCard {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .toggleable(value = state.enabled == true, enabled = state.canChange, role = Role.Switch,
                onValueChange = { onAction(SettingsAction.SetSoundEnabled(it)) }),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Звук", color = GameInk, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                AdventureBody("Музыка, голоса, звуки событий и видео")
            }
            Switch(checked = state.enabled == true, onCheckedChange = null, enabled = state.canChange,
                colors = SwitchDefaults.colors(checkedTrackColor = AdventureLime, checkedThumbColor = GameInk,
                    uncheckedTrackColor = GamePaper, uncheckedThumbColor = GameInk,
                    uncheckedBorderColor = GameInk.copy(alpha = .35f)))
        }
        if (state.loading || state.saving) {
            GameLoadingIndicator(Modifier.fillMaxWidth(), size = 40.dp)
            AdventureBody(if (state.saving) "Сохраняем…" else "Открываем настройку звука…")
        }
        state.error?.let { error ->
            AdventureBody(if (error == SoundSettingsError.READ)
                "Не удалось прочитать настройку звука. Попробуйте ещё раз."
            else "Не удалось сохранить настройку звука. Попробуйте ещё раз.")
            GameActionButton("Повторить", { onAction(SettingsAction.RetrySound) }, enabled = !state.saving,
                style = GameActionStyle.SECONDARY)
        }
    }
}

@Composable
private fun ParentCodeContent(state: SettingsUiState, onAction: (SettingsAction) -> Unit,
    onShareCode: () -> Unit, sharingCode: Boolean, shareError: Boolean) {
    when (state.codeStatus) {
        ParentCodeStatus.NONE -> AdventurePrimaryButton("Показать код для родителей", { onAction(SettingsAction.CreateParentCode) })
        ParentCodeStatus.LOADING -> {
            GameLoadingIndicator(Modifier.fillMaxWidth(), size = 40.dp)
            AdventureBody("Готовим код…")
        }
        ParentCodeStatus.READY -> {
            AdventureBody("Отсканируйте этот код в приложении для родителей.")
            state.qr?.let { qr ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { ParentLinkQrCode(qr) }
                AdventurePrimaryButton(if (sharingCode) "Готовим изображение…" else "Поделиться QR-кодом",
                    onShareCode, enabled = !sharingCode)
                if (shareError) AdventureBody("Не удалось поделиться кодом. Попробуйте ещё раз.")
            }
            if (!state.backendConfigured) {
                AdventureBody("Родитель сможет подключиться, когда заработает сервер.")
            } else when (state.registrationStatus) {
                ProfileRegistrationStatus.LOADING -> {
                    GameLoadingIndicator(Modifier.fillMaxWidth(), size = 40.dp)
                    AdventureBody("Регистрируем профиль на сервере…")
                }
                ProfileRegistrationStatus.ERROR -> {
                    AdventureBody("Не удалось зарегистрировать профиль. Код сохранён - попробуйте ещё раз.")
                    OutlinedButton(onClick = { onAction(SettingsAction.RetryRegistration) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = GameInk)) {
                        Text("Повторить подключение")
                    }
                }
                ProfileRegistrationStatus.NONE, ProfileRegistrationStatus.REGISTERED -> Unit
            }
        }
        ParentCodeStatus.ERROR -> {
            AdventureBody("Не удалось показать код. Попробуйте ещё раз.")
            AdventurePrimaryButton("Показать код для родителей", { onAction(SettingsAction.CreateParentCode) })
        }
    }
}

@Composable
internal fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), color = Color.White, shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, GameInk.copy(alpha = .12f))) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}
