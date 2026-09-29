package ru.nksk.lctapp.data.game.content

import ru.nksk.lctapp.domain.content.EventChoiceSpec
import ru.nksk.lctapp.domain.content.EventSpec
import ru.nksk.lctapp.domain.engine.EventGameMedia
import ru.nksk.lctapp.domain.engine.GameCatalog
import ru.nksk.lctapp.domain.minigame.DeedGameKind
import ru.nksk.lctapp.domain.pet.PetVisualState

internal const val LEGACY_RING_TOSS = "figma-2654-98-purchase-v2"
internal const val RING_TOSS = "figma-2654-98-purchase-v3"

/** The old immediate purchase stays readable; unresolved cards adopt the playable activity. */
internal fun GameCatalog.withRingTossGame(): GameCatalog {
    val original = content.events.single { it.id == LEGACY_RING_TOSS }
    val paid = content.choices.single { it.id == "$LEGACY_RING_TOSS:buy" }
    val policy = policies.getValue(LEGACY_RING_TOSS)
    val card = cards.getValue(LEGACY_RING_TOSS)
    val presentation = PurchasePresentation.RING_TOSS.forEvent(RING_TOSS)
    val spec = EventSpec(
        definition = original.copy(id = RING_TOSS,
            description = "На ярмарке предлагают бросить кольца и проверить меткость. После игры питомец порадуется и немного восстановит силы. Денежных призов здесь нет."),
        choices = listOf(
            EventChoiceSpec("buy", "Сыграть · 7", moneyDelta = paid.moneyDelta,
                petStateAfter = PetVisualState.HAPPY, energyCost = 0, gameKind = DeedGameKind.PRECISION,
                energyRestore = 1, recap = "Поиграли в кольцеброс и порадовали питомца"),
            EventChoiceSpec("pass", "Пройти мимо", recap = "Прошли мимо кольцеброса"),
        ),
        policy = policy.copy(scheduling = policy.scheduling.copy(
            previousEventIds = policy.scheduling.previousEventIds + LEGACY_RING_TOSS)),
        card = card.copy(summaryByChoiceId = emptyMap(), presentation = presentation.copy(
            body = "Бросим кольца и проверим меткость? После игры {petName} порадуется и восстановит немного сил. Денежных призов здесь нет.",
            outcomeLabels = mapOf("$RING_TOSS:pass" to "Прошли мимо кольцеброса"),
            media = presentation.media.copy(game = EventGameMedia(
                context = "Бросим пять колец на ярмарке: поймаем подходящий момент для каждого броска.",
                objectArtworkKey = "purchase.ring_toss")),
        )),
    )
    return withEventSpecs(listOf(spec)).copy(
        dailyEventPool = dailyEventPool.map { if (it == LEGACY_RING_TOSS) RING_TOSS else it },
        eventReplacements = eventReplacements + (LEGACY_RING_TOSS to RING_TOSS),
    )
}
