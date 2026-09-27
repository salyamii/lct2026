package ru.nksk.lctapp.core.ui.media

import org.junit.Assert.*
import org.junit.Test

class MediaWorkQueueTest {
    @Test fun visibleClipAndFocusGrantDoNotWaitForOtherFourClipWarmups() {
        val queue = MediaWorkQueue()
        val executed = mutableListOf<String>()
        listOf("port", "butcher", "payment", "appearance").forEach { key ->
            queue.add(Runnable { executed += "prepare:$key" }, speculative = true)
        }
        queue.add(Runnable { executed += "play:port" }, speculative = false)
        queue.take()!!.run()
        queue.add(Runnable { executed += "focus:port" }, speculative = false)
        queue.take()!!.run()
        assertEquals(listOf("play:port", "focus:port"), executed)
        while (!queue.isEmpty) queue.take()!!.run()
        assertEquals(listOf("play:port", "focus:port", "prepare:port", "prepare:butcher",
            "prepare:payment", "prepare:appearance"), executed)
    }

    @Test fun eventRequestedBetweenWarmupsRunsBeforeTheRemainingWarmups() {
        val queue = MediaWorkQueue()
        val executed = mutableListOf<String>()
        queue.add(Runnable {
            executed += "prepare:first"
            queue.add(Runnable { executed += "play:event" }, speculative = false)
        }, speculative = true)
        queue.add(Runnable { executed += "prepare:second" }, speculative = true)
        queue.take()!!.run()
        queue.take()!!.run()
        assertEquals(listOf("prepare:first", "play:event"), executed)
        queue.take()!!.run()
        assertEquals("prepare:second", executed.last())
    }

    @Test fun closeAndReplacementRemainOrderedAheadOfSpeculativePreparation() {
        val queue = MediaWorkQueue()
        val executed = mutableListOf<String>()
        queue.add(Runnable { executed += "prepare:unused" }, speculative = true)
        listOf("close:old", "play:new", "focus:new").forEach { operation ->
            queue.add(Runnable { executed += operation }, speculative = false)
        }
        while (!queue.isEmpty) queue.take()!!.run()
        assertEquals(listOf("close:old", "play:new", "focus:new", "prepare:unused"), executed)
        assertNull(queue.take())
    }
}
