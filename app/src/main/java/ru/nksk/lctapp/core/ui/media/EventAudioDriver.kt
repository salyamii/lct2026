package ru.nksk.lctapp.core.ui.media

import android.os.Handler
import android.os.Looper

/** Creation is inert; playback starts only after setForeground(true). */
internal interface EventAudioPlayer {
    fun setForeground(value: Boolean)
    fun setVolume(value: Float)
    fun close()
}

/** Small native boundary so cue delivery can be checked independently of Android playback. */
internal interface EventAudioDriver {
    fun create(cueKey: String, repeat: Boolean, volume: Float,
        onCompleted: () -> Unit, onError: () -> Unit): EventAudioPlayer?
    fun retryFocusAfterInteraction()
    fun setActive(value: Boolean) = Unit
}

internal class AssetEventAudioDriver(private val factory: AssetMediaPlayerFactory) : EventAudioDriver {
    private val focus = factory.audioFocus()
    private val handler = Handler(Looper.getMainLooper())
    private var active = false
    private var refillScheduled = false
    private val warmed = linkedMapOf<String, Slot>()
    private val failedWarmKeys = mutableSetOf<String>()
    private val refill = Runnable {
        refillScheduled = false
        if (active) warmMissing()
    }

    private class Slot {
        lateinit var player: AssetMediaPlayer
        var onCompleted: (() -> Unit)? = null
        var onError: (() -> Unit)? = null
        fun close() {
            onCompleted = null
            onError = null
            player.close()
        }
    }

    override fun setActive(value: Boolean) {
        if (active == value) return
        active = value
        if (value) warmMissing() else {
            handler.removeCallbacks(refill)
            refillScheduled = false
            warmed.values.forEach(Slot::close)
            warmed.clear()
            failedWarmKeys.clear()
        }
    }

    override fun create(cueKey: String, repeat: Boolean, volume: Float,
        onCompleted: () -> Unit, onError: () -> Unit): EventAudioPlayer? {
        val asset = BundledMediaCatalog.assetPath(cueKey) ?: return null
        val cached = if (active && !repeat) warmed.remove(cueKey) else null
        val slot = cached ?: createSlot(cueKey, asset, repeat, volume)
        slot.onCompleted = onCompleted
        slot.onError = onError
        slot.player.setVolume(volume)
        if (active && !repeat && cueKey in ShortCueKeys && !refillScheduled) {
            // The prepared first clip can start immediately; prepare its replacement on the next turn.
            refillScheduled = true
            handler.post(refill)
        }
        return object : EventAudioPlayer {
            override fun setForeground(value: Boolean) = slot.player.setForeground(value)
            override fun setVolume(value: Float) = slot.player.setVolume(value)
            override fun close() = slot.close()
        }
    }

    override fun retryFocusAfterInteraction() = focus.retryAfterInteraction()

    private fun warmMissing() {
        ShortCueKeys.forEach { key ->
            if (key in warmed || key in failedWarmKeys) return@forEach
            val asset = BundledMediaCatalog.assetPath(key) ?: return@forEach
            val slot = createSlot(key, asset, repeat = false, volume = 1f)
            warmed[key] = slot
            // This never makes a clip foreground or acquires audio focus.
            slot.player.prepare()
        }
    }

    private fun createSlot(key: String, asset: String, repeat: Boolean, volume: Float): Slot {
        val slot = Slot()
        slot.player = factory.create(asset, repeat = repeat, volume = volume, focus = focus,
            onCompleted = { slot.onCompleted?.invoke() },
            onError = {
                if (warmed[key] === slot) {
                    warmed.remove(key)
                    failedWarmKeys += key // Do not keep retrying a broken/unsupported asset in the background.
                    slot.close()
                } else slot.onError?.invoke()
            })
        return slot
    }

    private companion object {
        val ShortCueKeys = setOf("ambient.port", "ambient.butcher_shop", "sound.payment", "sound.purchase_appears",
            "sound.telescope_adjustment")
    }
}
