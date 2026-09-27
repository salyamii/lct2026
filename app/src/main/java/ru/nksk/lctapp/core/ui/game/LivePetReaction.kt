package ru.nksk.lctapp.core.ui.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import ru.nksk.lctapp.domain.pet.PetState

/** Temporary presentation of the real pet. Neither version is written back into the game. */
internal data class LivePetReaction(
    val rawPet: PetState,
    val effectivePet: PetState,
    val reactionVisible: Boolean,
)

internal val LocalLivePetReaction = staticCompositionLocalOf<LivePetReaction?> { null }

/** Only live-game screens opt in. Historical/alternative pets use the pure mapper directly. */
@Composable
internal fun livePetPresentation(): AdventurePetPresentation? = LocalLivePetReaction.current?.let {
    it.effectivePet.toAdventurePetPresentation(showReaction = it.reactionVisible)
}

@Composable
internal fun AdventurePetPresentation.forLiveDisplay(): AdventurePetPresentation = livePetPresentation() ?: this

@Composable
internal fun PetState.toLiveAdventurePetPresentation(): AdventurePetPresentation =
    livePetPresentation() ?: toAdventurePetPresentation()
