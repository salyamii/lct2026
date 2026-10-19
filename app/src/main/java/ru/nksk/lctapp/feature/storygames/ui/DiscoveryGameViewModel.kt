package ru.nksk.lctapp.feature.storygames.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.nksk.lctapp.domain.minigame.chapterone.ChapterDiscovery
import ru.nksk.lctapp.domain.minigame.chapterone.ChapterDiscoveryGame
import ru.nksk.lctapp.domain.minigame.chapterone.DiscoveryAction
import ru.nksk.lctapp.domain.minigame.chapterone.DiscoveryScene

/** Screen progress only; the event host owns all effects and the explicit final confirmation. */
@HiltViewModel
internal class DiscoveryGameViewModel @Inject constructor(private val savedState: SavedStateHandle) : ViewModel() {
    private val scene = DiscoveryScene.entries.firstOrNull { it.name == savedState.get<String>("discovery.scene") }
        ?: DiscoveryScene.OBSERVATORY
    private val mutableState = MutableStateFlow(ChapterDiscoveryGame.restore(scene,
        stage = savedState["discovery.stage"] ?: 0,
        revealed = savedState.get<IntArray>("discovery.revealed")?.toList().orEmpty(),
        placed = savedState.get<IntArray>("discovery.placed")?.toList().orEmpty(),
        selected = savedState["discovery.selected"],
        joined = savedState.get<IntArray>("discovery.joined")?.toList().orEmpty(),
        focus = savedState["discovery.focus"] ?: 0f,
        reflected = savedState.get<IntArray>("discovery.reflected")?.toList().orEmpty(),
        version = savedState["discovery.version"] ?: ChapterDiscoveryGame.SAVE_VERSION,
    ))
    val uiState = mutableState.asStateFlow()

    fun prepare(scene: DiscoveryScene) {
        if (mutableState.value.scene != scene) publish(ChapterDiscovery(scene))
    }

    fun onAction(action: DiscoveryAction) {
        val current = mutableState.value
        val next = ChapterDiscoveryGame.reduce(current, action)
        if (next != current) publish(next)
    }

    private fun publish(game: ChapterDiscovery) {
        savedState["discovery.version"] = ChapterDiscoveryGame.SAVE_VERSION
        savedState["discovery.scene"] = game.scene.name
        savedState["discovery.stage"] = game.stage
        savedState["discovery.revealed"] = game.revealed.toIntArray()
        savedState["discovery.placed"] = game.placed.toIntArray()
        savedState["discovery.selected"] = game.selected
        savedState["discovery.joined"] = game.joined.toIntArray()
        savedState["discovery.focus"] = game.focus
        savedState["discovery.reflected"] = game.reflected.toIntArray()
        mutableState.value = game
    }
}
