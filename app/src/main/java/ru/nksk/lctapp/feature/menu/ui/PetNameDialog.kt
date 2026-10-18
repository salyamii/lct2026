package ru.nksk.lctapp.feature.menu.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import ru.nksk.lctapp.core.ui.components.GameActionButton
import ru.nksk.lctapp.core.ui.components.GameActionStyle
import ru.nksk.lctapp.core.ui.components.GameInk
import ru.nksk.lctapp.core.ui.components.GamePaper

@Composable
internal fun PetNameDialog(
    state: PetNameEditorUiState,
    busy: Boolean,
    onChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val backgroundWindow = remember(context) { context.hostActivity()?.window }
    DisposableEffect(backgroundWindow) {
        if (backgroundWindow == null) return@DisposableEffect onDispose {}
        val previousMode = backgroundWindow.attributes.softInputMode
        backgroundWindow.setSoftInputMode(
            (previousMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST.inv()) or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING,
        )
        // The dialog keeps its own keyboard avoidance; other screens retain the Activity policy.
        onDispose { backgroundWindow.setSoftInputMode(previousMode) }
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = { if (!busy && !state.retryPending) onDismiss() },
        containerColor = GamePaper,
        titleContentColor = GameInk,
        textContentColor = GameInk,
        title = { Text("Имя спутника") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.input,
                    onValueChange = onChange,
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    enabled = !busy && !state.retryPending,
                    singleLine = true,
                    label = { Text("Имя") },
                    isError = state.error != null,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (!busy) onSave() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = GameInk, unfocusedTextColor = GameInk,
                        disabledTextColor = GameInk.copy(alpha = .65f),
                        focusedLabelColor = GameInk, unfocusedLabelColor = GameInk.copy(alpha = .65f),
                        cursorColor = GameInk, focusedBorderColor = GameInk,
                        unfocusedBorderColor = GameInk.copy(alpha = .35f),
                    ),
                )
                state.error?.let { Text(it) }
            }
        },
        confirmButton = {
            GameActionButton(if (state.retryPending) "Повторить" else "Сохранить", onSave,
                interactionBlocked = busy)
        },
        dismissButton = {
            GameActionButton("Отмена", onDismiss, enabled = !state.retryPending,
                interactionBlocked = busy, style = GameActionStyle.QUIET)
        },
    )
}

private tailrec fun Context.hostActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> if (baseContext !== this) baseContext.hostActivity() else null
    else -> null
}
