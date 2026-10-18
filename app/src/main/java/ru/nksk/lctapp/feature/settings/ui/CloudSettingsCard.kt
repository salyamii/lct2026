package ru.nksk.lctapp.feature.settings.ui

import ru.nksk.lctapp.core.ui.components.GameLoadingIndicator
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.nksk.lctapp.core.ui.components.AdventureBody
import ru.nksk.lctapp.core.ui.components.GameActionButton
import ru.nksk.lctapp.core.ui.components.GameActionButtonDefaults
import ru.nksk.lctapp.core.ui.components.GameActionStyle
import ru.nksk.lctapp.core.ui.components.GameInk
import ru.nksk.lctapp.core.ui.components.GamePaper
import ru.nksk.lctapp.domain.backend.CloudRestorePreview
import ru.nksk.lctapp.domain.backend.CloudLocalUploadPreview
import ru.nksk.lctapp.domain.backend.CloudSyncPhase

@Composable
internal fun CloudSettingsCard(state: CloudSettingsUiState, configured: Boolean,
    onAction: (SettingsAction) -> Unit) {
    SettingsCard {
        Text("Облако", color = GameInk, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        AdventureBody("Копия текущей игры хранится на сервере.")
        if (!configured) AdventureBody("Облако станет доступно после подключения профиля к серверу.")
        state.lastSyncedLabel?.let { AdventureBody("Последняя синхронизация: $it") }
        if (state.busy) {
            GameLoadingIndicator(Modifier.fillMaxWidth(), size = 40.dp)
            AdventureBody(when (state.operation) {
                CloudSettingsOperation.DOWNLOAD -> "Загружаем копию из облака…"
                CloudSettingsOperation.RESTORE -> "Восстанавливаем игру…"
                CloudSettingsOperation.PREPARE_UPLOAD -> "Проверяем облачную копию…"
                CloudSettingsOperation.UPLOAD_LOCAL -> "Отправляем текущую игру…"
                CloudSettingsOperation.SYNC, null -> "Синхронизируем игру…"
            })
        } else {
            val message = state.feedback ?: state.sync.message ?: when (state.sync.phase) {
                CloudSyncPhase.OFFLINE -> "Сейчас нет связи с облаком. Попробуйте позже."
                CloudSyncPhase.ERROR -> "Не удалось синхронизировать игру. Попробуйте ещё раз."
                CloudSyncPhase.CONFLICT -> "Облачная копия отличается. Можно отправить текущую игру или посмотреть сохранение с сервера."
                CloudSyncPhase.IDLE, CloudSyncPhase.SYNCING -> null
            }
            message?.let { AdventureBody(it) }
        }
        val enabled = configured && !state.busy && state.restorePreview == null && state.localUploadPreview == null
        Column(verticalArrangement = Arrangement.spacedBy(GameActionButtonDefaults.Spacing)) {
            GameActionButton("Синхронизировать сейчас", { onAction(SettingsAction.SyncCloud) }, enabled = enabled)
            GameActionButton("Загрузить копию из облака", { onAction(SettingsAction.PrepareCloudRestore) },
                enabled = enabled, style = GameActionStyle.SECONDARY)
            GameActionButton("Заменить облачную копию текущей игрой", { onAction(SettingsAction.PrepareLocalUpload) },
                enabled = enabled, style = GameActionStyle.SECONDARY)
        }
        AdventureBody("Загрузка сначала откроет предпросмотр. Замена облачной копии сохранит текущую игру на устройстве.")
    }
}

@Composable
internal fun CloudLocalUploadDialog(preview: CloudLocalUploadPreview, busy: Boolean, onAction: (SettingsAction) -> Unit) {
    AlertDialog(
        onDismissRequest = { if (!busy) onAction(SettingsAction.DismissLocalUpload(preview.id)) },
        containerColor = GamePaper, titleContentColor = GameInk, textContentColor = GameInk,
        title = { Text("Заменить облачную копию?") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AdventureBody("Текущая игра «${preview.petName}» останется на устройстве и будет отправлена на сервер.")
            preview.cloudPetName?.let { AdventureBody("Она заменит облачное сохранение «$it». Прежний облачный прогресс будет заменён.") }
            if (busy) GameLoadingIndicator(Modifier.fillMaxWidth(), size = 40.dp)
        } },
        confirmButton = { Column(verticalArrangement = Arrangement.spacedBy(GameActionButtonDefaults.Spacing)) {
            GameActionButton("Отправить текущую игру", { onAction(SettingsAction.ConfirmLocalUpload(preview.id)) }, enabled = !busy)
            GameActionButton("Отмена", { onAction(SettingsAction.DismissLocalUpload(preview.id)) }, enabled = !busy,
                style = GameActionStyle.SECONDARY)
        } },
    )
}

@Composable
internal fun CloudRestoreDialog(preview: CloudRestorePreview, busy: Boolean,
    onAction: (SettingsAction) -> Unit) {
    AlertDialog(
        onDismissRequest = { if (!busy) onAction(SettingsAction.DismissCloudRestore(preview.id)) },
        containerColor = GamePaper,
        titleContentColor = GameInk,
        textContentColor = GameInk,
        title = { Text("Восстановить игру?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AdventureBody(preview.petName)
                preview.day?.let { AdventureBody("День $it") }
                AdventureBody("Монеты с собой: ${preview.availableCoins}. В копилке: ${preview.savingsCoins}.")
                AdventureBody("Сохранение из облака заменит текущий мир, его журнал и прогресс на этом устройстве.")
                if (busy) {
                    GameLoadingIndicator(Modifier.fillMaxWidth(), size = 40.dp)
                    AdventureBody("Восстанавливаем игру…")
                }
            }
        },
        confirmButton = {
            Column(verticalArrangement = Arrangement.spacedBy(GameActionButtonDefaults.Spacing)) {
                GameActionButton("Восстановить эту игру", { onAction(SettingsAction.ConfirmCloudRestore(preview.id)) },
                    enabled = !busy)
                GameActionButton("Оставить текущую игру", { onAction(SettingsAction.DismissCloudRestore(preview.id)) },
                    enabled = !busy, style = GameActionStyle.SECONDARY)
            }
        },
    )
}
