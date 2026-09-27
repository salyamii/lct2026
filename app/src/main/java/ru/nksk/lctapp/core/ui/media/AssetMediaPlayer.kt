package ru.nksk.lctapp.core.ui.media

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import android.view.SurfaceHolder
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** All players share one process-owned worker; only application context enters the native layer. */
@Singleton
internal class AssetMediaPlayerFactory @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val runtime: MediaPlaybackRuntime,
) {
    fun audioFocus() = PlaybackAudioFocus(context)
    fun create(assetPath: String, video: Boolean = false, positionMs: Long = 0,
        repeat: Boolean = false, volume: Float = 1f, focus: PlaybackAudioFocus = audioFocus(),
        onCompleted: () -> Unit, onError: () -> Unit): AssetMediaPlayer =
        AssetMediaPlayer(context, runtime, assetPath, video, positionMs, repeat, volume, focus, onCompleted, onError)
}

/** Main-thread commands update intent; every native operation and callback is serialized off the UI. */
internal class AssetMediaPlayer(
    private val context: Context,
    private val runtime: MediaPlaybackRuntime,
    private val assetPath: String,
    private val video: Boolean,
    initialPositionMs: Long,
    private val repeat: Boolean,
    initialVolume: Float,
    private val audioFocus: PlaybackAudioFocus,
    private val onCompleted: () -> Unit,
    private val onError: () -> Unit,
) {
    private data class Intent(
        val foreground: Boolean = false,
        val preload: Boolean = false,
        val soundEnabled: Boolean = true,
        val volume: Float,
        val surface: SurfaceHolder? = null,
        val closed: Boolean = false,
    ) {
        val needsFocus get() = !closed && foreground && soundEnabled
    }

    // Published by main. Close/mute supersedes work queued before the worker sees it.
    @Volatile private var intent = Intent(volume = initialVolume.coerceIn(0f, 1f))
    @Volatile private var position = initialPositionMs.coerceIn(0, Int.MAX_VALUE.toLong())
    @Volatile private var finished = false
    @Volatile private var focusEpoch = 0L

    // Worker-owned native state. UI callers never read native properties.
    private var player: MediaPlayer? = null
    private var nativeSurface: SurfaceHolder? = null
    private var prepared = false
    private var seeking = false
    private var playing = false
    private var appliedVolume: Float? = null
    private var seekFloor: Long? = null
    private var focusRequested = false
    private var focusGranted = false
    private var terminalPosted = false

    // Main-owned focus registration and terminal callback delivery.
    private var registeredFocusEpoch: Long? = null
    private var terminalDelivered = false
    private val focusChanged: () -> Unit = { refreshFocusOnMain() }
    private val positionPoll = object : Runnable {
        override fun run() {
            if (intent.closed || finished || !playing || seeking) return
            // After playback has advanced, a loop may legitimately report its beginning.
            // Keep the floor only through seek/start, not across the next music loop.
            if (repeat) seekFloor = null
            rememberPosition()
            runtime.later(this, PositionPollMs)
        }
    }

    /** Never performs a binder/native query on the UI thread. */
    fun positionMs(): Long = position

    /** Warm a clip without making it foreground, requesting focus, or starting playback. */
    fun prepare() {
        val current = intent
        if (current.closed || finished || current.preload) return
        intent = current.copy(preload = true)
        runtime.prepare(::reconcile)
    }

    fun setSoundEnabled(value: Boolean) {
        val current = intent
        if (current.closed || finished || current.soundEnabled == value) return
        intent = current.copy(soundEnabled = value)
        runtime.execute(::reconcile)
    }

    fun setVolume(value: Float) {
        val current = intent
        val clamped = value.coerceIn(0f, 1f)
        if (current.closed || finished || current.volume == clamped) return
        intent = current.copy(volume = clamped)
        runtime.execute(::reconcile)
    }

    fun setForeground(value: Boolean) {
        val current = intent
        if (current.closed || finished || (current.foreground == value && (value || !current.preload))) return
        intent = current.copy(foreground = value, preload = current.preload && value)
        runtime.execute(::reconcile)
    }

    fun attachSurface(value: SurfaceHolder?) {
        val current = intent
        if (current.closed || finished || current.surface === value) return
        intent = current.copy(surface = value)
        runtime.execute(::reconcile)
    }

    fun close() {
        val current = intent
        if (current.closed) return
        intent = current.copy(closed = true, foreground = false, preload = false, surface = null)
        runtime.execute(::reconcile)
    }

    private fun shouldKeepPlayer(): Boolean = intent.let {
        !it.closed && !finished && (it.foreground || it.preload) && (!video || it.surface != null)
    }

    private fun reconcile() {
        if (!shouldKeepPlayer()) { releasePlayer(); return }
        if (player != null && video && nativeSurface !== intent.surface) releasePlayer()
        if (player == null) ensurePlayer() else updatePlayback()
    }

    private fun ensurePlayer() {
        if (!shouldKeepPlayer() || player != null) return
        try {
            val created = MediaPlayer()
            player = created
            if (!shouldKeepPlayer()) { releasePlayer(); return }
            created.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            nativeSurface = intent.surface
            if (video) created.setDisplay(nativeSurface)
            created.setOnPreparedListener { ready -> runtime.execute { nativePrepared(ready) } }
            created.setOnSeekCompleteListener { ready -> runtime.execute {
                if (player === ready && !intent.closed && !finished) {
                    seeking = false
                    rememberPosition()
                    updatePlayback()
                }
            } }
            created.setOnCompletionListener { completed -> runtime.execute {
                if (player === completed && !intent.closed && !finished) finish(error = false)
            } }
            created.setOnErrorListener { failed, what, extra ->
                runtime.execute {
                    if (player === failed && !intent.closed && !finished) fail("Decoder error $what/$extra")
                }
                true
            }
            context.assets.openFd(assetPath).use { source ->
                created.setDataSource(source.fileDescriptor, source.startOffset, source.length)
            }
            if (!shouldKeepPlayer()) { releasePlayer(); return }
            created.prepareAsync()
        } catch (error: Exception) { fail("Could not prepare bundled media", error) }
    }

    private fun nativePrepared(current: MediaPlayer) {
        if (player !== current || intent.closed || finished) return
        if (!shouldKeepPlayer()) { releasePlayer(); return }
        try {
            prepared = true
            // Fill the intro surface on tall phones without stretching the original portrait frame.
            if (video) current.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
            current.isLooping = repeat
            if (position > 0) {
                val target = position.coerceAtMost((current.duration - 1).coerceAtLeast(0).toLong())
                position = target
                seekFloor = target
                seeking = true
                @Suppress("DEPRECATION")
                current.seekTo(target.toInt())
            } else updatePlayback()
        } catch (error: Exception) { fail("Could not prepare playback position", error) }
    }

    private fun updatePlayback() {
        if (!shouldKeepPlayer()) { releasePlayer(); return }
        val current = player ?: return
        if (!prepared || seeking) return
        try {
            if (intent.needsFocus) requestFocus() else releaseFocus()
            val level = if (intent.soundEnabled) intent.volume else 0f
            if (appliedVolume != level) {
                current.setVolume(level, level)
                appliedVolume = level
            }
            val shouldPlay = intent.foreground && (!intent.soundEnabled || focusGranted)
            if (shouldPlay && !playing) {
                current.start()
                playing = true
                runtime.cancel(positionPoll)
                runtime.later(positionPoll, PositionPollMs)
            } else if (!shouldPlay && playing) {
                rememberPosition()
                current.pause()
                playing = false
                runtime.cancel(positionPoll)
            }
        } catch (error: Exception) { fail("Could not update playback", error) }
    }

    private fun requestFocus() {
        if (focusRequested) return
        focusRequested = true
        val epoch = ++focusEpoch
        runtime.onMain {
            if (epoch != focusEpoch) return@onMain
            if (!intent.needsFocus || finished) {
                cancelFocusRequest(epoch)
                return@onMain
            }
            registeredFocusEpoch = epoch
            publishFocusResult(epoch, audioFocus.acquire(focusChanged))
        }
    }

    private fun refreshFocusOnMain() {
        val epoch = registeredFocusEpoch ?: return
        if (epoch != focusEpoch || !intent.needsFocus || finished) return
        // acquire reads the shared focus state; its owner controls any new request.
        publishFocusResult(epoch, audioFocus.acquire(focusChanged))
    }

    private fun publishFocusResult(epoch: Long, granted: Boolean) {
        runtime.execute {
            if (epoch != focusEpoch || !focusRequested || finished) return@execute
            if (!intent.needsFocus) { releaseFocus(); return@execute }
            focusGranted = granted
            updatePlayback()
        }
    }

    private fun cancelFocusRequest(epoch: Long) {
        runtime.execute {
            if (epoch != focusEpoch || !focusRequested) return@execute
            // Main may observe a short pause that worker reconciliations coalesced away.
            // A skipped request is not a pending platform lease; acknowledge it before resuming.
            releaseFocus()
            updatePlayback()
        }
    }

    private fun releaseFocus() {
        if (!focusRequested && !focusGranted) return
        focusRequested = false
        focusGranted = false
        val epoch = ++focusEpoch
        runtime.onMain {
            if (registeredFocusEpoch?.let { it < epoch } == true) {
                registeredFocusEpoch = null
                audioFocus.release(focusChanged)
            }
        }
    }

    private fun rememberPosition() {
        val current = player ?: return
        if (!prepared || seeking) return
        val actual = runCatching { current.currentPosition.toLong().coerceAtLeast(0) }.getOrNull() ?: return
        val floor = seekFloor
        if (floor != null && actual < floor) position = floor else {
            seekFloor = null
            position = actual
        }
    }

    private fun releasePlayer() {
        runtime.cancel(positionPoll)
        rememberPosition()
        val old = player
        player = null
        nativeSurface = null
        prepared = false
        seeking = false
        playing = false
        appliedVolume = null
        seekFloor = null
        try { old?.release() } catch (error: Exception) {
            Log.w("BundledMedia", "Could not release bundled media: $assetPath", error)
        }
        releaseFocus()
    }

    private fun fail(reason: String, error: Throwable? = null) {
        if (finished) return
        if (!shouldKeepPlayer()) { releasePlayer(); return }
        Log.w("BundledMedia", "$reason: $assetPath", error)
        finish(error = true)
    }

    private fun finish(error: Boolean) {
        if (finished || intent.closed) return
        finished = true
        releasePlayer()
        if (terminalPosted) return
        terminalPosted = true
        runtime.onMain {
            if (!intent.closed && !terminalDelivered) {
                terminalDelivered = true
                if (error) onError() else onCompleted()
            }
        }
    }

    private companion object { const val PositionPollMs = 500L }
}
