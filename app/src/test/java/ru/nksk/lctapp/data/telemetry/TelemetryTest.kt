package ru.nksk.lctapp.data.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Without an initialized agent every span call must stay a harmless passthrough. */
class TelemetryTest {
    @Test
    fun tracedPassesTheResultThroughWhileTheAgentIsDisabled() {
        assertEquals(7, Telemetry.traced("sync.x") { 7 })
        assertEquals("ok", Telemetry.traced("sync.x", "k" to "v") { "ok" })
    }

    @Test
    fun tracedRethrowsTheBodyFailureWhileTheAgentIsDisabled() {
        assertThrows(IllegalStateException::class.java) {
            Telemetry.traced("sync.x") { error("boom") }
        }
    }
}
