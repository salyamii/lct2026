package ru.nksk.lctapp.core.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.theme.*

/** Shared native composition of the Figma adventure scenes, not a fixed phone canvas. */
@Composable
internal fun AdventureScreen(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes backgroundRes: Int = R.drawable.location_observatory_stage,
    available: Long? = null,
    savings: Long? = null,
    onOpenSavings: (() -> Unit)? = null,
    @DrawableRes characterRes: Int? = null,
    characterMotionIntensity: Float = 1f,
    characterDescription: String? = null,
    speech: String? = null,
    sceneFraction: Float = .60f,
    sceneAspectRatio: Float? = null,
    contentSpacing: Dp = 12.dp,
    pinFooter: Boolean = true,
    scrollWholePage: Boolean = false,
    contentScrollState: ScrollState? = null,
    headerAction: (@Composable () -> Unit)? = null,
    scene: (@Composable BoxScope.() -> Unit)? = null,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize().background(AdventureNight).safeDrawingPadding().imePadding()) {
        val pageScroll = contentScrollState ?: rememberScrollState()
        val density = LocalDensity.current
        val fontScale = density.fontScale
        val largeText = fontScale >= 1.4f
        val compact = maxHeight < 600.dp || largeText
        // Illustrated custom scenes retain their own framing. Adviser scenes wrap the
        // header, balances and character instead of reserving a percentage of empty floor.
        val customStageHeight = if (sceneAspectRatio != null) (maxWidth / sceneAspectRatio).coerceIn(180.dp, 440.dp)
            else (maxHeight * sceneFraction.coerceIn(.30f, .70f)).coerceIn(240.dp, 420.dp)
        var measuredStageHeight by remember { mutableIntStateOf(0) }
        val backgroundHeight = if (measuredStageHeight > 0) with(density) { measuredStageHeight.toDp() }
            else if (scene != null) customStageHeight else 340.dp
        @Composable fun Stage() {
            Column(Modifier.fillMaxWidth()
                .then(if (scene != null) Modifier.height(customStageHeight) else Modifier)
                .onSizeChanged { measuredStageHeight = it.height }) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp).background(AdventureNight, CircleShape)) {
                        Icon(painterResource(R.drawable.menu_chevron), "Назад",
                            Modifier.size(20.dp).rotate(180f), tint = Color.White)
                    }
                    Surface(color = AdventureNight, shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.weight(1f, fill = false)) {
                        Text(title, Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            color = Color.White, fontFamily = Nunito, fontSize = 16.sp, lineHeight = 22.sp)
                    }
                    headerAction?.invoke()
                }
                if (available != null && savings != null) {
                    AdventureBalances(available, savings, onOpenSavings,
                        Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp))
                }
                if (scene != null) Box(Modifier.weight(1f).fillMaxWidth(), content = scene)
                else AdventureAdviser(characterRes, characterMotionIntensity, characterDescription, speech)
            }
        }
        @Composable fun Panel(panelModifier: Modifier, scrollContent: Boolean, includeFooter: Boolean = true) {
            Surface(panelModifier.fillMaxWidth(), color = GamePaper,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
                Box(contentAlignment = Alignment.TopCenter) {
                    Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().let {
                        if (scrollContent && !pinFooter) it.verticalScroll(pageScroll) else it
                    }.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(if (scrollContent && pinFooter) Modifier.weight(1f, fill = false).verticalScroll(pageScroll)
                            else Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(contentSpacing),
                            content = content)
                        if (includeFooter) footer?.let {
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp), content = it)
                        }
                    }
                }
            }
        }
        @Composable fun SceneBackground() {
            // Continue the same scene beneath the rounded panel instead of exposing a
            // rectangular dark seam. The layer follows whichever content owns the scroll.
            Box(Modifier.fillMaxWidth().height(backgroundHeight + 28.dp)) {
                GameArtwork(backgroundRes, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                    0f to AdventureNight.copy(alpha = .48f), .28f to Color.Transparent,
                    1f to AdventureNight.copy(alpha = .10f),
                )))
            }
        }
        if (scrollWholePage) {
            // A form keeps one scroll owner through IME and compact-layout changes. Its
            // primary action remains outside the changing preview/receipt content.
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth().background(GamePaper).verticalScroll(pageScroll)) {
                    SceneBackground()
                    Column(Modifier.fillMaxWidth()) {
                        Stage()
                        Panel(Modifier, scrollContent = false, includeFooter = !pinFooter)
                    }
                }
                if (pinFooter) footer?.let {
                    Surface(Modifier.fillMaxWidth(), color = GamePaper) {
                        Box(contentAlignment = Alignment.TopCenter) {
                            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth()
                                .padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp), content = it)
                        }
                    }
                }
            }
        } else {
            Box(if (compact) Modifier.fillMaxSize().verticalScroll(pageScroll) else Modifier.fillMaxSize()) {
                SceneBackground()
                Column(if (compact) Modifier.fillMaxWidth() else Modifier.fillMaxSize()) {
                    Stage()
                    Panel(if (compact) Modifier else Modifier.weight(1f), scrollContent = !compact)
                }
            }
        }
    }
}

