package ru.nksk.lctapp.core.ui.media

import org.junit.Assert.*
import org.junit.Test

class PlaybackFocusSessionTest {
    @Test fun permanentLossPausesEveryClientUntilExplicitInteractionRequestsAgain() {
        var requests = 0
        val session = PlaybackFocusSession(request = { requests++; PlaybackFocusResult.GRANTED }, abandon = {})
        val musicStates = mutableListOf<Boolean>()
        val voiceStates = mutableListOf<Boolean>()
        lateinit var music: () -> Unit
        lateinit var voice: () -> Unit
        music = { musicStates += session.acquire(music) }
        voice = { voiceStates += session.acquire(voice) }
        assertTrue(session.acquire(music))
        assertTrue(session.acquire(voice))
        assertEquals(1, requests)

        session.changed(PlaybackFocusChange.PERMANENT_LOSS)
        assertEquals(listOf(false), musicStates)
        assertEquals(listOf(false), voiceStates)
        assertEquals(1, requests) // Reentrant acquire from each pause callback must not fight the loss.
        assertFalse(session.acquire(music))
        session.changed(PlaybackFocusChange.GAIN) // A stale gain cannot authorize revoked playback.
        assertFalse(session.acquire(voice))
        assertEquals(1, requests)

        session.retryAfterInteraction()
        assertEquals(2, requests)
        assertEquals(listOf(false, true), musicStates)
        assertEquals(listOf(false, true), voiceStates)
    }

    @Test fun failedRequestStaysPausedAndRetryNotifiesBothExistingAndNewClients() {
        var requests = 0
        val session = PlaybackFocusSession(request = {
            requests++
            if (requests == 1) PlaybackFocusResult.FAILED else PlaybackFocusResult.GRANTED
        }, abandon = {})
        val states = mutableListOf<Boolean>()
        lateinit var music: () -> Unit
        lateinit var action: () -> Unit
        music = { states += session.acquire(music) }
        action = { states += session.acquire(action) }
        assertFalse(session.acquire(music))
        assertFalse(session.acquire(action))
        assertFalse(session.acquire(music))
        assertEquals(1, requests)

        session.retryAfterInteraction()
        assertEquals(2, requests)
        assertEquals(listOf(true, true), states)
    }

    @Test fun transientLossWaitsForSystemGainEvenAfterAnotherInteraction() {
        var requests = 0
        val session = PlaybackFocusSession(request = { requests++; PlaybackFocusResult.GRANTED }, abandon = {})
        val states = mutableListOf<Boolean>()
        lateinit var client: () -> Unit
        client = { states += session.acquire(client) }
        assertTrue(session.acquire(client))
        session.changed(PlaybackFocusChange.TRANSIENT_LOSS)
        session.retryAfterInteraction()
        assertEquals(listOf(false), states)
        assertEquals(1, requests)
        session.changed(PlaybackFocusChange.GAIN)
        assertEquals(listOf(false, true), states)
        assertEquals(1, requests)
    }

    @Test fun delayedRequestIsNotRepeatedBeforeSystemGain() {
        var requests = 0
        val session = PlaybackFocusSession(request = { requests++; PlaybackFocusResult.DELAYED }, abandon = {})
        val states = mutableListOf<Boolean>()
        lateinit var client: () -> Unit
        client = { states += session.acquire(client) }
        assertFalse(session.acquire(client))
        session.retryAfterInteraction()
        assertEquals(1, requests)
        assertTrue(states.isEmpty())
        session.changed(PlaybackFocusChange.GAIN)
        assertEquals(listOf(true), states)
    }

    @Test fun releasingLastClientClearsBlockedStateForNextForegroundSession() {
        var requests = 0
        var abandons = 0
        val session = PlaybackFocusSession(request = {
            requests++
            if (requests == 1) PlaybackFocusResult.FAILED else PlaybackFocusResult.GRANTED
        }, abandon = { abandons++ })
        val music: () -> Unit = {}
        val voice: () -> Unit = {}
        assertFalse(session.acquire(music))
        assertFalse(session.acquire(voice))
        session.release(voice)
        assertEquals(0, abandons)
        session.release(music)
        assertEquals(1, abandons)
        session.release(music)
        session.retryAfterInteraction()
        assertEquals(1, requests)
        assertEquals(1, abandons)
        assertTrue(session.acquire(music))
        assertEquals(2, requests)
    }
}
