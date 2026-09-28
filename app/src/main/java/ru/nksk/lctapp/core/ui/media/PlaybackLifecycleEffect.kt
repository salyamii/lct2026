package ru.nksk.lctapp.core.ui.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/** Pause from the lifecycle callback itself: a background window may stop scheduling Compose frames. */
@Composable
internal fun PlaybackLifecycleEffect(lifecycle: Lifecycle, onForegroundChanged: (Boolean) -> Unit) {
    val update by rememberUpdatedState(onForegroundChanged)
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> update(true)
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP, Lifecycle.Event.ON_DESTROY -> update(false)
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        update(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        onDispose {
            lifecycle.removeObserver(observer)
            update(false)
        }
    }
}
