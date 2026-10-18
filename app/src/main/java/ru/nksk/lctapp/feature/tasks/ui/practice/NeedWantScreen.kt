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
import ru.nksk.lctapp.domain.minigame.PracticeItem

@Composable
fun NeedWantScreen(
    state: PracticeChoiceState<NeedWantQuestion>,
    onAction: (PracticeChoiceAction) -> Unit,
    onBack: () -> Unit,
    deed: DeedGamePresentation? = null,
) {
    val question = state.question
    PracticeScaffold(stringResource(R.string.practice_needs_title), R.drawable.location_city,
        R.drawable.npc_luna_explaining, stringResource(R.string.practice_needs_prompt),
        state.current, state.questions.size, state.correctAnswers, state.finished,
        { onAction(PracticeChoiceAction.Restart) }, onBack, deed, DeedRewardPreview.fromPractice(state)) {
        PracticeBoard {
            GameArtwork(question.item.artwork, null, Modifier.size(148.dp))
            PracticeText(stringResource(question.item.titleRes), bold = true)
            val context = when (question.item) {
                PracticeItem.BUN -> R.string.practice_bun_context
                PracticeItem.KEYCHAIN -> R.string.practice_keychain_context
                PracticeItem.CAP -> R.string.practice_cap_context
                else -> null
            }
            if (context != null) PracticeText(stringResource(context))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (choice in 0..1) PracticeOption(
                    title = stringResource(if (choice == 0) R.string.practice_road else R.string.practice_joy),
                    subtitle = stringResource(if (choice == 0) R.string.practice_road_hint else R.string.practice_joy_hint),
                    modifier = Modifier.weight(1f), enabled = deed?.canPlay != false && state.selectedChoice == null,
                    picked = state.selectedChoice == choice,
                    correct = state.selectedChoice?.let { choice == question.correctChoice },
                    onClick = { onAction(PracticeChoiceAction.Answer(choice, state.current)) },
                )
            }
        }
        PracticeFeedback(state, stringResource(when (question.item) {
            PracticeItem.MAP -> R.string.practice_need_map
            PracticeItem.WATER -> R.string.practice_need_water
            PracticeItem.BUN -> R.string.practice_need_bun
            PracticeItem.LANTERN -> R.string.practice_need_lantern
            PracticeItem.RINGS -> R.string.practice_want_rings
            PracticeItem.TOY_BOAT -> R.string.practice_want_boat
            PracticeItem.KEYCHAIN -> R.string.practice_want_keychain
            PracticeItem.CAP -> R.string.practice_want_cap
            else -> error("No need/want copy for ${question.item}")
        }), stringResource(R.string.practice_needs_hint), onAction)
    }
}
