package ru.nksk.lctapp.feature.debug.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import ru.nksk.lctapp.feature.debug.R

/** App-wide tooling with local UI state and no navigation or game dependencies. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugOverlay(onResetProgress: () -> Unit) {
    var isOpen by rememberSaveable { mutableStateOf(false) }

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
        } else {
            Popup(
                alignment = Alignment.CenterEnd,
                properties = PopupProperties(focusable = false),
            ) {
                FilledIconButton(
                    onClick = { isOpen = true },
                    modifier = Modifier.safeDrawingPadding().padding(12.dp).size(40.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color(0xFFD0D0D0),
                        contentColor = Color(0xFF333333),
                    ),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.debug_settings),
                        contentDescription = stringResource(R.string.debug_open),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}
