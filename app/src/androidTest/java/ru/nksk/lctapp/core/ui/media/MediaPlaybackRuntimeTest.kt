package ru.nksk.lctapp.core.ui.media

import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Device-only threading checks. These do not require launching an Activity or playing a clip. */
@RunWith(AndroidJUnit4::class)
class MediaPlaybackRuntimeTest {
    @Test fun workerSerializesCommandsAwayFromMainAndPostsCallbacksBackToMain() {
        val runtime = MediaPlaybackRuntime()
        val order = CopyOnWriteArrayList<Int>()
        val threads = CopyOnWriteArrayList<Thread>()
        val callbackIsMain = AtomicBoolean(false)
        val complete = CountDownLatch(1)
        repeat(50) { index ->
            runtime.execute {
                order += index
                threads += Thread.currentThread()
                if (index == 49) runtime.onMain {
                    callbackIsMain.set(Looper.myLooper() === Looper.getMainLooper())
                    complete.countDown()
                }
            }
        }
        assertTrue("Media worker did not finish", complete.await(10, TimeUnit.SECONDS))
        assertEquals((0 until 50).toList(), order.toList())
        assertEquals(1, threads.toSet().size)
        assertNotSame(Looper.getMainLooper().thread, threads.first())
        assertTrue(callbackIsMain.get())
    }

    @Test fun asynchronousPrepareFailureIsDeliveredOnceOnMain() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val runtime = MediaPlaybackRuntime()
        val complete = CountDownLatch(1)
        val failures = AtomicInteger()
        val completions = AtomicInteger()
        val callbackIsMain = AtomicBoolean(false)
        val clip = AtomicReference<AssetMediaPlayer>()
        instrumentation.runOnMainSync {
            clip.set(AssetMediaPlayerFactory(instrumentation.targetContext.applicationContext, runtime).create(
                "media/missing-test-asset.mp3", onCompleted = { completions.incrementAndGet() }, onError = {
                    callbackIsMain.set(Looper.myLooper() === Looper.getMainLooper())
                    failures.incrementAndGet()
                    complete.countDown()
                }))
            clip.get().prepare()
        }
        assertTrue("Prepare error was not delivered", complete.await(10, TimeUnit.SECONDS))
        instrumentation.runOnMainSync { clip.get().close() }
        assertTrue(callbackIsMain.get())
        assertEquals(1, failures.get())
        assertEquals(0, completions.get())
    }

    @Test fun closingWhileWarmupIsQueuedSuppressesItsLateTerminalCallback() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val runtime = MediaPlaybackRuntime()
        val errors = AtomicInteger()
        val completions = AtomicInteger()
        val drained = CountDownLatch(1)
        instrumentation.runOnMainSync {
            val clip = AssetMediaPlayerFactory(instrumentation.targetContext.applicationContext, runtime).create(
                "media/missing-test-asset.mp3", positionMs = 12_345,
                onCompleted = { completions.incrementAndGet() }, onError = { errors.incrementAndGet() })
            clip.prepare()
            clip.close()
            // Reading while warmup/release is queued never asks the native player for its position.
            assertEquals(12_345L, clip.positionMs())
            runtime.execute { runtime.onMain { drained.countDown() } }
        }
        assertTrue("Media work did not drain", drained.await(10, TimeUnit.SECONDS))
        assertEquals(0, errors.get())
        assertEquals(0, completions.get())
    }
}
