package ru.nksk.lctapp.core.ui.media

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import java.util.ArrayDeque
import javax.inject.Inject
import javax.inject.Singleton

/** One lazy media thread for the application process, holding no Activity or screen reference. */
@Singleton
internal class MediaPlaybackRuntime @Inject constructor() {
    private val main = Handler(Looper.getMainLooper())
    private val lock = Any()
    private val waiting = MediaWorkQueue()
    private var starting = false
    private var drainScheduled = false
    @Volatile private var worker: Handler? = null

    private val drain = object : Runnable {
        override fun run() {
            val task = synchronized(lock) { waiting.take() }
            try { task?.run() } finally {
                synchronized(lock) {
                    drainScheduled = false
                    scheduleLocked()
                }
            }
        }
    }

    fun execute(action: () -> Unit) = enqueue(action, speculative = false)

    /** Speculative asset setup yields to the clip requested by the visible card or user action. */
    fun prepare(action: () -> Unit) = enqueue(action, speculative = true)

    private fun enqueue(action: () -> Unit, speculative: Boolean) {
        val task = Runnable(action)
        synchronized(lock) {
            waiting.add(task, speculative)
            if (worker != null) { scheduleLocked(); return }
            if (starting) return
            starting = true
            // Do not call HandlerThread.getLooper() from UI: thread startup is asynchronous too.
            object : HandlerThread("LctMediaPlayback", Process.THREAD_PRIORITY_AUDIO) {
                override fun onLooperPrepared() {
                    synchronized(lock) {
                        val ready = Handler(checkNotNull(Looper.myLooper()))
                        worker = ready
                        scheduleLocked()
                    }
                }
            }.start()
        }
    }

    private fun scheduleLocked() {
        val ready = worker ?: return
        if (drainScheduled || waiting.isEmpty) return
        drainScheduled = true
        // One task per worker turn also lets native callbacks and focus grants run between warmups.
        ready.post(drain)
    }

    fun onMain(action: () -> Unit) { main.post(action) }

    /** Called only by the serialized media worker. */
    fun later(task: Runnable, delayMs: Long) { checkNotNull(worker).postDelayed(task, delayMs) }
    fun cancel(task: Runnable) { checkNotNull(worker).removeCallbacks(task) }
}

/** Access is serialized by the runtime lock; FIFO order is preserved within each kind of work. */
internal class MediaWorkQueue {
    private val playback = ArrayDeque<Runnable>()
    private val preparation = ArrayDeque<Runnable>()
    val isEmpty get() = playback.isEmpty() && preparation.isEmpty()

    fun add(task: Runnable, speculative: Boolean) {
        if (speculative) preparation.addLast(task) else playback.addLast(task)
    }

    fun take(): Runnable? = playback.pollFirst() ?: preparation.pollFirst()
}
