package ru.nksk.lctapp.app

import androidx.compose.runtime.Composable
import ru.nksk.lctapp.app.navigation.LctNavHost
import ru.nksk.lctapp.core.ui.theme.LCTAppTheme

/** Composition root for shared presentation and app-owned navigation. */
@Composable
fun LctApp() {
    LCTAppTheme {
        LctNavHost()
    }
}
