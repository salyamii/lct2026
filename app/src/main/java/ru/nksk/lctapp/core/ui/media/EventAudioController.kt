package ru.nksk.lctapp.core.ui.media

internal data class EventAudioCue(val key: String, val repeatCount: Int = 1) {
    init { require(repeatCount > 0) }
}

/** One sequential cue lane beside music. Completion belongs to each actually heard scene cue. */
internal class EventAudioController(private val driver: EventAudioDriver) {
    constructor(factory: AssetMediaPlayerFactory) : this(AssetEventAudioDriver(factory))

    private data class CueIdentity(val key: String, val repeatIndex: Int)
    private data class Cue(val sceneId: String?, val identity: CueIdentity)
    private var enabled = false
    private var foreground = false
    private var driverActive = false
    private var owner: Any? = null
    private var sceneId: String? = null
    private var sceneCues: List<EventAudioCue> = emptyList()
    private val completedScenes = linkedMapOf<String, MutableSet<CueIdentity>>()
    private val seenActions = linkedSetOf<String>()
    private val queue = ArrayDeque<Cue>()
    private var current: Cue? = null
    private var playbackToken: Any? = null
    private var player: EventAudioPlayer? = null
    private var musicKey: String? = null
    private var music: EventAudioPlayer? = null
    private var musicToken: Any? = null

    fun setSoundEnabled(value: Boolean) {
        if (enabled == value) return
        enabled = value
        updateDriverActivity()
        if (!value) { music?.setForeground(false); clearPlayback() } else activateScene()
        music?.setForeground(value && foreground)
        if (value) ensureMusic()
    }

    fun setForeground(value: Boolean) {
        val enteredForeground = value && !foreground
        foreground = value
        updateDriverActivity()
        player?.setForeground(value)
        music?.setForeground(value && enabled)
        if (enteredForeground) ensureMusic()
        if (value) startNext()
    }

    fun setMusicCue(key: String?) {
        if (key == musicKey) return
        stopMusic()
        musicKey = key
        ensureMusic()
    }

    fun show(owner: Any, id: String, cues: List<EventAudioCue>) {
        if (sceneId == id && sceneCues == cues) { this.owner = owner; return }
        // A new card takes priority over a payment/thanks still queued from the previous card.
        if (sceneId != id) clearPlayback() else leaveScenePlayback()
        this.owner = owner
        sceneId = id
        sceneCues = cues.toList()
        if (enabled && foreground) driver.retryFocusAfterInteraction()
        activateScene()
        ensureMusic()
    }

    fun leave(owner: Any) {
        if (this.owner !== owner) return
        leaveScenePlayback()
        this.owner = null
        sceneId = null
        sceneCues = emptyList()
    }

    fun playAction(id: String, cues: List<String>) {
        // Actions received while muted/backgrounded are consumed, never caught up on return.
        val fresh = seenActions.add(id)
        if (seenActions.size > 256) seenActions.remove(seenActions.first())
        if (!fresh || !enabled || !foreground || cues.isEmpty()) return
        driver.retryFocusAfterInteraction()
        clearPlayback()
        cues.forEach { queue.addLast(Cue(sceneId = null, identity = CueIdentity(it, 0))) }
        startNext()
        ensureMusic()
    }

    private fun activateScene() {
        val id = sceneId ?: return
        if (!enabled) return
        val completed = completedScenes[id].orEmpty()
        sceneCues.flatMap { cue -> List(cue.repeatCount) { CueIdentity(cue.key, it) } }
            .filterNot { it in completed }.distinct().forEach { queue.addLast(Cue(id, it)) }
        startNext()
    }

    private fun leaveScenePlayback() {
        queue.removeAll { it.sceneId != null }
        if (current?.sceneId != null) stopCurrent()
        music?.setVolume(musicVolume())
    }

    private fun startNext() {
        if (!enabled || !foreground || player != null) return
        while (queue.isNotEmpty()) {
            val cue = queue.removeFirst()
            val token = Any()
            val next = driver.create(cue.identity.key, repeat = false, volume = 1f,
                onCompleted = { finished(token, completed = true) },
                onError = { finished(token, completed = false) }) ?: continue
            current = cue
            playbackToken = token
            player = next
            music?.setVolume(musicVolume())
            next.setForeground(true)
            return
        }
    }

    private fun finished(token: Any, completed: Boolean) {
        // A released native player may still have a queued callback; it cannot finish its successor.
        if (playbackToken !== token) return
        val cue = current
        if (completed && cue?.sceneId != null) {
            completedScenes.getOrPut(cue.sceneId) { linkedSetOf() }.add(cue.identity)
            if (completedScenes.size > 256) completedScenes.remove(completedScenes.keys.first())
        }
        stopCurrent()
        // A failure advances this attempt. Only a later visit/unmute retries unfinished scene cues.
        startNext()
        music?.setVolume(musicVolume())
    }

    private fun musicVolume() = if (player == null) .35f else .10f

    private fun ensureMusic() {
        if (!enabled || !foreground || music != null) return
        val key = musicKey ?: return
        val token = Any()
        val next = driver.create(key, repeat = true, volume = musicVolume(),
            onCompleted = { musicFinished(key, token) },
            onError = { musicFinished(key, token) }) ?: return
        musicToken = token
        music = next
        next.setForeground(true)
    }

    private fun musicFinished(key: String, token: Any) {
        if (musicKey != key || musicToken !== token) return
        // A failed loop stays stopped until a new interaction or foreground/unmute transition.
        stopMusic()
    }

    private fun stopMusic() {
        val previous = music
        music = null
        musicToken = null
        previous?.close()
    }

    private fun updateDriverActivity() {
        val active = enabled && foreground
        if (active == driverActive) return
        driverActive = active
        driver.setActive(active)
    }

    private fun stopCurrent() {
        val previous = player
        player = null
        current = null
        playbackToken = null
        previous?.close()
    }

    private fun clearPlayback() {
        queue.clear()
        stopCurrent()
        music?.setVolume(musicVolume())
    }

    fun close() {
        driverActive = false
        driver.setActive(false)
        clearPlayback()
        stopMusic()
        musicKey = null
        owner = null
        sceneId = null
        sceneCues = emptyList()
    }
}
