package ru.nksk.lctapp.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import ru.nksk.lctapp.app.navigation.LctNavHost
import ru.nksk.lctapp.core.ui.theme.LCTAppTheme

/** Composition root for shared presentation and app-owned navigation. */
@Composable
fun LctApp() {
    Box(Modifier.fillMaxSize()) {
        LCTAppTheme {
            LctNavHost()
        }
        AppDebugOverlay()
    }
}
