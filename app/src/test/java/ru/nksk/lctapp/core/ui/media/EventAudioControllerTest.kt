package ru.nksk.lctapp.core.ui.media

import org.junit.Assert.*
import org.junit.Test

class EventAudioControllerTest {
    @Test fun firstEventPlaysOverMusicAndOnlyAmbientRepeatsTwice() {
        val driver = FakeDriver()
        val controller = activeController(driver)
        controller.setMusicCue("chapter")
        val music = driver.last("chapter")
        val owner = Any()
        val cues = listOf(EventAudioCue("appearance"), EventAudioCue("port", 2), EventAudioCue("narration"))

        controller.show(owner, "event-1", cues)
        assertTrue(music.foregroundState)
        assertTrue(music.repeat)
        assertEquals(.10f, music.level, 0f)
        assertEquals(listOf("chapter", "appearance"), driver.keys)
        repeat(3) { controller.show(owner, "event-1", cues); controller.setForeground(true) }
        assertEquals(1, driver.count("appearance"))

        driver.last("appearance").complete()
        driver.last("port").complete()
        assertEquals(2, driver.count("port"))
        driver.last("port").complete()
        assertEquals(1, driver.count("narration"))
        driver.last("narration").complete()
        assertEquals(.35f, music.level, 0f)
        assertFalse(music.closed)
        controller.leave(owner)
        controller.show(Any(), "event-1", cues)
        assertEquals(listOf("chapter", "appearance", "port", "port", "narration"), driver.keys)
    }

    @Test fun disposalBeforePreparationDoesNotConsumeCueAndStaleCallbacksCannotFinishReplacement() {
        val driver = FakeDriver()
        val controller = activeController(driver)
        val firstOwner = Any()
        val cues = listOf(EventAudioCue("narration"))
        controller.show(firstOwner, "event-1", cues)
        val preparing = driver.last("narration")
        controller.leave(firstOwner)
        assertTrue(preparing.closed)

        val newOwner = Any()
        controller.show(newOwner, "event-1", cues)
        val replacement = driver.last("narration")
        assertNotSame(preparing, replacement)
        preparing.complete()
        preparing.fail()
        assertFalse(replacement.closed)
        controller.leave(newOwner)
        controller.show(Any(), "event-1", cues)
        assertEquals(3, driver.count("narration"))
    }

    @Test fun muteRetriesOnlyUnfinishedRepetitionThenContinuesToNarration() {
        val driver = FakeDriver()
        val controller = activeController(driver)
        val owner = Any()
        val cues = listOf(EventAudioCue("appearance"), EventAudioCue("port", 2), EventAudioCue("narration"))
        controller.show(owner, "event-1", cues)
        driver.last("appearance").complete()
        driver.last("port").complete() // First repetition was heard; second is still preparing.
        val interrupted = driver.last("port")
        controller.setSoundEnabled(false)
        assertTrue(interrupted.closed)
        controller.setSoundEnabled(true)
        assertEquals(1, driver.count("appearance"))
        assertEquals(3, driver.count("port"))
        driver.last("port").complete()
        assertEquals(1, driver.count("narration"))
        driver.last("narration").complete()
        controller.setSoundEnabled(false)
        controller.setSoundEnabled(true)
        assertEquals(3, driver.count("port"))
        assertEquals(1, driver.count("narration"))
    }

    @Test fun backgroundPausesTheSamePlayerAndKeepsRemainingQueue() {
        val driver = FakeDriver()
        val controller = activeController(driver)
        val owner = Any()
        val cues = listOf(EventAudioCue("port", 2), EventAudioCue("narration"))
        controller.show(owner, "event-1", cues)
        val first = driver.last("port")
        controller.setForeground(false)
        assertFalse(first.foregroundState)
        assertFalse(first.closed)
        controller.show(owner, "event-1", cues)
        controller.setForeground(true)
        assertSame(first, driver.last("port"))
        assertTrue(first.foregroundState)
        first.complete()
        driver.last("port").complete()
        assertEquals(listOf("port", "port", "narration"), driver.keys)
    }

    @Test fun changingEventClosesOldCueAndDoesNotLetItsLateCompletionConsumeTheNewOne() {
        val driver = FakeDriver()
        val controller = activeController(driver)
        val owner = Any()
        val cues = listOf(EventAudioCue("port", 2))
        controller.show(owner, "event-1", cues)
        val previous = driver.last("port")
        controller.show(owner, "event-2", cues)
        val current = driver.last("port")
        assertNotSame(previous, current)
        assertTrue(previous.closed)
        previous.complete()
        assertFalse(current.closed)
        controller.show(owner, "event-1", cues)
        assertEquals(3, driver.count("port"))
    }

