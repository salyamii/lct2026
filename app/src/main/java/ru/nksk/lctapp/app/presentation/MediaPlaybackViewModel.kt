package ru.nksk.lctapp.app.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.Lazy
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import ru.nksk.lctapp.core.ui.game.paymentAudioCues
import ru.nksk.lctapp.app.di.MediaComputationDispatcher
import ru.nksk.lctapp.core.ui.media.AssetMediaPlayerFactory
import ru.nksk.lctapp.core.ui.media.EventAudioController
import ru.nksk.lctapp.domain.engine.GameSession
import ru.nksk.lctapp.domain.engine.GameCatalog
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.media.MediaPreferences
import ru.nksk.lctapp.domain.media.MediaPreferencesRepository

internal data class MediaPlaybackUiState(val soundEnabled: Boolean = false, val loaded: Boolean = false,
    val saving: Boolean = false, val error: String? = null, val musicCueKey: String? = null)
internal data class MediaCueRequest(val id: String, val cues: List<String>)

@HiltViewModel
internal class MediaPlaybackViewModel @Inject constructor(
    private val preferences: MediaPreferencesRepository,
    private val factory: Lazy<AssetMediaPlayerFactory>,
    private val session: GameSession,
    @param:MediaComputationDispatcher private val computation: CoroutineDispatcher,
) : ViewModel() {
    val playerFactory: AssetMediaPlayerFactory get() = factory.get()
    private val retainedAudio = lazy { EventAudioController(playerFactory) }
    val audioController: EventAudioController get() = retainedAudio.value
    private val state = MutableStateFlow(MediaPlaybackUiState())
    val uiState = state.asStateFlow()
    private var observation: Job? = null
    private var pendingSound: Boolean? = null
    private var writeError: String? = null
    private var currentMusic: String? = null
    private val cues = Channel<MediaCueRequest>(Channel.BUFFERED)
    val actionCues = cues.receiveAsFlow()

    init {
        observePreferences()
        observeMusic()
        viewModelScope.launch {
            session.appliedCommands.collect { applied ->
                val sounds = paymentAudioCues(applied, session.catalog)
                if (state.value.loaded && state.value.soundEnabled && sounds.isNotEmpty()) {
                    cues.trySend(MediaCueRequest(applied.request.id, sounds))
                }
            }
        }
    }

    private fun observePreferences(restart: Boolean = false) {
        if (!restart && observation?.isActive == true) return
        observation?.cancel()
        observation = viewModelScope.launch {
            preferences.observe().retryWhen { cause, attempt ->
                if (cause is CancellationException) throw cause
                // Do not replace unreadable storage with a persisted/default preference.
                state.value = state.value.copy(soundEnabled = false, loaded = false, musicCueKey = null,
                    error = ReadError)
                delay(mediaRetryDelay(attempt))
                true
            }.collect(::preferencesChanged)
        }
    }

    private fun preferencesChanged(preferences: MediaPreferences) {
        if (!state.value.saving && pendingSound == preferences.soundEnabled) {
            // A later successful read (including a Settings change) can confirm an uncertain write.
            pendingSound = null
            writeError = null
        }
        state.value = state.value.copy(soundEnabled = preferences.soundEnabled, loaded = true,
            musicCueKey = currentMusic, error = writeError)
    }

    private fun observeMusic() {
        viewModelScope.launch {
            // Observation does not initialize a world or load history merely to choose its soundtrack.
            session.observe()
                // Chapter completion depends on decisions; money, clothing and UI facts do not change it.
                .distinctUntilChangedBy { it?.story?.decisions }
                .map { currentChapterMusicCue(it, session.catalog) }.distinctUntilChanged()
                .flowOn(computation)
                .retryWhen { cause, attempt ->
                    if (cause is CancellationException) throw cause
                    currentMusic = null
                    state.value = state.value.copy(musicCueKey = null)
                    delay(mediaRetryDelay(attempt))
                    true
                }.collect { cue ->
                    currentMusic = cue
                    state.value = state.value.copy(musicCueKey = cue.takeIf { state.value.loaded })
                }
        }
    }

    fun setSoundEnabled(enabled: Boolean) {
        if (state.value.saving || !state.value.loaded) return
        if (state.value.soundEnabled == enabled && pendingSound == null) return
        pendingSound = enabled
        writeError = null
        state.value = state.value.copy(saving = true, error = null)
        viewModelScope.launch {
            try {
                preferences.setSoundEnabled(enabled)
                pendingSound = null
                writeError = null
                state.value = state.value.copy(soundEnabled = enabled && state.value.loaded, saving = false,
                    error = if (state.value.loaded) null else ReadError)
                if (!state.value.loaded) observePreferences(restart = true)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                val confirmed = state.value.loaded && state.value.soundEnabled == enabled
                if (confirmed) pendingSound = null
                writeError = if (confirmed) null else "Не удалось сохранить настройку звука."
                state.value = state.value.copy(saving = false,
                    error = if (state.value.loaded) writeError else ReadError)
            }
        }
    }

    fun retry() {
        if (state.value.saving) return
        pendingSound?.let { if (state.value.loaded) { setSoundEnabled(it); return } }
        state.value = state.value.copy(error = null)
        observePreferences(restart = true)
    }

    override fun onCleared() {
        if (retainedAudio.isInitialized()) retainedAudio.value.close()
    }

    private companion object { const val ReadError = "Не удалось прочитать настройку звука." }
}

/** Same completed-campaign policy as story age: retain the final act after its finale. */
internal fun currentChapterMusicCue(game: GameState?, catalog: GameCatalog): String? {
    if (game == null) return null
    val progress = catalog.storyProgress(game)
    val act = progress.currentAct ?: catalog.storyCampaign?.acts?.lastOrNull()?.takeIf { progress.campaignComplete }
    return act?.eventIds?.firstNotNullOfOrNull { eventId -> catalog.cards[eventId]?.presentation?.media?.musicCueKey }
}

private fun mediaRetryDelay(attempt: Long): Long = (1_000L shl attempt.coerceIn(0, 5).toInt()).coerceAtMost(30_000L)
