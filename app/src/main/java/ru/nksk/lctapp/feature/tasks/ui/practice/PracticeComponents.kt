package ru.nksk.lctapp.feature.tasks.ui.practice

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.components.GameRewardProgress
import ru.nksk.lctapp.domain.minigame.DeedRewardPreview
import ru.nksk.lctapp.feature.tasks.ui.DeedGamePresentation
import ru.nksk.lctapp.core.ui.components.GameArtwork
import ru.nksk.lctapp.core.ui.components.GameInk
import ru.nksk.lctapp.core.ui.components.GamePlaque
import ru.nksk.lctapp.core.ui.theme.GameLabelTextWrapping
import ru.nksk.lctapp.core.ui.theme.Nunito
import ru.nksk.lctapp.core.ui.theme.Rubik
import ru.nksk.lctapp.domain.minigame.PracticeChoiceAction
import ru.nksk.lctapp.domain.minigame.PracticeChoiceState
import ru.nksk.lctapp.domain.minigame.PracticeQuestion
import ru.nksk.lctapp.feature.tasks.ui.DeedButton
import ru.nksk.lctapp.feature.tasks.ui.DeedButtonSoft
import ru.nksk.lctapp.feature.tasks.ui.DeedColors
import ru.nksk.lctapp.feature.tasks.ui.DeedHeader
import ru.nksk.lctapp.feature.tasks.ui.DeedBackdrop
import ru.nksk.lctapp.feature.tasks.ui.DeedSheet

internal val PracticeInk = GameInk

/** Shared presentation only. A host supplies all state/actions; there is no world or navigation owner here. */
@Composable
internal fun PracticeScaffold(
    title: String,
    @DrawableRes scene: Int,
    @DrawableRes npc: Int,
    prompt: String,
    current: Int,
    total: Int,
    correct: Int,
    finished: Boolean,
    onAgain: () -> Unit,
    onBack: () -> Unit,
    deed: DeedGamePresentation? = null,
    reward: DeedRewardPreview,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().background(DeedColors.Scene)) {
        Box {
            DeedBackdrop(scene, Modifier.fillMaxWidth().height(116.dp), blurred = deed?.storyAction == false)
            Box(Modifier.fillMaxWidth().height(116.dp).background(
                Brush.verticalGradient(listOf(Color(0x99171440), Color.Transparent))))
            DeedHeader(title, onBack)
        }
        DeedSheet(Modifier.weight(1f)) {
            if (deed != null && !deed.storyAction && deed.maximumReward > 0) {
                GameRewardProgress(reward.reward(deed.maximumReward), deed.maximumReward)
                Spacer(Modifier.height(12.dp))
            }
            val scroll = rememberScrollState()
            LaunchedEffect(current, finished) { scroll.scrollTo(0) }
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    PracticeText(stringResource(R.string.practice_round, minOf(current + 1, total), total))
                    PracticeText(stringResource(R.string.practice_score, correct))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (finished) stringResource(R.string.practice_result) else prompt,
                        Modifier.weight(1f).padding(end = 8.dp),
                        color = PracticeInk, fontFamily = Nunito, fontWeight = FontWeight.Medium,
                        fontSize = 16.sp, lineHeight = 22.sp, style = GameLabelTextWrapping)
                    GameArtwork(npc, null, Modifier.size(78.dp, 100.dp))
                }
                if (finished) {
                    PracticeBoard {
                        GameArtwork(R.drawable.gear_stargazing_trip, null, Modifier.size(120.dp))
                        PracticeText(stringResource(R.string.practice_result_count, correct, total), bold = true)
                    }
                    if (deed == null) {
                        DeedButton(stringResource(R.string.practice_again), onAgain, Modifier.fillMaxWidth())
                        DeedButtonSoft(stringResource(R.string.practice_back), onBack, Modifier.fillMaxWidth())
                    }
                } else content()
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
internal fun PracticeBoard(
    modifier: Modifier = Modifier,
    @DrawableRes background: Int? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    Box(modifier.fillMaxWidth().clip(shape)) {
        if (background != null) {
            GameArtwork(background, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
            Box(Modifier.matchParentSize().background(Color(0x33794B24))
                .border(2.dp, Color(0xFF79583C), shape))
        } else {
            GamePlaque(Modifier.matchParentSize(), light = true, fixedEdges = true) {}
        }
        Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
internal fun PracticeText(text: String, modifier: Modifier = Modifier, bold: Boolean = false) {
    Text(text, modifier, color = PracticeInk, fontFamily = Nunito,
        fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.SemiBold,
        fontSize = 15.sp, lineHeight = 21.sp, textAlign = TextAlign.Center, style = GameLabelTextWrapping)
}

/** Wide choices share a height; narrow screens and large fonts get full-width choices. */
@Composable
internal fun PracticeChoices(count: Int, content: @Composable (Int, Modifier) -> Unit) {
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 280.dp * fontScale) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(count) { content(it, Modifier.fillMaxWidth()) }
            }
        } else {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(count) { content(it, Modifier.weight(1f).fillMaxHeight()) }
            }
        }
    }
}

@Composable
internal fun PracticeOption(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    @DrawableRes artwork: Int? = null,
    enabled: Boolean,
    picked: Boolean,
    correct: Boolean?,
    onClick: () -> Unit,
) {
    GamePlaque(modifier.heightIn(min = 64.dp)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .semantics(mergeDescendants = true) {},
        light = true, fixedEdges = true, fillVisibleHeight = true,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)) {
            if (artwork != null) GameArtwork(artwork, null, Modifier.size(72.dp))
            PracticeText(title, bold = true)
            if (subtitle != null) PracticeText(subtitle)
            if (correct == true || picked) {
                Text(stringResource(if (correct == true) R.string.practice_right_choice else R.string.practice_your_choice),
                    color = PracticeInk, fontFamily = Nunito, fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, style = GameLabelTextWrapping)
            }
        }
    }
}

@Composable
internal fun <Q : PracticeQuestion> PracticeFeedback(
    state: PracticeChoiceState<Q>,
    explanation: String,
    hint: String,
    onAction: (PracticeChoiceAction) -> Unit,
) {
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(state.current, state.selectedChoice) {
        if (state.selectedChoice != null) requester.bringIntoView()
    }
    Column(Modifier.fillMaxWidth().bringIntoViewRequester(requester),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.selectedChoice == null) PracticeText(hint, Modifier.fillMaxWidth())
        else {
            FeedbackNote(state.lastCorrect == true, explanation)
            DeedButton(stringResource(if (state.current == state.questions.lastIndex)
                R.string.practice_finish else R.string.practice_next),
                { onAction(PracticeChoiceAction.Next(state.current)) }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
internal fun FeedbackNote(correct: Boolean, explanation: String) {
    Column(Modifier.fillMaxWidth()
        .semantics { liveRegion = LiveRegionMode.Polite }.padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(stringResource(if (correct) R.string.practice_correct else R.string.practice_incorrect),
            color = PracticeInk, fontFamily = Rubik,
            fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Text(explanation, color = PracticeInk, fontFamily = Nunito, fontSize = 15.sp,
            lineHeight = 22.sp, style = GameLabelTextWrapping)
    }
}
