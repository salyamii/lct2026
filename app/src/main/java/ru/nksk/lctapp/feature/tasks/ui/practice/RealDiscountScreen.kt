package ru.nksk.lctapp.feature.tasks.ui.practice

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.nksk.lctapp.R
import ru.nksk.lctapp.domain.minigame.DeedRewardPreview
import ru.nksk.lctapp.feature.tasks.ui.DeedGamePresentation
import ru.nksk.lctapp.core.ui.components.GameArtwork
import ru.nksk.lctapp.core.ui.theme.Rubik
import ru.nksk.lctapp.domain.minigame.DiscountQuestion
import ru.nksk.lctapp.domain.minigame.PracticeChoiceAction
import ru.nksk.lctapp.domain.minigame.PracticeChoiceState
import ru.nksk.lctapp.feature.tasks.ui.DeedColors

@Composable
fun RealDiscountScreen(
    state: PracticeChoiceState<DiscountQuestion>,
    onAction: (PracticeChoiceAction) -> Unit,
    onBack: () -> Unit,
    deed: DeedGamePresentation? = null,
) {
    val question = state.question
    PracticeScaffold(stringResource(R.string.practice_discount_title), R.drawable.location_city,
        R.drawable.npc_butcher_pointing, stringResource(R.string.practice_discount_prompt),
        state.current, state.questions.size, state.correctAnswers, state.finished,
        { onAction(PracticeChoiceAction.Restart) }, onBack, deed, DeedRewardPreview.fromPractice(state)) {
        PracticeBoard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    GameArtwork(question.item.artwork, null, Modifier.size(132.dp))
                    PracticeText(stringResource(question.item.titleRes), bold = true)
                }
                Column(Modifier.weight(1f).clip(RoundedCornerShape(8.dp, 24.dp, 8.dp, 8.dp))
                    .background(DeedColors.CreamCard).padding(vertical = 24.dp, horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val oldDescription = stringResource(R.string.practice_old_price, question.crossedOutPrice)
                    Text(question.crossedOutPrice.toString(),
                        Modifier.clearAndSetSemantics { contentDescription = oldDescription },
                        color = PracticeRed, fontFamily = Rubik, fontSize = 23.sp,
                        textDecoration = TextDecoration.LineThrough)
                    Text(question.todayPrice.toString(), color = PracticeInk,
                        fontFamily = Rubik, fontWeight = FontWeight.ExtraBold, fontSize = 40.sp)
                    PracticeText(stringResource(R.string.practice_price, question.todayPrice))
                }
            }
            PracticeText(stringResource(R.string.practice_yesterday, question.yesterdayPrice),
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(DeedColors.CreamCard).padding(10.dp), bold = true)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (choice in 0..1) PracticeOption(
                    stringResource(if (choice == 0) R.string.practice_trick else R.string.practice_discount), null,
                    Modifier.weight(1f), enabled = deed?.canPlay != false && state.selectedChoice == null,
                    picked = state.selectedChoice == choice,
                    correct = state.selectedChoice?.let { choice == question.correctChoice },
                    onClick = { onAction(PracticeChoiceAction.Answer(choice, state.current)) })
            }
        }
        val explanation = when {
            question.saving > 0 -> stringResource(R.string.practice_discount_saving,
                question.yesterdayPrice, question.todayPrice, question.saving)
            question.saving < 0 -> stringResource(R.string.practice_discount_markup,
                question.yesterdayPrice, question.todayPrice, -question.saving)
            else -> stringResource(R.string.practice_discount_equal, question.todayPrice)
        }
        PracticeFeedback(state, explanation, stringResource(R.string.practice_discount_hint), onAction)
    }
}
