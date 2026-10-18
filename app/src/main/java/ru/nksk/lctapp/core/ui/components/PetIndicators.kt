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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
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

/** One division per unit of the existing daily energy allowance. */
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
    Box(modifier.height(frameHeight)) {
        GamePlaque(Modifier.matchParentSize(), artworkRes = R.drawable.menu_energy_frame_v4,
            fixedEdges = true, contentPadding = PaddingValues(0.dp)) {}
        Canvas(Modifier.matchParentSize().clearAndSetSemantics {}) {
            // Match the nine-slice skin: larger text grows the middle, not the carved ends.
            val referenceHeight = minOf(size.height, 64.dp.toPx())
            val side = minOf(referenceHeight * 3f * .115f, size.width * .22f)
            val top = size.height * .50f
            val height = (size.height - referenceHeight * .52f) * (.18f / .48f)
            val divisions = state.maxEnergy.coerceAtLeast(1)
            val gap = minOf(3.dp.toPx(), (size.width - side * 2f) / (divisions * 5f))
            val cellWidth = ((size.width - side * 2f - gap * (divisions - 1)) / divisions).coerceAtLeast(0f)
            val radius = CornerRadius(minOf(height / 2f, 4.dp.toPx()))
            for (division in 0 until divisions) {
                val left = side + division * (cellWidth + gap)
                val origin = Offset(left, top)
                val cell = Size(cellWidth, height)
                drawRoundRect(Color(0xFF503522), origin, cell, radius)
                val charge = (fraction * divisions - division).coerceIn(0f, 1f)
                if (charge > 0f) clipRect(left, top, left + cellWidth * charge, top + height) {
                    drawRoundRect(Brush.verticalGradient(
                        if (low) listOf(Color(0xFFF4C486), Color(0xFFC87832), Color(0xFF955027))
                        else listOf(Color(0xFFFFE8A8), Color(0xFFE8B44F), Color(0xFFAF762B)),
                        startY = top, endY = top + height), origin, cell, radius)
                }
                drawRoundRect(Color(0xFFB28749).copy(alpha = .55f), origin, cell, radius,
                    style = Stroke(.6.dp.toPx()))
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
