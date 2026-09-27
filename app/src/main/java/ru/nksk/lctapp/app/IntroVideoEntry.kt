package ru.nksk.lctapp.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import ru.nksk.lctapp.app.navigation.AppStartupState
import ru.nksk.lctapp.app.presentation.MediaPlaybackUiState
import ru.nksk.lctapp.core.ui.components.GameActionButton
import ru.nksk.lctapp.core.ui.components.GameActionStyle
import ru.nksk.lctapp.core.ui.media.IntroVideoPlayer
import ru.nksk.lctapp.core.ui.media.INTRO_VIDEO_ASSET
import ru.nksk.lctapp.feature.onboarding.ui.IntroVideoScreen

/** Startup presentation only: every exit continues to the persisted profile draft. */
@Composable
internal fun IntroVideoEntry(
    state: AppStartupState.IntroVideo,
    media: MediaPlaybackUiState,
    onPositionChanged: (Long) -> Unit,
    onFinished: () -> Unit,
    onPlaybackError: () -> Unit,
    onSoundChanged: (Boolean) -> Unit,
    onSoundRetry: () -> Unit,
) {
    val continueToProfile = dropUnlessResumed { onFinished() }
    BackHandler { continueToProfile() }
    IntroVideoScreen(
        playbackFailed = state.failed,
        video = { modifier ->
            if (media.loaded || media.error != null) IntroVideoPlayer(
                assetPath = INTRO_VIDEO_ASSET,
                positionMs = state.positionMs,
                soundEnabled = media.soundEnabled,
                onPositionChanged = onPositionChanged,
                onCompleted = onFinished,
                onError = onPlaybackError,
                modifier = modifier,
            ) else Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        },
        soundToggle = { modifier ->
            IntroVideoAction(if (media.soundEnabled) "Выключить звук" else "Включить звук",
                dropUnlessResumed { onSoundChanged(!media.soundEnabled) }, modifier,
                enabled = media.loaded, saving = media.saving)
        },
        continueAction = { modifier ->
            IntroVideoAction(if (state.failed) "Продолжить" else "Пропустить", continueToProfile, modifier)
        },
        settingsStatus = {
            media.error?.let { error ->
                Text(error, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                IntroVideoAction("Повторить", dropUnlessResumed { onSoundRetry() }, Modifier.widthIn(max = 220.dp),
                    saving = media.saving)
            }
        },
    )
}

@Composable
private fun IntroVideoAction(text: String, onClick: () -> Unit, modifier: Modifier,
    enabled: Boolean = true, saving: Boolean = false) {
    GameActionButton(text, onClick, modifier, enabled = enabled, interactionBlocked = saving,
        style = GameActionStyle.SECONDARY, minHeight = 48.dp, textStyle = MaterialTheme.typography.labelLarge,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        containerColor = Color.Black.copy(alpha = .75f), contentColor = Color.White,
        borderColor = Color.White.copy(alpha = .65f))
}
