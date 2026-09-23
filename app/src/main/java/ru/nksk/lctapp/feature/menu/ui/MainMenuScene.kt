package ru.nksk.lctapp.feature.menu.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.components.GameArtwork

@Composable
internal fun VillageBackdrop(painter: Painter) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Figma crops a 483 × 858 image at x=-72 in a 390 × 844 frame.
        val scale = maxOf(maxWidth / 390.dp, maxHeight / 844.dp)
        Image(
            painter = painter,
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.align(Alignment.Center)
                .offset(x = (-25.5f * scale).dp, y = (7f * scale).dp)
                .requiredSize((483f * scale).dp, (858f * scale).dp)
                .blur(1.5.dp),
        )
    }
    Box(Modifier.fillMaxSize().background(Color(0xFF090B21).copy(alpha = 0.18f)))
}

@Composable
internal fun CharacterScene(
    pet: MainMenuPetUiState,
    modifier: Modifier = Modifier,
    artworkModifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.offset(y = 64.dp), contentAlignment = Alignment.Center) {
        // Scale the full shared canvas and its ground shadow together.
        val characterSize = minOf(maxWidth * 1.18f, maxHeight * 0.92f, 560.dp) * pet.artworkScale
        val artwork = pet.artworkRes
        if (artwork == null) {
            MenuText(stringResource(pet.descriptionRes, pet.name), size = 18, modifier = artworkModifier)
            return@BoxWithConstraints
        }
        GameArtwork(
            R.drawable.menu_ground_shadow, null,
            Modifier.offset(x = characterSize * -0.03f, y = characterSize * 0.378f)
                .size(characterSize * 0.328f, characterSize * 0.117f),
        )
        MovingPet(
            artwork = artwork,
            description = stringResource(pet.descriptionRes, pet.name),
            intensity = pet.motionIntensity,
            modifier = Modifier.requiredSize(characterSize).then(artworkModifier),
        )
    }
}

@Composable
internal fun FrostedVillagePanel(viewport: DpSize, position: Offset, modifier: Modifier, painter: Painter) {
    Box(modifier) {
        Box(
            Modifier.matchParentSize().blur(9.dp).drawWithCache {
                val scale = maxOf(viewport.width / 390.dp, viewport.height / 844.dp)
                val artworkWidth = (483f * scale).dp.toPx()
                val artworkHeight = (858f * scale).dp.toPx()
                val left = viewport.width.toPx() / 2f - artworkWidth / 2f - (25.5f * scale).dp.toPx()
                val top = viewport.height.toPx() / 2f - artworkHeight / 2f + (7f * scale).dp.toPx()
                onDrawBehind {
                    translate(left - position.x, top - position.y) {
                        with(painter) { draw(Size(artworkWidth, artworkHeight)) }
                    }
                    drawRect(Color(0xFF090B21).copy(alpha = 0.18f))
                }
            },
        )
        Box(Modifier.matchParentSize().background(Color(0xFF130E30).copy(alpha = 0.30f)))
    }
}
