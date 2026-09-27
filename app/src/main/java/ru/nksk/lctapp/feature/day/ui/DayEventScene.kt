package ru.nksk.lctapp.feature.day.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import ru.nksk.lctapp.core.ui.components.MovingPetArtwork

/** One scene composition for lore, work and unexpected events, using the actual saved pet. */
@Composable
internal fun DayEventScene(state: DayUiState, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
        val focus = state.eventArtwork
        val pet = state.pet
        val petSize = minOf(maxHeight, maxWidth * if (focus == null) .86f else .56f)
        focus?.let { art ->
            val focusSize = minOf(maxHeight, maxWidth * .67f)
            if (art.isCharacter) {
                GameArtwork(art.resource, art.description,
                    Modifier.size(focusSize).align(Alignment.BottomStart), contentScale = ContentScale.Fit)
            } else {
                GameArtwork(art.resource, art.description,
                    Modifier.size(focusSize).align(Alignment.CenterStart),
                    contentScale = ContentScale.Fit)
            }
        }
        if (pet != null) {
            val description = stringResource(pet.descriptionRes, pet.name)
            Box(Modifier.size(petSize).align(if (focus == null) Alignment.BottomCenter else Alignment.BottomEnd)) {
                pet.artworkRes?.let { resource ->
                    MovingPetArtwork(resource, description, pet.motionIntensity, Modifier.fillMaxSize())
                } ?: Surface(Modifier.align(Alignment.Center), color = GamePaper, shape = RoundedCornerShape(20.dp)) {
                    Text(description, Modifier.padding(16.dp), color = GameInk)
                }
            }
        }
    }
}
