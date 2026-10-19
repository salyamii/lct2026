package ru.nksk.lctapp.core.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.theme.Nunito

/** Saved needs, not real-time timers. Details are available without advancing the game. */
@Composable
internal fun PetIndicators(
    state: PetIndicatorsUiState,
    modifier: Modifier = Modifier,
    onFeed: (() -> Unit)? = null,
    interactionEnabled: Boolean = true,
    satietyModifier: Modifier = Modifier,
) {
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    PetIndicatorsHud(state, modifier, interactionEnabled, satietyModifier,
        canChooseMeal = onFeed != null && state.satiety != PetSatietyUiState.FED) { kind ->
        if (kind == PetIndicatorKind.SATIETY && state.satiety != PetSatietyUiState.FED && onFeed != null) {
            onFeed()
        } else selected = kind.name
    }
    selected?.let { key ->
        val (title, explanation) = when (PetIndicatorKind.valueOf(key)) {
            PetIndicatorKind.ENERGY -> "Энергия" to buildString {
                if (!state.demoMode) state.energy?.let { append("$it из ${state.maxEnergy}. ") }
                append(state.energyExplanation)
            }
            PetIndicatorKind.SATIETY -> "Сытость" to
                if (state.satiety == PetSatietyUiState.FED) "Сегодня уже поели" else state.satiety.explanation
            PetIndicatorKind.MOOD -> "Настроение" to state.mood.explanation
        }
        AlertDialog(onDismissRequest = { selected = null }, containerColor = GamePaper,
            title = { AdventureHeading(title) },
            text = { AdventureBody(explanation, Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = { selected = null }) { Text("Понятно", color = GameInk) } })
    }
}

/** Three independent game objects, with the actual food and emotion drawn in their icons. */
@Composable
private fun PetIndicatorsHud(
    state: PetIndicatorsUiState,
    modifier: Modifier,
    interactionEnabled: Boolean,
    satietyModifier: Modifier,
    canChooseMeal: Boolean,
    onSelect: (PetIndicatorKind) -> Unit,
) {
    val energyDescription = "Энергия: " + when {
        state.energy == null -> state.energyLabel
        state.demoMode -> "не тратится, режим бога"
        else -> "${state.energy} из ${state.maxEnergy}. ${state.energyLabel}"
    }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        FramedEnergyGauge(
            state = state,
            modifier = Modifier.weight(1f)
                .clickable(enabled = interactionEnabled, role = Role.Button,
                    onClickLabel = "Подробнее об энергии") { onSelect(PetIndicatorKind.ENERGY) }
                .semantics(mergeDescendants = true) { contentDescription = energyDescription },
        )
        StateIconButton(
            resource = when (state.satiety) {
                PetSatietyUiState.FED -> R.drawable.menu_satiety_fed_v2
                PetSatietyUiState.NOT_EATEN, PetSatietyUiState.HUNGRY -> R.drawable.menu_satiety_hungry_v2
            },
            description = "Сытость: ${state.satiety.label}",
            onClick = { onSelect(PetIndicatorKind.SATIETY) },
            modifier = satietyModifier,
            enabled = interactionEnabled,
            onClickLabel = if (canChooseMeal) "Выбрать обед" else "Подробнее о сытости",
        )
        StateIconButton(
            resource = when (state.mood) {
                PetMoodUiState.CALM -> R.drawable.menu_mood_calm_v2
                PetMoodUiState.HAPPY -> R.drawable.menu_mood_happy_v2
                PetMoodUiState.UPSET, PetMoodUiState.HUNGRY -> R.drawable.menu_mood_upset_v2
                PetMoodUiState.THINKING, PetMoodUiState.WORRIED, PetMoodUiState.NEEDS_CARE -> R.drawable.menu_mood_worried_v2
                PetMoodUiState.TIRED -> R.drawable.menu_mood_tired_v2
            },
            description = "Настроение: ${state.mood.label}",
            onClick = { onSelect(PetIndicatorKind.MOOD) },
            enabled = interactionEnabled,
        )
    }
}

