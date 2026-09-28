package ru.nksk.lctapp.app.parents

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import ru.nksk.lctapp.feature.parents.pin.ParentsAccessViewModel
import ru.nksk.lctapp.feature.parents.pin.ParentsPinScreen
import ru.nksk.lctapp.feature.parents.theme.ParentsTheme

/** Internal same-process UI boundary; no game state or unlock token is accepted from Intent. */
@AndroidEntryPoint
class ParentsActivity : ComponentActivity() {
    private val access by lazy { ViewModelProvider(this)[ParentsAccessViewModel::class.java] }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Do not expose a previously unlocked report in the recents thumbnail.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
        setContent {
            val state by access.uiState.collectAsStateWithLifecycle()
            ParentsTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    // The entire restored navigation tree stays behind the in-memory access gate.
                    if (state.unlocked) ParentsNavHost(onClose = ::closeParentMode)
                    else ParentsPinScreen(state = state, onDigit = access::onDigit,
                        onDelete = access::onDelete, onRetry = access::retry, onClose = ::closeParentMode)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        access.onForeground()
    }

    override fun onStop() {
        if (!isChangingConfigurations) access.lock()
        super.onStop()
    }

    private fun closeParentMode() {
        access.lock()
        finish()
    }
}
