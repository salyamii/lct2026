package ru.nksk.lctapp.feature.day.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.nksk.lctapp.core.ui.components.GameArtwork
import ru.nksk.lctapp.core.ui.components.GameInk
import ru.nksk.lctapp.core.ui.components.GamePaper
import ru.nksk.lctapp.core.ui.components.MovingNpcArtwork
import ru.nksk.lctapp.core.ui.components.MovingPetArtwork
import ru.nksk.lctapp.core.ui.components.eventArtworkBounds
import ru.nksk.lctapp.core.ui.components.petArtworkGrounding
import ru.nksk.lctapp.core.ui.game.forLiveDisplay

/** One scene composition for lore, work and unexpected events, using the actual saved pet. */
@Composable
internal fun DayEventScene(state: DayUiState, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
        val focus = state.eventArtwork
        val pet = state.pet?.forLiveDisplay()
        val companionBounds = focus?.takeIf { it.isCharacter }?.let { eventArtworkBounds(it.resource) }
        val petBounds = pet?.artworkRes?.let(::eventArtworkBounds)
        val pair = if (pet != null && companionBounds != null && petBounds != null) {
            eventCharacterLayout(maxWidth.value, maxHeight.value, pet.eventCompanionWidthFraction,
                petBounds, companionBounds, pet.artworkRes?.let(::petArtworkGrounding))
        } else null
        val petSize = minOf(maxHeight, maxWidth * if (focus == null) .86f else .56f)
        focus?.let { art ->
            val focusSize = minOf(maxHeight, maxWidth * .67f)
            if (art.isCharacter) {
                MovingNpcArtwork(art.resource, art.description,
                    pair?.companion?.let { Modifier.offset(it.x.dp, it.y.dp)
                        .wrapContentSize(Alignment.TopStart, unbounded = true).requiredSize(it.size.dp) }
                        ?: Modifier.size(focusSize).align(Alignment.BottomStart))
            } else {
                GameArtwork(art.resource, art.description,
                    Modifier.size(focusSize).align(Alignment.CenterStart),
                    contentScale = ContentScale.Fit)
            }
        }
        if (pet != null) {
            val description = stringResource(pet.descriptionRes, pet.name)
            Box(pair?.pet?.let { Modifier.offset(it.x.dp, it.y.dp)
                .wrapContentSize(Alignment.TopStart, unbounded = true).requiredSize(it.size.dp) }
                ?: Modifier.size(petSize).align(if (focus == null) Alignment.BottomCenter else Alignment.BottomEnd)) {
                pet.artworkRes?.let { resource ->
                    MovingPetArtwork(resource, description, pet.motionIntensity, Modifier.fillMaxSize())
                } ?: Surface(Modifier.align(Alignment.Center), color = GamePaper, shape = RoundedCornerShape(20.dp)) {
                    Text(description, Modifier.padding(16.dp), color = GameInk)
                }
            }
        }
    }
}
