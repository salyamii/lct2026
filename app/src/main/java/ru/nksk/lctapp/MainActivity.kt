package ru.nksk.lctapp

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint
import ru.nksk.lctapp.app.LctApp
import ru.nksk.lctapp.data.diagnostics.AppDiagnostics
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var diagnostics: AppDiagnostics
    private var splashVisible by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Configuration recreation has no starting window / exit callback to wait for.
        splashVisible = savedInstanceState == null
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        // Swap the identical static frame for Compose without the default icon exit motion.
        splash.setOnExitAnimationListener { provider ->
            provider.remove()
            splashVisible = false
        }
        setContent {
            LctApp(startupAnimationPlaying = !splashVisible)
        }
    }

    override fun onStart() {
        super.onStart()
        diagnostics.updateContext("lifecycle", "foreground")
    }

    override fun onStop() {
        diagnostics.updateContext("lifecycle", "background")
        super.onStop()
    }
}
