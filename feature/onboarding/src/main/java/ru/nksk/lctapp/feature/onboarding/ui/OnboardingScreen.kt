package ru.nksk.lctapp.feature.onboarding.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.nksk.lctapp.feature.onboarding.R

private val Night = Color(0xff1f1447)
private val Lime = Color(0xffa8e830)

@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    artwork: OnboardingArtwork,
    saving: Boolean,
    saveFailed: Boolean,
    onAction: (OnboardingAction) -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize().background(Night)) {
        Image(painterResource(artwork.background), null, Modifier.fillMaxSize().blur(1.7.dp),
            contentScale = ContentScale.Crop)
        val actions: (OnboardingAction) -> Unit = { if (!saving) onAction(it) }
        if (maxWidth >= 700.dp && maxWidth > maxHeight) {
            Row(Modifier.fillMaxSize().safeDrawingPadding(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Column(Modifier.widthIn(max = 480.dp)) {
                        Brand(artwork)
                        CharacterScene(artwork, state, actions)
                    }
                }
                Box(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
                    WelcomeCard(state, artwork, saving, saveFailed, onStart)
                }
            }
        } else {
            BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
                val availableHeight = maxHeight
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Layout(
                        modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
                        content = {
                            Column {
                                Brand(artwork)
                                CharacterScene(artwork, state, actions)
                            }
                            WelcomeCard(state, artwork, saving, saveFailed, onStart)
                        },
                    ) { measurables, constraints ->
                        val scene = measurables[0].measure(constraints.copy(minHeight = 0))
                        // Fill the viewport with the card, but keep its maximum height
                        // unbounded so long copy and the button can still scroll normally.
                        val card = measurables[1].measure(constraints.copy(
                            minHeight = (availableHeight.roundToPx() - scene.height).coerceAtLeast(0),
                        ))
                        layout(constraints.maxWidth, scene.height + card.height) {
                            scene.placeRelative(0, 0)
                            card.placeRelative(0, scene.height)
                        }
                    }
                }
            }
        }
        // Content respects safeDrawing; extend the card color behind the transparent system bar.
        Spacer(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            .windowInsetsBottomHeight(WindowInsets.safeDrawing).background(Night))
        if (state.selectionRequired) {
            AlertDialog(
                onDismissRequest = { onAction(OnboardingAction.DismissSelectionDialog) },
                text = { Text(stringResource(R.string.onboarding_selection_required)) },
                confirmButton = {
                    TextButton(onClick = { onAction(OnboardingAction.DismissSelectionDialog) }) {
                        Text(stringResource(R.string.onboarding_understood))
                    }
                },
            )
        }
    }
}

@Composable
private fun Brand(artwork: OnboardingArtwork) {
    Box(Modifier.fillMaxWidth().heightIn(min = 58.dp).padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.CenterStart) {
        Text(stringResource(R.string.onboarding_brand), color = Color.White,
            style = TextStyle(fontFamily = artwork.titleFont, fontWeight = FontWeight.ExtraBold,
                fontSize = 28.sp, lineHeight = 34.sp, shadow = Shadow(Color.Black.copy(alpha = .55f), blurRadius = 8f)))
    }
}

@Composable
private fun WelcomeCard(
    state: OnboardingUiState,
    artwork: OnboardingArtwork,
    saving: Boolean,
    saveFailed: Boolean,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val chooseFirst = stringResource(R.string.onboarding_choose_first)
    Column(modifier.fillMaxWidth().heightIn(min = 324.dp).testTag("onboarding_welcome_card")
        .background(Brush.verticalGradient(listOf(Color(0xf2170f38), Night)), RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
        .padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 32.dp)) {
        Text(stringResource(R.string.onboarding_title), color = Color.White,
            fontFamily = artwork.titleFont, fontWeight = FontWeight.ExtraBold,
            fontSize = 24.sp, lineHeight = 26.sp)
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.onboarding_description), color = Color(0xffccc7e5),
            fontFamily = artwork.bodyFont, fontSize = 14.sp, lineHeight = 20.sp)
        Spacer(Modifier.height(36.dp))
        if (saveFailed) {
            Text(stringResource(R.string.onboarding_save_error), color = Color(0xffffe2b4),
                modifier = Modifier.padding(bottom = 12.dp).semantics { liveRegion = LiveRegionMode.Polite })
        }
        // A muted button must remain clickable to explain why the adventure cannot start yet.
        Button(onClick = onStart, enabled = !saving,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).semantics {
                if (!state.foxSelected) stateDescription = chooseFirst
            },
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (state.foxSelected) Lime else Color(0xff6c677e),
                contentColor = if (state.foxSelected) Color(0xff171440) else Color(0xffd5d0e0),
                disabledContainerColor = Color(0xff6c677e), disabledContentColor = Color(0xffd5d0e0))) {
            Text(stringResource(if (saving) R.string.onboarding_starting else R.string.onboarding_start),
                fontFamily = artwork.titleFont, fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp, lineHeight = 22.sp)
        }
    }
}
