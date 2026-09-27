package ru.nksk.lctapp.feature.settings.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.components.*
import ru.nksk.lctapp.core.ui.theme.AdventureNight
import ru.nksk.lctapp.core.ui.theme.AdventureLime

@Composable
internal fun SettingsGearButton(onClick: () -> Unit) {
    IconButton(onClick, Modifier.size(48.dp)) {
        Icon(painterResource(R.drawable.settings_gear), "Настройки", Modifier.size(20.dp), tint = Color.White)
    }
}

@Composable
internal fun SettingsScreen(state: SettingsUiState, onAction: (SettingsAction) -> Unit, onBack: () -> Unit,
    onCopyProfile: (String) -> Unit, debugButton: (@Composable () -> Unit)? = null,
    onShareCode: () -> Unit = {}, sharingCode: Boolean = false, shareError: Boolean = false) {
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
            SoundSettingsCard(state.sound, onAction)
            SettingsCard {
                Text("Для родителей", color = GameInk, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                AdventureBody("Подключите родительский профиль, чтобы видеть, чему учится ребёнок.")
                if (state.loading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
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
                    Text("Номер профиля", color = GameInk, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(profileId, color = GameInk, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
                    OutlinedButton(onClick = { onCopyProfile(profileId); copied = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = GameInk)) {
                        Text(if (copied) "Скопировано" else "Скопировать номер")
                    }
                }
            }
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
            LinearProgressIndicator(Modifier.fillMaxWidth())
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
            LinearProgressIndicator(Modifier.fillMaxWidth())
            AdventureBody("Готовим код…")
        }
        ParentCodeStatus.READY -> {
            AdventureBody("Отсканируйте этот код в приложении для родителей.")
            state.qr?.let { qr ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { ParentLinkQrCode(qr) }
                AdventurePrimaryButton(if (sharingCode) "Готовим изображение…" else "Поделиться QR-кодом",
                    onShareCode, enabled = !sharingCode)
                if (shareError) AdventureBody("Не удалось открыть отправку. Попробуйте ещё раз.")
            }
            if (!state.backendConfigured) {
                AdventureBody("Подключение родителей станет доступно после подключения сервера.")
            } else when (state.registrationStatus) {
                ProfileRegistrationStatus.LOADING -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    AdventureBody("Регистрируем профиль на сервере…")
                }
                ProfileRegistrationStatus.ERROR -> {
                    AdventureBody("Не удалось зарегистрировать профиль. Код сохранён — попробуйте ещё раз.")
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
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), color = Color.White, shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, GameInk.copy(alpha = .12f))) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}
