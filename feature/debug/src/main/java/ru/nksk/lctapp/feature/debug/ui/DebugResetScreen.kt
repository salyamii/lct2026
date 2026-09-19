package ru.nksk.lctapp.feature.debug.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.nksk.lctapp.feature.debug.R

@Composable
fun DebugResetScreen(failed: Boolean, onRetry: () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme()) {
        Surface(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.safeDrawingPadding().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            ) {
                if (failed) {
                    Text(stringResource(R.string.debug_reset_failed))
                    Button(onClick = onRetry) { Text(stringResource(R.string.debug_retry)) }
                } else {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.debug_resetting))
                }
            }
        }
    }
}
