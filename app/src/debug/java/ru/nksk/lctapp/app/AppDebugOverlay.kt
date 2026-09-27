package ru.nksk.lctapp.app

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import ru.nksk.lctapp.feature.debug.ui.DebugOverlay

@Composable
internal fun AppDebugOverlay(content: @Composable ((@Composable () -> Unit)?) -> Unit) {
    val context = LocalContext.current
    DebugOverlay(onResetProgress = {
        context.startActivity(Intent(context, DebugResetActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
    }, content = { settingsButton -> content(settingsButton) })
}
