package ru.nksk.lctapp.media

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import ru.nksk.lctapp.core.ui.media.PlaybackLifecycleEffect

class PlaybackLifecycleEffectTest {
    @get:Rule val compose = createComposeRule()

    @Test fun pauseIsDeliveredInsideLifecycleCallbackWithoutWaitingForAnotherFrame() {
        val owner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry(this)
        }
        val foreground = mutableListOf<Boolean>()
        compose.setContent { PlaybackLifecycleEffect(owner.lifecycle) { foreground += it } }
        compose.runOnIdle {
            owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)
            owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            assertEquals(true, foreground.last())
            owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            assertEquals(false, foreground.last())
            owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            assertEquals(false, foreground.last())
            owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)
            owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            assertEquals(true, foreground.last())
        }
    }
}