@Composable
private fun AdventureAdviser(
    @DrawableRes characterRes: Int?,
    motionIntensity: Float,
    description: String?,
    speech: String?,
) {
    if (characterRes == null && speech.isNullOrBlank()) return
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 8.dp)) {
        val characterSize = (maxWidth * .60f).coerceAtMost(228.dp)
        val speechWidth = if (characterRes == null) maxWidth else maxWidth - characterSize * .78f
        Box(Modifier.fillMaxWidth().heightIn(min = if (characterRes == null) 0.dp else characterSize)) {
            if (!speech.isNullOrBlank()) {
                AdventureSpeechBubble(speech, Modifier.width(speechWidth).align(Alignment.CenterStart),
                    pointToCharacter = characterRes != null)
            }
            characterRes?.let { resource ->
                Box(Modifier.size(characterSize).align(Alignment.BottomEnd)) {
                    MovingPetArtwork(resource, description, motionIntensity, Modifier.fillMaxSize())
                }
            }
        }
    }
}

/** A shared adviser bubble: its right-hand tail points into the character's canvas. */
@Composable
internal fun AdventureSpeechBubble(text: String, modifier: Modifier = Modifier, pointToCharacter: Boolean = true) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Surface(Modifier.weight(1f), color = GamePaper, shape = RoundedCornerShape(20.dp)) {
            Text(text, Modifier.padding(12.dp), color = GameInk,
                fontFamily = Nunito, fontSize = 14.sp, lineHeight = 19.sp)
        }
        if (pointToCharacter) Canvas(Modifier.width(12.dp).height(20.dp)) {
            drawPath(Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, size.height / 2f)
                lineTo(0f, size.height)
                close()
            }, GamePaper)
        }
    }
}

@Composable
internal fun AdventureBalances(
    available: Long,
    savings: Long,
    onOpenSavings: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AdventureBalance("Доступно", available, Modifier.weight(1f))
        AdventureBalance("В копилке", savings, Modifier.weight(1f), onOpenSavings)
    }
}

@Composable
private fun AdventureBalance(label: String, value: Long, modifier: Modifier, onClick: (() -> Unit)? = null) {
    Surface(modifier.clip(RoundedCornerShape(12.dp)).then(
        if (onClick != null) Modifier.clickable(role = Role.Button, onClickLabel = "Открыть копилку", onClick = onClick)
        else Modifier), color = GamePaper, shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.heightIn(min = 56.dp).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, color = Color(0xFF514972), fontFamily = Nunito,
                    fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp)
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    GameArtwork(R.drawable.menu_coin, null, Modifier.size(20.dp))
                    Text(value.toString(), Modifier.clearAndSetSemantics {
                        text = AnnotatedString("$value монет")
                    }, color = GameInk, fontFamily = Nunito, fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp, lineHeight = 22.sp)
                }
            }
            if (onClick != null) {
                Icon(painterResource(R.drawable.menu_chevron), null,
                    Modifier.size(24.dp), tint = GameInk)
            }
        }
    }
}

@Composable
internal fun AdventureHeading(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, color = GameInk, fontFamily = Rubik, fontWeight = FontWeight.ExtraBold,
        fontSize = 22.sp, lineHeight = 28.sp)
}

@Composable
internal fun AdventureBody(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, color = Color(0xFF383363), fontFamily = Nunito,
        fontSize = 16.sp, lineHeight = 23.sp)
}

@Composable
internal fun AdventurePrimaryButton(text: String, onClick: () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true, loading: Boolean = false) {
    GameActionButton(text, onClick, modifier, enabled, loading)
}

@Composable
internal fun AdventureQuietButton(text: String, onClick: () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true, loading: Boolean = false) {
    GameActionButton(text, onClick, modifier, enabled, loading, style = GameActionStyle.QUIET,
        minHeight = 48.dp, textStyle = GameActionButtonDefaults.QuietText)
}
