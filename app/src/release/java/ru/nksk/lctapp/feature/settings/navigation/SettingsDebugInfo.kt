package ru.nksk.lctapp.feature.settings.navigation

import androidx.compose.runtime.Composable
import ru.nksk.lctapp.BuildConfig
import ru.nksk.lctapp.feature.settings.ui.CloudSettingsCard
import ru.nksk.lctapp.feature.settings.ui.SettingsAction
import ru.nksk.lctapp.feature.settings.ui.SettingsUiState

@Suppress("UNUSED_PARAMETER")
@Composable
internal fun SettingsDebugInfo(settings: SettingsUiState, onAction: (SettingsAction) -> Unit,
    onCopy: (String) -> Unit) {
    if (BuildConfig.JURY_TOOLS) {
        CloudSettingsCard(settings.cloud,
            configured = settings.backendConfigured && !settings.loading && !settings.profileError,
            onAction = onAction)
    }
}
