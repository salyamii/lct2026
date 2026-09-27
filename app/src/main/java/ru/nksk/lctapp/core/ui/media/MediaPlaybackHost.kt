package ru.nksk.lctapp.core.ui.media

import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState

internal val LocalSoundEnabled = staticCompositionLocalOf { false }
internal val LocalMediaPlayback = staticCompositionLocalOf<EventAudioController?> { null }
private val LocalPlayerFactory = staticCompositionLocalOf<AssetMediaPlayerFactory?> { null }
internal val LocalPlaybackLifecycle = staticCompositionLocalOf<Lifecycle?> { null }

@Composable
internal fun MediaPlaybackHost(soundEnabled: Boolean, factory: AssetMediaPlayerFactory,
    audio: EventAudioController, musicCueKey: String? = null, content: @Composable () -> Unit) {
    val rootLifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycle by rootLifecycle.currentStateAsState()
    SideEffect {
        audio.setSoundEnabled(soundEnabled)
        audio.setForeground(lifecycle.isAtLeast(Lifecycle.State.RESUMED))
        audio.setMusicCue(musicCueKey)
    }
    // The app ViewModel retains positions and deduplication across activity recreation;
    // the host releases native players whenever its window goes away.
    DisposableEffect(audio) { onDispose { audio.setForeground(false) } }
    CompositionLocalProvider(LocalMediaPlayback provides audio, LocalPlayerFactory provides factory,
        LocalSoundEnabled provides soundEnabled, LocalPlaybackLifecycle provides rootLifecycle, content = content)
}

@Composable
internal fun mediaPlayerFactory(): AssetMediaPlayerFactory = checkNotNull(LocalPlayerFactory.current)
