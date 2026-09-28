package ru.nksk.lctapp.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.nksk.lctapp.core.ui.components.AdventureBody
import ru.nksk.lctapp.core.ui.components.GameActionButton
import ru.nksk.lctapp.core.ui.components.GameActionStyle
import ru.nksk.lctapp.core.ui.components.GameInk
import ru.nksk.lctapp.core.ui.components.GamePaper
import ru.nksk.lctapp.domain.backend.CloudRestorePreview
import ru.nksk.lctapp.domain.backend.CloudSyncPhase

@Composable
internal fun CloudSettingsCard(state: CloudSettingsUiState, configured: Boolean,
    onAction: (SettingsAction) -> Unit) {
    SettingsCard {
        Text("Облако", color = GameInk, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        AdventureBody("Сохранённая копия содержит весь мир и журнал приключения.")
        if (!configured) AdventureBody("Облако станет доступно после подключения профиля к серверу.")
        state.lastSyncedLabel?.let { AdventureBody("Последняя синхронизация: $it") }
        if (state.busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            AdventureBody(when (state.operation) {
                CloudSettingsOperation.DOWNLOAD -> "Загружаем копию из облака…"
                CloudSettingsOperation.RESTORE -> "Восстанавливаем игру…"
                CloudSettingsOperation.SYNC, null -> "Синхронизируем игру…"
            })
        } else {
            val message = state.feedback ?: state.sync.message ?: when (state.sync.phase) {
                CloudSyncPhase.OFFLINE -> "Сейчас нет связи с облаком. Попробуйте позже."
                CloudSyncPhase.ERROR -> "Не удалось синхронизировать игру. Попробуйте ещё раз."
                CloudSyncPhase.CONFLICT -> "В облаке есть другая версия игры. Сначала можно посмотреть её, а потом решить, нужно ли восстановление."
                CloudSyncPhase.IDLE, CloudSyncPhase.SYNCING -> null
            }
            message?.let { AdventureBody(it) }
        }
        val enabled = configured && !state.busy && state.restorePreview == null
        GameActionButton("Синхронизировать сейчас", { onAction(SettingsAction.SyncCloud) }, enabled = enabled)
        GameActionButton("Загрузить копию из облака", { onAction(SettingsAction.PrepareCloudRestore) },
            enabled = enabled, style = GameActionStyle.SECONDARY)
        AdventureBody("Сначала покажем сохранение. Текущая игра заменится только после подтверждения.")
    }
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
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    AdventureBody("Восстанавливаем игру…")
                }
            }
        },
        confirmButton = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GameActionButton("Восстановить эту игру", { onAction(SettingsAction.ConfirmCloudRestore(preview.id)) },
                    enabled = !busy)
                GameActionButton("Оставить текущую игру", { onAction(SettingsAction.DismissCloudRestore(preview.id)) },
                    enabled = !busy, style = GameActionStyle.SECONDARY)
            }
        },
    )
}
