package ru.nksk.lctapp.app

import androidx.compose.runtime.Composable

@Composable
internal fun AppDebugOverlay(content: @Composable ((@Composable () -> Unit)?) -> Unit) {
    content(null)
}
