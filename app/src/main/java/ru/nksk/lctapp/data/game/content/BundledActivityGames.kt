package ru.nksk.lctapp.data.game.content

import ru.nksk.lctapp.domain.content.*
import ru.nksk.lctapp.domain.engine.*
import ru.nksk.lctapp.domain.minigame.DeedGameKind

/** A different board is a new immutable event, never a replacement of an offered or active game. */
private data class ActivityRevision(val oldId: String, val newId: String, val kind: DeedGameKind,
    val context: String, val body: String? = null)

private fun deedRevision(node: String, kind: DeedGameKind, context: String, body: String? = null) =
    ActivityRevision("figma-$node-v1:balance-v2", "figma-$node-v1:balance-v2:game-v3", kind, context, body)

private fun storyRevision(source: String, kind: DeedGameKind, context: String, body: String? = null) =
    ActivityRevision("campaign-choice-v1:$source", "campaign-choice-v2:$source", kind, context, body)

private val activityRevisions = listOf(
    deedRevision("2238-120", DeedGameKind.PRECISION, "Протрём линзы аккуратными движениями."),
    deedRevision("2270-54", DeedGameKind.PIPES, "Найдём концы каждого каната и проложим их без пересечений."),
    deedRevision("2270-106", DeedGameKind.DIFFERENCES, "Сверим полки с образцом и найдём перепутанные посылки.",
        "На складе перепутались посылки. Смотритель показывает, как они должны стоять на полках. Сравни два ряда и найди отличия."),
    deedRevision("2289-2", DeedGameKind.PRECISION, "Донесём поднос до прилавка, удерживая его ровно."),
    deedRevision("2289-110", DeedGameKind.DIFFERENCES, "Сверим витрину с образцом Медведя.",
        "На витрине перепутали товар. Медведь показывает образец расстановки. Сравни полки и найди, что стоит не на своём месте."),
    deedRevision("2289-164", DeedGameKind.MEMORY, "Поможем Медведю собрать заказы: найдём пары одинаковых карточек.",
        "Заказов так много, что Медведь приготовил карточки с картинками. У каждого заказа есть пара. Помоги найти одинаковые карточки, чтобы ничего не забыть."),
    deedRevision("2289-218", DeedGameKind.PRECISION, "Взвесим свёртки: поймаем момент, когда весы в равновесии."),
    storyRevision("G2.03", DeedGameKind.PIPES, "Соединим крепёжные канаты, чтобы укрепить переход вместе с Бобром."),
    storyRevision("G3.06", DeedGameKind.DIFFERENCES, "Сравним старую полку с образцом и найдём предметы, которые переставили."),
    storyRevision("G3.09", DeedGameKind.STACKING, "Поставим мешающие ящики ровной стопкой, чтобы добраться до нужного ящика."),
    storyRevision("G3.11", DeedGameKind.SEQUENCE, "Тико проверяет сигналы тестера. Запомним и повторим порядок вспышек."),
    storyRevision("G5.08", DeedGameKind.LIGHTS,
        "Перед перезапуском реле нужно погасить все контрольные огни. Тико показывает, как переключатели влияют на соседние лампы."),
    ActivityRevision("figma-2326-112-v2", "figma-2326-112-v3", DeedGameKind.PIPES,
        "Скрепим страницы журнала: соединим концы нитей одного цвета без пересечений."),
    ActivityRevision("figma-2326-352-v2", "figma-2326-352-v3", DeedGameKind.PRECISION,
        "Подгоним заевшую крышку ящика аккуратными движениями."),
)

/** Current presentation only. The matching price board is not a crate-counting exercise. */
private val unchangedDeedContexts = mapOf(
    "figma-2163-2-v1:balance-v2" to "Поможем Смотрителю сравнить цены приборов перед покупкой.",
    "figma-2163-43-v1:balance-v2" to "Наведём малый телескоп точно на цель.",
    "figma-2270-2-v1:balance-v2" to "Поставим ящики ровной стопкой, чтобы груз не упал.",
    "figma-2270-158-v1:balance-v2" to "Закрепим полотно по краям, чтобы дождь не намочил груз.",
    "figma-2270-210-v1:balance-v2" to "Сверим картинки на ящике с карточками отправителя: найдём одинаковые пары.",
    "figma-2289-56-v1:balance-v2" to "Перевяжем покупки: соединим концы бечёвки одного цвета.",
)

