package ru.nksk.lctapp.core.ui.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import ru.nksk.lctapp.core.ui.media.LocalMediaPlayback
import ru.nksk.lctapp.core.ui.media.LocalPlaybackLifecycle
import ru.nksk.lctapp.core.ui.media.EventAudioCue
import ru.nksk.lctapp.domain.engine.EventMedia

/** Read-only scene adapter; ordinary recompositions and revisions never replay a cue. */
@Composable
internal fun EventAudioEffect(occurrenceId: String?, media: EventMedia, isCurrentEntry: Boolean) {
    val audio = LocalMediaPlayback.current
    val owner = remember { Any() }
    val rootLifecycle = LocalPlaybackLifecycle.current
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    DisposableEffect(audio, owner, rootLifecycle) {
        onDispose {
            // Activity recreation is not leaving the event: its retained player will resume.
            if (rootLifecycle?.currentState != Lifecycle.State.DESTROYED) audio?.leave(owner)
        }
    }
    SideEffect {
        // The incoming card is already visible during STARTED. Navigation, not RESUMED,
        // identifies it so an outgoing/Back-preview card cannot take over its audio.
        if (occurrenceId != null && canPresentEventAudio(isCurrentEntry, lifecycle))
            audio?.show(owner, occurrenceId, eventAudioCues(media))
        // Route disposal/new scene ends narration. A paused entry may just be app backgrounding.
        else if (isCurrentEntry && occurrenceId == null) audio?.leave(owner)
    }
}

internal fun canPresentEventAudio(isCurrentEntry: Boolean, lifecycle: Lifecycle.State): Boolean =
    isCurrentEntry && lifecycle.isAtLeast(Lifecycle.State.STARTED)

/** Location recordings, speech and appearance sounds each play once. */
internal fun eventAudioCues(media: EventMedia): List<EventAudioCue> = listOfNotNull(
    media.appearanceCueKey?.let { EventAudioCue(it) },
    media.ambientCueKey?.let { EventAudioCue(it, repeatCount = 1) },
    media.narrationCueKey?.let { EventAudioCue(it) },
)
