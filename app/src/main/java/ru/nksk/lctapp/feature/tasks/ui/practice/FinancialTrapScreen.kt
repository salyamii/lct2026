package ru.nksk.lctapp.feature.tasks.ui.practice

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.nksk.lctapp.R
import ru.nksk.lctapp.domain.minigame.DeedRewardPreview
import ru.nksk.lctapp.feature.tasks.ui.DeedGamePresentation
import ru.nksk.lctapp.core.ui.components.GameArtwork
import ru.nksk.lctapp.domain.minigame.FinancialTrapQuestion
import ru.nksk.lctapp.domain.minigame.PracticeChoiceAction
import ru.nksk.lctapp.domain.minigame.PracticeChoiceState
import ru.nksk.lctapp.domain.minigame.TrapScenario

@Composable
fun FinancialTrapScreen(
    state: PracticeChoiceState<FinancialTrapQuestion>,
    onAction: (PracticeChoiceAction) -> Unit,
    onBack: () -> Unit,
    deed: DeedGamePresentation? = null,
    onHome: () -> Unit = onBack,
) {
    val question = state.question
    val scenario = question.scenario
    PracticeScaffold(stringResource(R.string.practice_trap_title), R.drawable.location_pier,
        R.drawable.npc_carpenter_explaining, stringResource(when (scenario) {
            TrapScenario.TICKET -> R.string.practice_ticket_prompt
            TrapScenario.BORROWED_LANTERN -> R.string.practice_loan_prompt
            TrapScenario.URGENT_TICKET -> R.string.practice_urgent_prompt
            TrapScenario.DELIVERY_FEE -> R.string.practice_delivery_prompt
        }), state.current, state.questions.size, state.correctAnswers, state.finished,
        { onAction(PracticeChoiceAction.Restart) }, onBack, deed, DeedRewardPreview.fromPractice(state), onHome = onHome) {
        PracticeBoard(background = R.drawable.location_pier) {
            GameArtwork(R.drawable.gear_stargazing_trip, null, Modifier.size(72.dp))
            PracticeChoices(question.options.size) { index, modifier ->
                    val option = question.options[index]
                    val first = index == 0
                    val title = when (scenario) {
                        TrapScenario.TICKET ->
                            if (first) R.string.practice_today else R.string.practice_tomorrow
                        TrapScenario.URGENT_TICKET ->
                            if (first) R.string.practice_ticket_for_today else R.string.practice_ticket_for_tomorrow
                        TrapScenario.BORROWED_LANTERN -> if (first) R.string.practice_borrow else R.string.practice_save
                        TrapScenario.DELIVERY_FEE -> if (first) R.string.practice_shop_a else R.string.practice_shop_b
                    }
                    val details = when (scenario) {
                        TrapScenario.TICKET, TrapScenario.URGENT_TICKET ->
                            stringResource(R.string.practice_ticket_cost, option.price)
                        TrapScenario.BORROWED_LANTERN -> if (first)
                            stringResource(R.string.practice_loan_cost, option.price, option.total)
                        else stringResource(R.string.practice_save_cost, option.price) + "\n" +
                            pluralStringResource(R.plurals.practice_saving_time, option.waitDays, option.waitDays)
                        TrapScenario.DELIVERY_FEE -> stringResource(R.string.practice_delivery_cost,
                            option.price, option.extraCost)
                    }
                    val artwork = when (scenario) {
                        TrapScenario.TICKET, TrapScenario.URGENT_TICKET -> R.drawable.prop_practice_ticket
                        TrapScenario.BORROWED_LANTERN -> if (first) R.drawable.story_letter else R.drawable.prop_practice_lantern
                        TrapScenario.DELIVERY_FEE -> R.drawable.story_supply_bag
                    }
                    PracticeOption(stringResource(title), details, modifier, artwork,
                        enabled = deed?.canPlay != false && state.selectedChoice == null, picked = state.selectedChoice == index,
                        correct = state.selectedChoice?.let { question.correctChoice == index },
                        onClick = { onAction(PracticeChoiceAction.Answer(index, state.current)) })
            }
        }
        PracticeFeedback(state, stringResource(when (scenario) {
            TrapScenario.TICKET -> R.string.practice_ticket_explanation
            TrapScenario.BORROWED_LANTERN -> R.string.practice_loan_explanation
            TrapScenario.URGENT_TICKET -> R.string.practice_urgent_explanation
            TrapScenario.DELIVERY_FEE -> R.string.practice_delivery_explanation
        }), stringResource(R.string.practice_trap_hint), onAction)
    }
}
