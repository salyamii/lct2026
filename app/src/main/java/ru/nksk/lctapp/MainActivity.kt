package ru.nksk.lctapp

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import ru.nksk.lctapp.app.LctApp
import ru.nksk.lctapp.data.diagnostics.AppDiagnostics
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var diagnostics: AppDiagnostics

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
            LctApp()
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
