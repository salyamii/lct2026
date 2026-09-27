package ru.nksk.lctapp.core.ui.game

import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.app.createInitialGameState
import ru.nksk.lctapp.domain.content.EventChoiceDefinition
import ru.nksk.lctapp.domain.content.GoalImpact
import ru.nksk.lctapp.domain.content.StoryContent
import ru.nksk.lctapp.domain.economy.BudgetPlan
import ru.nksk.lctapp.domain.economy.EconomyState
import ru.nksk.lctapp.domain.engine.*
import ru.nksk.lctapp.domain.minigame.DeedGameScore
import ru.nksk.lctapp.domain.minigame.TargetStopState

class PaymentAudioCuesTest {
    @Test fun successfulChoiceUsesItsAuthoredSoundAndVoiceForAllChoiceCommands() {
        val score = checkNotNull(DeedGameScore.fromPrecision(TargetStopState.create().copy(round = 5, hits = 5, lastHit = true)))
        val commands = listOf(EngineCommand.Choose("occurrence", "paid"),
            EngineCommand.CompleteEvent("occurrence", "paid"),
            EngineCommand.CompleteStoryGame("occurrence", "paid", score))
        for (command in commands) {
            assertEquals(listOf("sound.custom-payment", "voice.thanks"), paymentAudioCues(applied(command, 95, 20), catalog()))
        }
    }

    @Test fun paidMealGoalItemAndEventStartHaveOnePaymentFallback() {
        val commands = listOf(EngineCommand.Feed("basic"), EngineCommand.BuyGoalItem("goal", "item"), EngineCommand.OpenNextEvent)
        for (command in commands) {
            assertEquals(listOf("sound.payment"), paymentAudioCues(applied(command, 95, 20), catalog()))
        }
        assertEquals(listOf("sound.payment"), paymentAudioCues(applied(EngineCommand.BuyGoalItem("goal", "item"), 100, 8), catalog()))
    }

    @Test fun transfersFreeMealAndUnchangedBalanceNeverPlayAPayment() {
        for (command in listOf(EngineCommand.DepositSavings(10), EngineCommand.WithdrawSavings(10, true))) {
            assertTrue(paymentAudioCues(applied(command, 90, 30), catalog()).isEmpty())
        }
        assertTrue(paymentAudioCues(applied(EngineCommand.Feed("free"), 100, 20), catalog()).isEmpty())
        assertTrue(paymentAudioCues(applied(EngineCommand.CompleteEvent("occurrence", "paid"), 100, 20), catalog()).isEmpty())
        assertTrue(paymentAudioCues(applied(EngineCommand.CompleteEvent("occurrence", "paid"), 105, 20), catalog()).isEmpty())
    }

    @Test fun missingAuthoredSoundKeepsThePaymentFallbackAndOptionalVoice() {
        assertEquals(listOf("sound.payment", "voice.thanks"), paymentAudioCues(
            applied(EngineCommand.CompleteEvent("occurrence", "paid"), 95, 20), catalog(sound = null)))
    }

    private fun applied(command: EngineCommand, available: Long, savings: Long): AppliedGameCommand {
        val before = createInitialGameState().copy(economy = EconomyState(BudgetPlan(0, 0, 0, 100), savingsBalance = 20))
        val after = before.copy(economy = EconomyState(BudgetPlan(0, 0, 0, available), savingsBalance = savings))
        return AppliedGameCommand(EngineRequest("receipt", null, command), before, after)
    }

    private fun catalog(sound: String? = "sound.custom-payment") = GameCatalog(
        content = StoryContent(choices = listOf(EventChoiceDefinition("paid", "event", 0, "Pay", -5, null, null, GoalImpact.NEUTRAL))),
        policies = emptyMap(),
        cards = mapOf("event" to EventCardCopy("", "", "", null, "", "", "", null,
            presentation = EventPresentation(media = EventMedia(actionAudio = mapOf(
                "paid" to EventActionAudio(sound, "voice.thanks")))))),
        rules = EngineRules("test", 5, 50, 1), meals = emptyList(), storyDayId = "day", introductionId = "unused", deedPool = emptyList(),
    )
}
