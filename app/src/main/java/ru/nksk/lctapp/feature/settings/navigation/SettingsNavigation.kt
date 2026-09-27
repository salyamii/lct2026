package ru.nksk.lctapp.feature.settings.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.nksk.lctapp.feature.settings.ui.SettingsScreen
import ru.nksk.lctapp.feature.settings.ui.SettingsViewModel

@Serializable
@SerialName("settings")
data object Settings : NavKey

@Suppress("DEPRECATION")
fun EntryProviderScope<NavKey>.settingsEntry(onBack: (Settings) -> Unit, debugButton: (@Composable () -> Unit)? = null) {
    entry<Settings> { source ->
        val model = hiltViewModel<SettingsViewModel>()
        val state by model.uiState.collectAsStateWithLifecycle()
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        val clipboard = LocalClipboardManager.current
        SettingsScreen(state,
            onAction = { if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) model.onAction(it) },
            onBack = dropUnlessResumed { onBack(source) },
            onCopyProfile = { id -> if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) clipboard.setText(AnnotatedString(id)) },
            debugButton = debugButton)
    }
}
