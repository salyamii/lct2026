package ru.nksk.lctapp.feature.tasks.ui.practice

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.nksk.lctapp.R
import ru.nksk.lctapp.domain.minigame.DeedRewardPreview
import ru.nksk.lctapp.feature.tasks.ui.DeedGamePresentation
import ru.nksk.lctapp.core.ui.components.GameArtwork
import ru.nksk.lctapp.domain.minigame.NeedWantQuestion
import ru.nksk.lctapp.domain.minigame.PracticeChoiceAction
import ru.nksk.lctapp.domain.minigame.PracticeChoiceState

@Composable
fun NeedWantScreen(
    state: PracticeChoiceState<NeedWantQuestion>,
    onAction: (PracticeChoiceAction) -> Unit,
    onBack: () -> Unit,
    deed: DeedGamePresentation? = null,
    onHome: () -> Unit = onBack,
) {
    val question = state.question
    PracticeScaffold(stringResource(R.string.practice_needs_title), R.drawable.location_city,
        R.drawable.npc_luna_explaining, stringResource(question.situation.promptRes),
        state.current, state.questions.size, state.correctAnswers, state.finished,
        { onAction(PracticeChoiceAction.Restart) }, onBack, deed, DeedRewardPreview.fromPractice(state), onHome = onHome) {
        PracticeBoard {
            GameArtwork(question.item.artwork, null, Modifier.size(148.dp))
            PracticeText(stringResource(question.item.titleRes), bold = true)
            PracticeChoices(2) { choice, modifier ->
                PracticeOption(
                    title = stringResource(if (choice == 0) R.string.practice_road else R.string.practice_joy),
                    subtitle = stringResource(if (choice == 0) R.string.practice_road_hint else R.string.practice_joy_hint),
                    modifier = modifier, enabled = deed?.canPlay != false && state.selectedChoice == null,
                    picked = state.selectedChoice == choice,
                    correct = state.selectedChoice?.let { choice == question.correctChoice },
                    onClick = { onAction(PracticeChoiceAction.Answer(choice, state.current)) },
                )
            }
        }
        PracticeFeedback(state, stringResource(question.situation.explanationRes),
            stringResource(R.string.practice_needs_hint), onAction)
    }
}