/** One paw print per unit of the existing daily energy allowance. */
@Composable
private fun FramedEnergyGauge(state: PetIndicatorsUiState, modifier: Modifier) {
    val frameHeight = 68.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
    val edgeHeight = minOf(frameHeight, 64.dp) * .26f
    val captionTop = edgeHeight * (.17f / .26f)
    val captionBottom = edgeHeight + (frameHeight - edgeHeight * 2) * ((.40f - .26f) / .48f)
    val energy = if (state.demoMode) state.maxEnergy else state.energy
    val fraction by animateFloatAsState(
        targetValue = ((energy ?: 0).toFloat() / state.maxEnergy.coerceAtLeast(1)).coerceIn(0f, 1f),
        animationSpec = tween(250), label = "Framed energy level",
    )
    val low = energy != null && energy <= 1 && !state.demoMode
    // Simple silhouette stays legible in the narrow rail; no small raster copies are decoded.
    val paw = remember { energyPawOutline() }
    val chargedPaw = remember(low) {
        Brush.verticalGradient(if (low) listOf(Color(0xFFF7D6A0), Color(0xFFD99447), Color(0xFFAA632B))
            else listOf(Color(0xFFFFF1C1), Color(0xFFF0C064), Color(0xFFC18B3C)), startY = 0f, endY = 100f)
    }
    Box(modifier.height(frameHeight)) {
        GamePlaque(Modifier.matchParentSize(), artworkRes = R.drawable.menu_energy_frame_v4,
            fixedEdges = true, contentPadding = PaddingValues(0.dp)) {}
        Canvas(Modifier.matchParentSize().clearAndSetSemantics {}) {
            // Match the nine-slice skin: larger text grows the middle, not the carved ends.
            val referenceHeight = minOf(size.height, 64.dp.toPx())
            val side = minOf(referenceHeight * 3f * .115f, size.width * .22f)
            val edge = referenceHeight * .26f
            val middle = size.height - edge * 2f
            val top = edge + middle * ((.475f - .26f) / .48f)
            val height = middle * (.225f / .48f)
            val divisions = state.maxEnergy.coerceAtLeast(1)
            val cellWidth = ((size.width - side * 2f) / divisions).coerceAtLeast(0f)
            val pawSize = minOf(height, cellWidth * .80f)
            for (division in 0 until divisions) {
                val left = side + cellWidth * (division + .5f) - pawSize / 2f
                val charge = (fraction * divisions - division).coerceIn(0f, 1f)
                translate(left, top + (height - pawSize) / 2f) {
                    scale(pawSize / 100f, pawSize / 100f, pivot = Offset.Zero) {
                        translate(top = 3f) { drawPath(paw, Color(0xAA24170F)) }
                        drawPath(paw, Color(0xFF533A29))
                        drawPath(paw, Color(0xFF997449), style = Stroke(2.5f))
                        // Whole prints brighten/fade; no rectangular fill or clipped half-paws.
                        if (charge > 0f) {
                            drawPath(paw, chargedPaw, alpha = charge)
                            drawPath(paw, Color(0xFFFFE6AA), alpha = charge * .85f, style = Stroke(2f))
                        }
                    }
                }
            }
        }
        Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(top = captionTop)
            .height(captionBottom - captionTop).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
            Text(if (state.demoMode) "Энергия ∞" else "Энергия", color = Color(0xFFFFEBC5),
                fontFamily = Nunito, fontWeight = FontWeight.ExtraBold,
                fontSize = 12.sp, lineHeight = 15.sp, maxLines = 1)
        }
    }
}

/** Four toes and a soft pad, cached once and scaled uniformly inside each energy slot. */
private fun energyPawOutline() = Path().apply {
    moveTo(50f, 45f)
    cubicTo(38f, 45f, 34f, 57f, 25f, 68f)
    cubicTo(18f, 77f, 22f, 91f, 32f, 92f)
    cubicTo(40f, 93f, 44f, 87f, 50f, 87f)
    cubicTo(56f, 87f, 60f, 93f, 68f, 92f)
    cubicTo(78f, 91f, 82f, 77f, 75f, 68f)
    cubicTo(66f, 57f, 62f, 45f, 50f, 45f)
    close()
    moveTo(17f, 29f)
    cubicTo(8f, 30f, 6f, 40f, 11f, 49f)
    cubicTo(16f, 59f, 26f, 61f, 30f, 55f)
    cubicTo(35f, 47f, 27f, 29f, 17f, 29f)
    close()
    moveTo(35f, 7f)
    cubicTo(24f, 7f, 23f, 20f, 27f, 31f)
    cubicTo(31f, 41f, 41f, 43f, 46f, 35f)
    cubicTo(51f, 26f, 46f, 7f, 35f, 7f)
    close()
    moveTo(65f, 7f)
    cubicTo(54f, 7f, 49f, 26f, 54f, 35f)
    cubicTo(59f, 43f, 69f, 41f, 73f, 31f)
    cubicTo(77f, 20f, 76f, 7f, 65f, 7f)
    close()
    moveTo(83f, 29f)
    cubicTo(73f, 29f, 65f, 47f, 70f, 55f)
    cubicTo(74f, 61f, 84f, 59f, 89f, 49f)
    cubicTo(94f, 40f, 92f, 30f, 83f, 29f)
    close()
}

@Composable
private fun StateIconButton(
    @DrawableRes resource: Int,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClickLabel: String = "Подробнее",
) {
    Box(modifier.size(52.dp).clickable(enabled = enabled, role = Role.Button, onClickLabel = onClickLabel, onClick = onClick)
        .semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        GameArtwork(resource = resource, contentDescription = null, modifier = Modifier.size(48.dp))
    }
}

/** The caller places these with the relevant choice, so multiple choices never share a vague cost. */
@Composable
internal fun PetChoiceEffects(effects: List<PetIndicatorEffectUiState>, modifier: Modifier = Modifier) {
    if (effects.isEmpty()) return
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        effects.forEach { effect ->
            Row(Modifier.padding(vertical = 3.dp).clearAndSetSemantics {
                    contentDescription = listOfNotNull(effect.label, effect.explanation).joinToString(". ")
                }, verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IndicatorArtwork(effect.kind, Modifier.size(22.dp))
                Text(effect.label, color = GameInk, fontFamily = Nunito, fontSize = 13.sp, lineHeight = 18.sp,
                    fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Fixed category illustrations; the adjacent text states the actual need or emotion. */
@Composable
private fun IndicatorArtwork(kind: PetIndicatorKind, modifier: Modifier) {
    GameArtwork(resource = when (kind) {
        PetIndicatorKind.ENERGY -> R.drawable.menu_status_energy
        PetIndicatorKind.SATIETY -> R.drawable.menu_status_satiety
        PetIndicatorKind.MOOD -> R.drawable.menu_status_mood
    }, contentDescription = null, modifier = modifier)
}
