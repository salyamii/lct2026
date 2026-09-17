package ru.nksk.lctapp

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ru.nksk.lctapp.feature.menu.ui.MainMenuScreen
import ru.nksk.lctapp.feature.menu.ui.MainMenuDemoState
import ru.nksk.lctapp.core.ui.theme.LCTAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        setContent {
            LCTAppTheme {
                MainMenuScreen(state = MainMenuDemoState, onAction = {})
            }
        }
    }
}