    @Test fun newSilentDeedStopsPreviousNarrationButKeepsChapterMusic() {
        val driver = FakeDriver()
        val controller = activeController(driver)
        controller.setMusicCue("chapter")
        val music = driver.last("chapter")
        controller.show(Any(), "cargo-journal", listOf(EventAudioCue("cargo-narration")))
        val previousNarration = driver.last("cargo-narration")

        controller.show(Any(), "telescope-deed", emptyList())
        assertTrue(previousNarration.closed)
        assertFalse(music.closed)
        assertEquals(.35f, music.level, 0f)
        previousNarration.complete()
        assertEquals(listOf("chapter", "cargo-narration"), driver.keys)
    }

    @Test fun ownershipTransfersToMiniGameWithoutRestartOrOldOwnerDisposalStoppingIt() {
        val driver = FakeDriver()
        val controller = activeController(driver)
        val proposal = Any()
        val miniGame = Any()
        val cues = listOf(EventAudioCue("port", 2))
        controller.show(proposal, "deed-1", cues)
        val playing = driver.last("port")
        controller.show(miniGame, "deed-1", cues)
        controller.leave(proposal)
        assertSame(playing, driver.last("port"))
        assertFalse(playing.closed)
        assertEquals(1, driver.count("port"))
        playing.complete()
        driver.last("port").complete()
        controller.show(miniGame, "deed-1", cues)
        assertEquals(2, driver.count("port"))
    }

    @Test fun playbackErrorDoesNotMarkCompletedOrAutoRetryAndNextVisitRetriesOnlyFailedCue() {
        val driver = FakeDriver()
        val controller = activeController(driver)
        val owner = Any()
        val cues = listOf(EventAudioCue("appearance"), EventAudioCue("narration"))
        controller.show(owner, "event-1", cues)
        driver.last("appearance").fail()
        assertEquals(listOf("appearance", "narration"), driver.keys)
        driver.last("narration").complete()
        repeat(3) { controller.show(owner, "event-1", cues) }
        assertEquals(1, driver.count("appearance"))
        controller.leave(owner)
        controller.show(Any(), "event-1", cues)
        assertEquals(2, driver.count("appearance"))
        driver.last("appearance").complete()
        assertEquals(1, driver.count("narration"))
    }

    @Test fun initiallyMutedSceneStartsWhenSoundIsEnabled() {
        val driver = FakeDriver()
        val controller = EventAudioController(driver)
        controller.setForeground(true)
        controller.show(Any(), "event-1", listOf(EventAudioCue("narration")))
        assertTrue(driver.keys.isEmpty())
        controller.setSoundEnabled(true)
        assertEquals(listOf("narration"), driver.keys)
    }

    @Test fun actionsPlayOncePerRequestAndMutedOrBackgroundRequestsNeverCatchUp() {
        val driver = FakeDriver()
        val controller = activeController(driver)
        controller.playAction("paid", listOf("payment"))
        controller.playAction("paid", listOf("payment"))
        driver.last("payment").complete()
        controller.playAction("paid", listOf("payment"))
        assertEquals(1, driver.count("payment"))

        controller.setForeground(false)
        controller.playAction("background", listOf("payment"))
        controller.setForeground(true)
        controller.playAction("background", listOf("payment"))
        controller.setSoundEnabled(false)
        controller.playAction("muted", listOf("payment"))
        controller.setSoundEnabled(true)
        controller.playAction("muted", listOf("payment"))
        assertEquals(1, driver.count("payment"))

        controller.playAction("failed", listOf("payment"))
        driver.last("payment").fail()
        controller.playAction("failed", listOf("payment"))
        assertEquals(2, driver.count("payment"))
    }

    @Test fun sceneDisposalDoesNotCancelConfirmedPaymentQueue() {
        val driver = FakeDriver()
        val controller = activeController(driver)
        val owner = Any()
        controller.show(owner, "event-1", listOf(EventAudioCue("narration")))
        val narration = driver.last("narration")
        controller.playAction("paid", listOf("payment", "thanks"))
        assertTrue(narration.closed)
        val payment = driver.last("payment")
        controller.leave(owner)
        assertFalse(payment.closed)
        payment.complete()
        assertEquals(listOf("narration", "payment", "thanks"), driver.keys)
    }

    @Test fun newCardDoesNotWaitForPaymentOrThanksFromPreviousCard() {
        val driver = FakeDriver()
        val controller = activeController(driver)
        val previousOwner = Any()
        controller.show(previousOwner, "event-1", listOf(EventAudioCue("narration")))
        controller.playAction("paid", listOf("payment", "thanks"))
        val payment = driver.last("payment")
        controller.leave(previousOwner)
        assertFalse(payment.closed) // Leaving the card alone still lets its payment finish.

        controller.show(Any(), "event-2", listOf(EventAudioCue("port", 2)))
        val port = driver.last("port")
        assertTrue(payment.closed)
        assertTrue(port.foregroundState)
        assertEquals(0, driver.count("thanks"))
        payment.complete()
        assertFalse(port.closed)
        assertEquals(listOf("narration", "payment", "port"), driver.keys)
    }

