package ru.nksk.lctapp

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import coil.ImageLoader
import coil.decode.DataSource
import coil.intercept.Interceptor
import coil.request.SuccessResult
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import ru.nksk.lctapp.core.ui.components.GameArtworkLoad
import ru.nksk.lctapp.core.ui.components.rememberGameArtworkLoad

/** Counts actual Coil requests, independently of bitmap caches or device decoding speed. */
class GameArtworkResizeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun shrinkingThroughIntermediateSizesKeepsOneRequestAndTheSameReadyPainter() {
        val requests = AtomicInteger()
        val latest = AtomicReference<GameArtworkLoad>()
        val loader = ImageLoader.Builder(InstrumentationRegistry.getInstrumentation().targetContext)
            .components {
                add(Interceptor { chain ->
                    requests.incrementAndGet()
                    SuccessResult(ColorDrawable(Color.RED), chain.request, DataSource.DISK)
                })
            }.build()
        var side by mutableStateOf(240.dp)
        try {
            compose.setContent {
                val load = rememberGameArtworkLoad(R.drawable.menu_ryzhik, DpSize(side, side),
                    participateInScene = false, imageLoader = loader)
                SideEffect { latest.set(load) }
                load.painter?.let { Image(it, null, Modifier.size(side)) }
            }
            compose.waitUntil { latest.get()?.painter != null }
            val initial = latest.get().painter
            for (size in listOf(230, 210, 180, 140, 100)) {
                compose.runOnIdle { side = size.dp }
                compose.waitForIdle()
                assertSame(initial, latest.get().painter)
                assertTrue(latest.get().settled)
                assertEquals(1, requests.get())
            }
        } finally {
            loader.shutdown()
        }
    }

    @Test fun aLargerDecodeKeepsTheOldFrameVisibleUntilTheUpgradeCompletes() {
        val requests = AtomicInteger()
        val latest = AtomicReference<GameArtworkLoad>()
        val upgrade = CompletableDeferred<Unit>()
        val loader = ImageLoader.Builder(InstrumentationRegistry.getInstrumentation().targetContext)
            .components {
                add(Interceptor { chain ->
                    val number = requests.incrementAndGet()
                    if (number > 1) upgrade.await()
                    SuccessResult(ColorDrawable(if (number == 1) Color.RED else Color.BLUE),
                        chain.request, DataSource.DISK)
                })
            }.build()
        var side by mutableStateOf(64.dp)
        try {
            compose.setContent {
                val load = rememberGameArtworkLoad(R.drawable.menu_ryzhik, DpSize(side, side),
                    participateInScene = false, imageLoader = loader)
                SideEffect { latest.set(load) }
                load.painter?.let { Image(it, null, Modifier.size(side)) }
            }
            compose.waitUntil { latest.get()?.painter != null }
            val initial = latest.get().painter
            compose.runOnIdle { side = 160.dp }
            compose.waitUntil { requests.get() == 2 }
            compose.waitForIdle()
            assertSame(initial, latest.get().painter)
            assertTrue(latest.get().settled)
            upgrade.complete(Unit)
            compose.waitUntil { latest.get().painter != null && latest.get().painter !== initial }
            assertNotSame(initial, latest.get().painter)
            assertEquals(2, requests.get())
        } finally {
            upgrade.cancel()
            loader.shutdown()
        }
    }
}
