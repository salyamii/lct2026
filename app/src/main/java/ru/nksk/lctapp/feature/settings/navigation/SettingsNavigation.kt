package ru.nksk.lctapp.feature.settings.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import ru.nksk.lctapp.feature.settings.sharing.createParentCodeShareIntent
import ru.nksk.lctapp.feature.settings.ui.SettingsScreen
import ru.nksk.lctapp.feature.settings.ui.SettingsViewModel
import ru.nksk.lctapp.feature.settings.ui.SettingsAction
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var sharingCode by remember { mutableStateOf(false) }
        var shareError by remember { mutableStateOf(false) }
        var pickingDiagnostics by rememberSaveable { mutableStateOf(false) }
        val diagnosticsFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            pickingDiagnostics = false
            // The result can arrive before this entry is RESUMED. The chosen document still belongs to this request.
            uri?.let { model.onAction(SettingsAction.ExportDiagnostics(it.toString())) }
        }
        SettingsScreen(state,
            onAction = { if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) model.onAction(it) },
            onBack = dropUnlessResumed { onBack(source) },
            onCopyProfile = { id -> if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) clipboard.setText(AnnotatedString(id)) },
            debugButton = debugButton,
            sharingCode = sharingCode,
            shareError = shareError,
            pickingDiagnostics = pickingDiagnostics,
            onDownloadDiagnostics = {
                if (!pickingDiagnostics && !model.uiState.value.diagnostics.saving &&
                    lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    pickingDiagnostics = true
                    model.onAction(SettingsAction.PrepareDiagnosticsExport)
                    try {
                        val timestamp = SimpleDateFormat("yyyy-MM-dd-HHmmss", Locale.ROOT).format(Date())
                        diagnosticsFile.launch("paws-coins-diagnostics-$timestamp.txt")
                    } catch (cancelled: CancellationException) {
                        pickingDiagnostics = false
                        throw cancelled
                    } catch (_: RuntimeException) {
                        pickingDiagnostics = false
                        model.onAction(SettingsAction.DiagnosticsPickerFailed)
                    }
                }
            },
            onShareCode = {
                val qr = state.qr
                if (qr != null && !sharingCode && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    sharingCode = true
                    shareError = false
                    scope.launch {
                        try {
                            val send = createParentCodeShareIntent(context.applicationContext, qr)
                            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                                context.startActivity(Intent.createChooser(send, "Поделиться QR-кодом"))
                            }
                        } catch (cancelled: CancellationException) { throw cancelled
                        } catch (_: Exception) { shareError = true
                        } finally { sharingCode = false }
                    }
                }
            })
    }
}