    @Test fun driverIsActiveOnlyWhileSoundEnabledAndWindowForeground() {
        val driver = FakeDriver()
        val controller = EventAudioController(driver)
        controller.setForeground(true)
        assertTrue(driver.activations.isEmpty())
        controller.setSoundEnabled(true)
        controller.setForeground(true)
        controller.setSoundEnabled(true)
        assertEquals(listOf(true), driver.activations)
        controller.setSoundEnabled(false)
        controller.setSoundEnabled(true)
        controller.setForeground(false)
        controller.setForeground(false)
        controller.setForeground(true)
        controller.close()
        assertEquals(listOf(true, false, true, false, true, false), driver.activations)
    }

    @Test fun failedMusicWaitsForNewSceneAndDoesNotRetryOnOrdinaryRecomposition() {
        val driver = FakeDriver()
        val controller = activeController(driver)
        val owner = Any()
        val cues = listOf(EventAudioCue("port"))
        controller.setMusicCue("chapter")
        controller.show(owner, "event-1", cues)
        val failed = driver.last("chapter")
        failed.fail()
        assertTrue(failed.closed)
        repeat(3) {
            controller.setMusicCue("chapter")
            controller.setForeground(true)
            controller.setSoundEnabled(true)
            controller.show(owner, "event-1", cues)
        }
        assertEquals(1, driver.count("chapter"))

        controller.show(owner, "event-2", cues)
        val recovered = driver.last("chapter")
        assertEquals(2, driver.count("chapter"))
        assertTrue(recovered.foregroundState)
        assertEquals(.10f, recovered.level, 0f)
    }

    @Test fun staleMusicCallbacksCannotCloseDifferentChapterOrReplacementOfSameChapter() {
        val driver = FakeDriver()
        val controller = activeController(driver)
        controller.setMusicCue("chapter-1")
        val old = driver.last("chapter-1")
        controller.setMusicCue("chapter-2")
        val secondChapter = driver.last("chapter-2")
        old.fail()
        old.complete()
        assertFalse(secondChapter.closed)
        controller.setMusicCue("chapter-1")
        val replacement = driver.last("chapter-1")
        old.fail()
        secondChapter.complete()
        assertFalse(replacement.closed)
        assertEquals(2, driver.count("chapter-1"))
        assertEquals(1, driver.count("chapter-2"))
    }

    @Test fun musicErrorInBackgroundStaysSilentUntilRealForegroundTransition() {
        val driver = FakeDriver()
        val controller = activeController(driver)
        controller.setMusicCue("chapter")
        val failed = driver.last("chapter")
        controller.setForeground(false)
        failed.fail()
        controller.setMusicCue("chapter")
        controller.show(Any(), "event-1", listOf(EventAudioCue("port")))
        controller.setForeground(false)
        assertEquals(1, driver.count("chapter"))
        assertEquals(0, driver.count("port"))
        controller.setForeground(true)
        assertEquals(2, driver.count("chapter"))
        assertTrue(driver.last("chapter").foregroundState)
        assertEquals(1, driver.count("port"))
        controller.setForeground(true)
        assertEquals(2, driver.count("chapter"))
    }

    @Test fun unexpectedlyCompletedMusicCanRecoverOnActionAndFailedMusicOnUnmute() {
        val driver = FakeDriver()
        val controller = activeController(driver)
        controller.setMusicCue("chapter")
        driver.last("chapter").complete() // A loop should not complete, but a native stop must be recoverable.
        assertEquals(1, driver.count("chapter"))
        controller.playAction("paid", listOf("payment"))
        assertEquals(2, driver.count("chapter"))
        driver.last("chapter").fail()
        controller.setSoundEnabled(false)
        assertEquals(2, driver.count("chapter"))
        controller.setSoundEnabled(true)
        assertEquals(3, driver.count("chapter"))
        assertTrue(driver.last("chapter").foregroundState)
    }

    private fun activeController(driver: FakeDriver) = EventAudioController(driver).apply {
        setSoundEnabled(true)
        setForeground(true)
    }

    private class FakeDriver : EventAudioDriver {
        val players = mutableListOf<FakePlayer>()
        val activations = mutableListOf<Boolean>()
        val keys get() = players.map { it.key }
        fun count(key: String) = players.count { it.key == key }
        fun last(key: String) = players.last { it.key == key }

        override fun create(cueKey: String, repeat: Boolean, volume: Float,
            onCompleted: () -> Unit, onError: () -> Unit): EventAudioPlayer =
            FakePlayer(cueKey, repeat, volume, onCompleted, onError).also(players::add)

        override fun retryFocusAfterInteraction() = Unit
        override fun setActive(value: Boolean) { activations += value }
    }

    private class FakePlayer(val key: String, val repeat: Boolean, initialVolume: Float,
        private val onCompleted: () -> Unit, private val onError: () -> Unit) : EventAudioPlayer {
        var foregroundState = false
            private set
        var level = initialVolume
            private set
        var closed = false
            private set
        override fun setForeground(value: Boolean) { foregroundState = value }
        override fun setVolume(value: Float) { level = value }
        override fun close() { closed = true }
        // Keep callbacks callable after close to model queued native callbacks explicitly.
        fun complete() = onCompleted()
        fun fail() = onError()
    }
}
