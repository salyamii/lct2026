package ru.nksk.lctapp.core.ui.game

import androidx.lifecycle.Lifecycle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.nksk.lctapp.core.ui.media.EventAudioCue
import ru.nksk.lctapp.domain.engine.EventMedia

class EventAudioCuesTest {
    @Test fun repeatsOnlyTheShortLocationClipBetweenAppearanceAndNarration() {
        val media = EventMedia(musicCueKey = "story.chapter_1", ambientCueKey = "ambient.port",
            appearanceCueKey = "sound.purchase_appears", narrationCueKey = "narration.story.cargo_journal")
        assertEquals(listOf(EventAudioCue("sound.purchase_appears"),
            EventAudioCue("ambient.port", repeatCount = 2), EventAudioCue("narration.story.cargo_journal")),
            eventAudioCues(media))
    }

    @Test fun chapterMusicIsOwnedByTheRootPlayerNotRepeatedAsAnEventCue() {
        assertTrue(eventAudioCues(EventMedia(musicCueKey = "story.chapter_1")).isEmpty())
    }

    @Test fun incomingVisibleCardDoesNotWaitForEndOfNavigationAnimation() {
        assertTrue(canPresentEventAudio(isCurrentEntry = true, Lifecycle.State.STARTED))
        assertTrue(canPresentEventAudio(isCurrentEntry = true, Lifecycle.State.RESUMED))
    }

    @Test fun outgoingOrPredictiveBackPreviewCannotReplaceCurrentCardsAudio() {
        assertFalse(canPresentEventAudio(isCurrentEntry = false, Lifecycle.State.STARTED))
        assertFalse(canPresentEventAudio(isCurrentEntry = false, Lifecycle.State.RESUMED))
        assertTrue(canPresentEventAudio(isCurrentEntry = true, Lifecycle.State.STARTED))
    }

    @Test fun entryNotYetVisibleCannotStartItsScene() {
        assertFalse(canPresentEventAudio(isCurrentEntry = true, Lifecycle.State.CREATED))
        assertFalse(canPresentEventAudio(isCurrentEntry = true, Lifecycle.State.DESTROYED))
    }
}
