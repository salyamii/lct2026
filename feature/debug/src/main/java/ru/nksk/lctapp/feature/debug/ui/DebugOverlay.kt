package ru.nksk.lctapp.feature.debug.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.nksk.lctapp.feature.debug.R

/** App-wide tooling with local UI state and no navigation or game dependencies. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugOverlay(
    onResetProgress: () -> Unit,
    content: @Composable (@Composable () -> Unit) -> Unit,
) {
    var isOpen by rememberSaveable { mutableStateOf(false) }

    content {
        IconButton(onClick = { isOpen = true }, modifier = Modifier.size(48.dp)) {
            Icon(
                painter = painterResource(R.drawable.debug_settings),
                contentDescription = stringResource(R.string.debug_open),
                tint = Color(0xFFF9F5FF),
                modifier = Modifier.size(20.dp),
            )
        }
    }
    MaterialTheme(colorScheme = lightColorScheme()) {
        if (isOpen) {
            ModalBottomSheet(
                onDismissRequest = { isOpen = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            ) {
                Column(
                    Modifier.fillMaxWidth().fillMaxHeight(0.4f).padding(horizontal = 24.dp),
                ) {
                    Text(
                        text = stringResource(R.string.debug_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                        item {
                            Button(onClick = onResetProgress, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.debug_reset_progress))
                            }
                        }
                    }
                }
            }
        }
    }
}