internal fun GameCatalog.withActivityGameVersions(): GameCatalog {
    val revisions = activityRevisions.associateBy { it.oldId }
    val compiled = withEventSpecs(activityRevisions.map { activitySpec(it) })
    val campaign = checkNotNull(storyCampaign)
    val completionAliases = campaign.completionAliases.toMutableMap()
    for (revision in activityRevisions.filter { policies.getValue(it.oldId).storyActId != null }) {
        completionAliases[revision.newId] = completionAliases[revision.newId].orEmpty() +
            completionAliases[revision.oldId].orEmpty() + content.choices.filter { it.eventId == revision.oldId }.map { it.id }
        completionAliases[revision.oldId] = completionAliases[revision.oldId].orEmpty() +
            compiled.content.choices.filter { it.eventId == revision.newId }.map { it.id }
    }
    fun current(id: String) = revisions[id]?.newId ?: id
    val currentCards = compiled.cards.toMutableMap()
    unchangedDeedContexts.forEach { (id, context) ->
        val card = currentCards.getValue(id)
        currentCards[id] = card.copy(presentation = card.presentation.copy(
            body = when (id) {
                "figma-2163-2-v1:balance-v2" -> "Смотритель выбирает приборы для наблюдений. Помоги сравнить ценники и найти, какой предмет стоит дороже."
                "figma-2270-210-v1:balance-v2" -> "Под пылью на ящике нашлась картинка отправителя. Бобёр принёс карточки из своих записей. Найди одинаковые пары, чтобы сверить отметки."
                else -> card.presentation.body
            },
            media = card.presentation.media.copy(game = EventGameMedia(context)),
        ))
    }
    return compiled.copy(
        content = compiled.content.copy(eventItemEffects = content.eventItemEffects +
            content.eventItemEffects.filter { it.eventId in revisions }.map { effect ->
                val id = current(effect.eventId)
                effect.copy(id = "$id:event-effect:${effect.id}", eventId = id)
            }),
        cards = currentCards,
        deedPool = deedPool.map(::current),
        dailyEventPool = dailyEventPool.map(::current),
        oneTimeEventIds = oneTimeEventIds.map(::current).toSet(),
        storyCampaign = campaign.copy(
            acts = campaign.acts.map { it.copy(eventIds = it.eventIds.map(::current), finaleId = current(it.finaleId)) },
            deedHints = campaign.deedHints.map { it.copy(eventId = current(it.eventId)) },
            completionAliases = completionAliases,
        ),
    )
}

/** Compatibility authoring: copy effects verbatim while assigning a new board and identities. */
private fun GameCatalog.activitySpec(revision: ActivityRevision): EventSpec {
    val event = content.events.single { it.id == revision.oldId }
    val choices = content.choices.filter { it.eventId == event.id }.sortedBy { it.position }
    val policy = policies.getValue(event.id)
    val card = cards.getValue(event.id)
    fun key(id: String) = id.removePrefix("${event.id}:").also { require(it != id) }
    fun id(oldChoiceId: String) = "${revision.newId}:${key(oldChoiceId)}"
    return EventSpec(
        definition = event.copy(id = revision.newId),
        choices = choices.map { choice -> EventChoiceSpec(
            key = key(choice.id), text = choice.text, moneyDelta = choice.moneyDelta,
            budgetSection = choice.budgetSection, petStateAfter = choice.petStateAfter, goalImpact = choice.goalImpact,
            energyCost = policy.choiceEnergyCosts[choice.id],
            gameKind = policy.choiceGameKinds[choice.id]?.let { revision.kind },
            facts = policy.factsByChoiceId[choice.id], feedsPet = choice.id in policy.feedsPetChoiceIds,
            disabled = choice.id in policy.disabledChoiceIds, recap = card.summaryByChoiceId[choice.id],
            destination = policy.choiceDestinations[choice.id],
            itemEffects = content.choiceItemEffects.filter { it.choiceId == choice.id }.sortedBy { it.position }.map {
                ChoiceItemSpec("${revision.newId}:choice-effect:${it.id}", it.itemId, it.operation)
            },
        ) },
        policy = policy.copy(
            deedGameKind = policy.deedGameKind?.let { revision.kind },
            choiceEnergyCosts = emptyMap(), choiceGameKinds = emptyMap(), factsByChoiceId = emptyMap(),
            feedsPetChoiceIds = emptySet(), disabledChoiceIds = emptySet(), choiceDestinations = emptyMap(),
            scheduling = policy.scheduling.copy(family = policy.scheduling.family ?: event.id,
                previousEventIds = policy.scheduling.previousEventIds + event.id),
        ),
        card = card.copy(summaryByChoiceId = emptyMap(), presentation = card.presentation.copy(
            body = revision.body ?: card.presentation.body,
            actionLabels = card.presentation.actionLabels.mapKeys { id(it.key) },
            outcomeLabels = card.presentation.outcomeLabels.mapKeys { id(it.key) },
            media = card.presentation.media.copy(
                actionAudio = card.presentation.media.actionAudio.mapKeys { id(it.key) },
                game = EventGameMedia(revision.context, objectArtworkKey = card.presentation.media.game?.objectArtworkKey),
            ),
        )),
    )
}
