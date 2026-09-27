package ru.nksk.lctapp.core.ui.media

import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner

internal val LocalSoundEnabled = staticCompositionLocalOf { false }
internal val LocalMediaPlayback = staticCompositionLocalOf<EventAudioController?> { null }
private val LocalPlayerFactory = staticCompositionLocalOf<AssetMediaPlayerFactory?> { null }
internal val LocalPlaybackLifecycle = staticCompositionLocalOf<Lifecycle?> { null }

@Composable
internal fun MediaPlaybackHost(soundEnabled: Boolean, factory: AssetMediaPlayerFactory,
    audio: EventAudioController, musicCueKey: String? = null, content: @Composable () -> Unit) {
    val rootLifecycle = LocalLifecycleOwner.current.lifecycle
    SideEffect {
        audio.setSoundEnabled(soundEnabled)
        audio.setMusicCue(musicCueKey)
    }
    // The app ViewModel retains positions and deduplication across activity recreation;
    // the host releases native players whenever its window goes away.
    PlaybackLifecycleEffect(rootLifecycle, audio::setForeground)
    CompositionLocalProvider(LocalMediaPlayback provides audio, LocalPlayerFactory provides factory,
        LocalSoundEnabled provides soundEnabled, LocalPlaybackLifecycle provides rootLifecycle, content = content)
}

@Composable
internal fun mediaPlayerFactory(): AssetMediaPlayerFactory = checkNotNull(LocalPlayerFactory.current)
