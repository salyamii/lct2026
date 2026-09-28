package ru.nksk.lctapp.feature.onboarding.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.nksk.lctapp.feature.onboarding.R
import kotlin.math.roundToInt

private sealed interface ArtworkLoad {
    data object Loading : ArtworkLoad
    data object Failed : ArtworkLoad
    data class Ready(val art: CharacterArtwork) : ArtworkLoad
}

@Composable
internal fun CharacterScene(
    artwork: OnboardingArtwork,
    state: OnboardingUiState,
    onAction: (OnboardingAction) -> Unit,
    modifier: Modifier = Modifier,
    loadingIndicator: @Composable (Modifier) -> Unit,
) {
    val resources = LocalContext.current.resources
    var attempt by remember { mutableIntStateOf(0) }
    val loaded by produceState<ArtworkLoad>(ArtworkLoad.Loading, artwork, attempt) {
        value = ArtworkLoad.Loading
        value = try {
            ArtworkLoad.Ready(withContext(Dispatchers.Default) { loadCharacters(resources, artwork) })
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) { ArtworkLoad.Failed }
    }
    val action by rememberUpdatedState(onAction)
    val highlight by animateFloatAsState(if (state.foxSelected) 1f else 0f, tween(240), label = "selection")
    val gray = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) }
    val names = listOf(stringResource(R.string.onboarding_owl), stringResource(R.string.onboarding_fox),
        stringResource(R.string.onboarding_axolotl))
    BoxWithConstraints(modifier.fillMaxWidth().aspectRatio(402f / 456f).clipToBounds()) {
        val scale = maxWidth / 402f
        Image(painterResource(artwork.groundShadow), null,
            Modifier.offset(scale * 12f, scale * 419f).size(scale * 390f, scale * 27f),
            contentScale = ContentScale.FillBounds)
        when (val result = loaded) {
            ArtworkLoad.Loading -> loadingIndicator(Modifier.align(Alignment.Center))
            ArtworkLoad.Failed -> TextButton(onClick = { attempt++ }, Modifier.align(Alignment.Center)) {
                Text(stringResource(R.string.onboarding_art_retry))
            }
            is ArtworkLoad.Ready -> {
                val art = result.art
                val hitLayers = remember(art) { art.layers.map { it.hit } }
                Canvas(Modifier.fillMaxSize().pointerInput(art) {
                    detectTapGestures { position ->
                        hitCharacter(hitLayers, position.x * 402f / size.width,
                            position.y * 456f / size.height)?.let { index ->
                            action(if (index == 1) OnboardingAction.SelectFox else OnboardingAction.UnavailableCharacter)
                        }
                    }
                }) {
                    val factor = size.width / 402f
                    art.layers.forEachIndexed { index, layer ->
                        if (index == 1 && highlight > 0f) {
                            drawCharacter(art.foxGlow, layer.hit, factor, null, highlight)
                        }
                        drawCharacter(layer.image, layer.hit, factor, if (index == 1) null else gray)
                    }
                }
                // Semantic/focus bounds are compact opaque bounds. Pointer taps are resolved once
                // on the shared canvas, so rectangular transparent margins cannot intercept them.
                art.layers.forEachIndexed { index, layer ->
                    val left = layer.opaqueLeft.coerceIn(0f, 402f)
                    val top = layer.opaqueTop.coerceIn(0f, 456f)
                    val right = layer.opaqueRight.coerceIn(left, 402f)
                    val bottom = layer.opaqueBottom.coerceIn(top, 456f)
                    val activate = {
                        action(if (index == 1) OnboardingAction.SelectFox else OnboardingAction.UnavailableCharacter)
                    }
                    Box(Modifier.offset(scale * left, scale * top)
                        .size(scale * (right - left), scale * (bottom - top))
                        .semantics {
                            contentDescription = names[index]
                            role = Role.Button
                            selected = index == 1 && state.foxSelected
                            onClick { activate(); true }
                        }
                        .onKeyEvent {
                            if (it.type == KeyEventType.KeyUp &&
                                (it.key == Key.Enter || it.key == Key.Spacebar || it.key == Key.DirectionCenter)) {
                                activate(); true
                            } else false
                        }.focusable())
                }
            }
        }
        AnimatedVisibility(state.noticeVisible,
            Modifier.align(Alignment.TopStart).padding(start = 12.dp, end = 12.dp, top = 33.dp),
            enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { -it / 3 },
            exit = fadeOut(tween(180)) + slideOutVertically(tween(180)) { -it / 3 }) {
            Text(stringResource(R.string.onboarding_unavailable),
                Modifier.background(Color(0xffdddddd).copy(alpha = .95f), RoundedCornerShape(23.dp))
                    .padding(10.dp).semantics { liveRegion = LiveRegionMode.Polite },
                color = Color.Black, fontFamily = artwork.bodyFont, fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp, lineHeight = 25.sp)
        }
    }
}

private fun DrawScope.drawCharacter(
    image: androidx.compose.ui.graphics.ImageBitmap,
    bounds: HitLayer,
    scale: Float,
    filter: ColorFilter?,
    alpha: Float = 1f,
) = drawImage(image, dstOffset = IntOffset((bounds.left * scale).roundToInt(), (bounds.top * scale).roundToInt()),
    dstSize = IntSize((bounds.width * scale).roundToInt(), (bounds.height * scale).roundToInt()),
    colorFilter = filter, alpha = alpha)
