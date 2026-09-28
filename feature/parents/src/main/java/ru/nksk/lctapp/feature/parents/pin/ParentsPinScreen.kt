package ru.nksk.lctapp.feature.parents.pin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.nksk.lctapp.feature.parents.R

/** Pure UI; the Activity renders this outside the protected navigation graph. */
@Composable
fun ParentsPinScreen(
    state: ParentsAccessUiState,
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        val title = if (state.stage == ParentsAccessStage.Create || state.stage == ParentsAccessStage.Confirm) {
            R.string.parents_pin_setup_title
        } else {
            R.string.parents_pin_lock_title
        }
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        when (state.stage) {
            ParentsAccessStage.Loading -> {
                Text(stringResource(R.string.parents_pin_loading), textAlign = TextAlign.Center)
                Spacer(Modifier.height(32.dp))
                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
            }
            ParentsAccessStage.StorageError -> {
                Text(
                    text = stringResource(R.string.parents_pin_storage_error),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(24.dp))
                OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.parents_pin_retry)) }
            }
            ParentsAccessStage.Create, ParentsAccessStage.Confirm, ParentsAccessStage.Enter -> {
                val hint = when (state.stage) {
                    ParentsAccessStage.Create -> R.string.parents_pin_setup_hint_enter
                    ParentsAccessStage.Confirm -> R.string.parents_pin_setup_hint_confirm
                    else -> R.string.parents_pin_lock_hint
                }
                Text(
                    text = stringResource(hint),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(32.dp))
                PinDots(state.enteredDigits)
                val message = when {
                    state.retryAfterSeconds > 0 -> stringResource(R.string.parents_pin_wait, state.retryAfterSeconds)
                    state.message == ParentsPinMessage.Mismatch -> stringResource(R.string.parents_pin_setup_mismatch)
                    state.message == ParentsPinMessage.WrongPin -> stringResource(R.string.parents_pin_lock_wrong)
                    else -> ""
                }
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Spacer(Modifier.height(24.dp))
                PinPad(state.acceptsDigits, onDigit, onDelete)
                if (state.busy) {
                    Spacer(Modifier.height(16.dp))
                    CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                }
            }
            ParentsAccessStage.Unlocked -> Unit
        }
        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onClose) { Text(stringResource(R.string.parents_pin_close)) }
    }
}

@Composable
private fun PinDots(entered: Int) {
    val description = stringResource(R.string.parents_pin_entered, entered)
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.clearAndSetSemantics { contentDescription = description },
    ) {
        repeat(PIN_LENGTH) { index ->
            Box(
                Modifier.size(14.dp).clip(CircleShape).background(
                    if (index < entered) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
                ),
            )
        }
    }
}

@Composable
private fun PinPad(enabled: Boolean, onDigit: (Char) -> Unit, onDelete: () -> Unit) {
    Column(
        modifier = Modifier.widthIn(max = 288.dp).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        listOf("123", "456", "789", " 0⌫").forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                row.forEach { key ->
                    when (key) {
                        ' ' -> Spacer(Modifier.size(64.dp))
                        '⌫' -> {
                            val description = stringResource(R.string.parents_pin_delete)
                            TextButton(
                                onClick = onDelete,
                                enabled = enabled,
                                modifier = Modifier.size(64.dp),
                                contentPadding = PaddingValues(0.dp),
                            ) {
                                Text(
                                    text = "⌫",
                                    style = MaterialTheme.typography.headlineSmall,
                                    modifier = Modifier.clearAndSetSemantics { contentDescription = description },
                                )
                            }
                        }
                        else -> OutlinedButton(
                            onClick = { onDigit(key) },
                            enabled = enabled,
                            modifier = Modifier.size(64.dp),
                            shape = CircleShape,
                            contentPadding = PaddingValues(0.dp),
                        ) {
                            Text(key.toString(), style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                }
            }
        }
    }
}
