package ru.nksk.lctapp.core.ui.media

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper

/** Music and speech share focus, so the app's own clips never pause each other. */
internal class PlaybackAudioFocus(context: Context) {
    private val manager = context.getSystemService(AudioManager::class.java)
    private var request: AudioFocusRequest? = null
    private val session = PlaybackFocusSession(::requestFocus, ::abandonFocus)
    private val listener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> session.changed(PlaybackFocusChange.GAIN)
            AudioManager.AUDIOFOCUS_LOSS -> session.changed(PlaybackFocusChange.PERMANENT_LOSS)
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK ->
                session.changed(PlaybackFocusChange.TRANSIENT_LOSS)
        }
    }

    fun acquire(onChanged: () -> Unit): Boolean = session.acquire(onChanged)

    /** A new user action can retry a refused/revoked request, without fighting transient focus. */
    fun retryAfterInteraction() = session.retryAfterInteraction()

    fun release(onChanged: () -> Unit) = session.release(onChanged)

    @Suppress("DEPRECATION")
    private fun requestFocus(): PlaybackFocusResult {
        val result = if (Build.VERSION.SDK_INT >= 26) {
            val focus = request ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setWillPauseWhenDucked(true).setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(listener, Handler(Looper.getMainLooper())).build()
                .also { request = it }
            manager.requestAudioFocus(focus)
        } else manager.requestAudioFocus(listener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        return when (result) {
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> PlaybackFocusResult.GRANTED
            AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> PlaybackFocusResult.DELAYED
            else -> PlaybackFocusResult.FAILED
        }
    }

    @Suppress("DEPRECATION")
    private fun abandonFocus() {
        if (Build.VERSION.SDK_INT >= 26) request?.let(manager::abandonAudioFocusRequest)
        else manager.abandonAudioFocus(listener)
        request = null
    }
}

internal enum class PlaybackFocusResult { GRANTED, DELAYED, FAILED }
internal enum class PlaybackFocusChange { GAIN, TRANSIENT_LOSS, PERMANENT_LOSS }

/** Pure focus policy: listener callbacks never turn a permanent loss into a new request. */
internal class PlaybackFocusSession(
    private val request: () -> PlaybackFocusResult,
    private val abandon: () -> Unit,
) {
    private enum class Phase { IDLE, REQUESTING, GRANTED, WAITING, RETRY_REQUIRED }
    private var phase = Phase.IDLE
    private val clients = linkedSetOf<() -> Unit>()

    fun acquire(onChanged: () -> Unit): Boolean {
        clients += onChanged
        if (phase == Phase.IDLE) requestFocus()
        return phase == Phase.GRANTED
    }

    fun retryAfterInteraction() {
        if (clients.isEmpty() || phase != Phase.RETRY_REQUIRED) return
        requestFocus()
        notifyClients()
    }

    fun changed(change: PlaybackFocusChange) {
        // No callback can grant a request that was released or permanently revoked.
        if (phase == Phase.IDLE || phase == Phase.RETRY_REQUIRED) return
        phase = when (change) {
            PlaybackFocusChange.GAIN -> Phase.GRANTED
            PlaybackFocusChange.TRANSIENT_LOSS -> Phase.WAITING
            PlaybackFocusChange.PERMANENT_LOSS -> Phase.RETRY_REQUIRED
        }
        notifyClients()
    }

    fun release(onChanged: () -> Unit) {
        clients -= onChanged
        if (clients.isNotEmpty() || phase == Phase.IDLE) return
        phase = Phase.IDLE
        abandon()
    }

    private fun requestFocus() {
        phase = Phase.REQUESTING
        val result = try { request() } catch (_: Exception) { PlaybackFocusResult.FAILED }
        if (phase != Phase.REQUESTING) return
        phase = when (result) {
            PlaybackFocusResult.GRANTED -> Phase.GRANTED
            PlaybackFocusResult.DELAYED -> Phase.WAITING
            PlaybackFocusResult.FAILED -> Phase.RETRY_REQUIRED
        }
    }

    private fun notifyClients() { clients.toList().forEach { it() } }
}
