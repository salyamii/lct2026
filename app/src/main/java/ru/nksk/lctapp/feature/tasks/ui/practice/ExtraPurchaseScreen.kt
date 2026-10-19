package ru.nksk.lctapp.feature.tasks.ui.practice

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.nksk.lctapp.R
import ru.nksk.lctapp.domain.minigame.DeedRewardPreview
import ru.nksk.lctapp.feature.tasks.ui.DeedGamePresentation
import ru.nksk.lctapp.domain.minigame.ExtraPurchaseQuestion
import ru.nksk.lctapp.domain.minigame.PracticeChoiceAction
import ru.nksk.lctapp.domain.minigame.PracticeChoiceState
import ru.nksk.lctapp.domain.minigame.PurchaseGoal
import ru.nksk.lctapp.feature.tasks.ui.DeedColors

@Composable
fun ExtraPurchaseScreen(
    state: PracticeChoiceState<ExtraPurchaseQuestion>,
    onAction: (PracticeChoiceAction) -> Unit,
    onBack: () -> Unit,
    deed: DeedGamePresentation? = null,
) {
    val question = state.question
    PracticeScaffold(stringResource(R.string.practice_extra_title), R.drawable.location_workshop_day,
        R.drawable.npc_tiko_workbench, stringResource(R.string.practice_extra_prompt),
        state.current, state.questions.size, state.correctAnswers, state.finished,
        { onAction(PracticeChoiceAction.Restart) }, onBack, deed, DeedRewardPreview.fromPractice(state)) {
        PracticeBoard(background = R.drawable.prop_practice_workbench) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(DeedColors.CreamCard).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PracticeText(stringResource(when (question.goal) {
                    PurchaseGoal.WORKBENCH -> R.string.practice_goal_workbench
                    PurchaseGoal.OBSERVATORY -> R.string.practice_goal_observatory
                    PurchaseGoal.FISHING -> R.string.practice_goal_fishing
                }), Modifier.fillMaxWidth(), bold = true)
                PracticeText(stringResource(when (question.goal) {
                    PurchaseGoal.WORKBENCH -> R.string.practice_goal_workbench_hint
                    PurchaseGoal.OBSERVATORY -> R.string.practice_goal_observatory_hint
                    PurchaseGoal.FISHING -> R.string.practice_goal_fishing_hint
                }), Modifier.fillMaxWidth())
            }
            question.items.indices.chunked(2).forEach { row ->
                PracticeChoices(row.size) { position, modifier ->
                        val index = row[position]
                        val item = question.items[index]
                        PracticeOption(stringResource(item.titleRes), null, modifier, item.artwork,
                            enabled = deed?.canPlay != false && state.selectedChoice == null, picked = state.selectedChoice == index,
                            correct = state.selectedChoice?.let { index == question.correctChoice },
                            onClick = { onAction(PracticeChoiceAction.Answer(index, state.current)) })
                }
            }
        }
        PracticeFeedback(state, stringResource(when (question.goal) {
            PurchaseGoal.WORKBENCH -> R.string.practice_extra_workbench
            PurchaseGoal.OBSERVATORY -> R.string.practice_extra_observatory
            PurchaseGoal.FISHING -> R.string.practice_extra_fishing
        }), stringResource(R.string.practice_extra_hint), onAction)
    }
}
