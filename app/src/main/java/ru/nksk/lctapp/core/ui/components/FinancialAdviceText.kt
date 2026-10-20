package ru.nksk.lctapp.core.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import ru.nksk.lctapp.core.ui.theme.Nunito

/** Inline copy only. A fully visible reminder may report existing learning support once. */
@Composable
internal fun FinancialAdviceText(text: String, modifier: Modifier = Modifier, color: Color = GameInk,
    exposureKey: String = text, onShown: () -> Unit = {}) {
    var visible by remember(exposureKey) { mutableStateOf(false) }
    var reported by remember(exposureKey) { mutableStateOf(false) }
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    LaunchedEffect(exposureKey, visible, lifecycle) {
        if (!reported && visible && lifecycle.isAtLeast(Lifecycle.State.RESUMED)) {
            reported = true
            onShown()
        }
    }
    Text(text, modifier.animateGameContentSize().onGloballyPositioned { coordinates ->
        val bounds = coordinates.boundsInWindow()
        visible = bounds.width > 0 && bounds.height > 0 &&
            bounds.width >= coordinates.size.width - 1 && bounds.height >= coordinates.size.height - 1
    }, color = color, fontFamily = Nunito, fontSize = 15.sp, lineHeight = 21.sp)
}
