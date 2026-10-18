package ru.nksk.lctapp.data.game.content

import ru.nksk.lctapp.domain.content.*
import ru.nksk.lctapp.domain.engine.*
import ru.nksk.lctapp.domain.minigame.DeedGameKind

private data class ChapterGameRevision(
    val oldId: String,
    val newId: String,
    val choiceKey: String,
    val kind: DeedGameKind,
    val instruction: String,
    val deed: Boolean = false,
)

private val chapterGameRevisions = listOf(
    ChapterGameRevision("figma-2163-43-v1:balance-v2", "figma-2163-43-v1:balance-v2:game-v3",
        "complete", DeedGameKind.TELESCOPE, "Соберём телескоп и наведём резкость.", deed = true),
    ChapterGameRevision("campaign-choice-v1:G1.02", "campaign-choice-v2:G1.02",
        "continue", DeedGameKind.MARK, "Сотри пыль и зарисуй знак."),
    ChapterGameRevision("campaign-choice-v1:N1.WHEEL", "campaign-choice-v2:N1.WHEEL",
        "continue", DeedGameKind.WHEEL, "Подгони части колеса и закрепи их."),
    ChapterGameRevision("figma-2297-2-v2", "figma-2297-2-v3",
        "work", DeedGameKind.STRAP, "Совмести края лямки и зашей разрыв."),
    ChapterGameRevision("campaign-choice-v1:G1.11", "campaign-choice-v2:G1.11",
        "continue", DeedGameKind.SIGNAL, "Зажги три огня в порядке из журнала."),
    ChapterGameRevision("campaign-choice-v1:G1.06", "campaign-choice-v2:G1.06",
        "continue", DeedGameKind.ROUTE_MAP, "Дорисуй путь от причала через мост к башне."),
)

/** Future offers use the new scenes; already offered boards keep their original identities. */
internal fun GameCatalog.withChapterOneGameVersions(): GameCatalog {
    val revisions = chapterGameRevisions.associateBy { it.oldId }
    val compiled = withEventSpecs(chapterGameRevisions.map { chapterGameSpec(it) })
    val campaign = checkNotNull(storyCampaign)
    val aliases = campaign.completionAliases.toMutableMap()
    chapterGameRevisions.filter { policies.getValue(it.oldId).storyActId != null }.forEach { revision ->
        aliases[revision.newId] = aliases[revision.newId].orEmpty() + aliases[revision.oldId].orEmpty() +
            content.choices.filter { it.eventId == revision.oldId }.map { it.id }
        aliases[revision.oldId] = aliases[revision.oldId].orEmpty() +
            compiled.content.choices.filter { it.eventId == revision.newId }.map { it.id }
    }
    fun current(id: String) = revisions[id]?.newId ?: id
    return compiled.copy(
        content = compiled.content.copy(eventItemEffects = content.eventItemEffects +
            content.eventItemEffects.filter { it.eventId in revisions }.map { effect ->
                val id = current(effect.eventId)
                effect.copy(id = "$id:event-effect:${effect.id}", eventId = id)
            }),
        deedPool = deedPool.map(::current),
        dailyEventPool = dailyEventPool.map(::current),
        oneTimeEventIds = oneTimeEventIds.map(::current).toSet(),
        storyCampaign = campaign.copy(
            acts = campaign.acts.map { it.copy(eventIds = it.eventIds.map(::current), finaleId = current(it.finaleId)) },
            deedHints = campaign.deedHints.map { it.copy(eventId = current(it.eventId)) },
            completionAliases = aliases,
        ),
    )
}

/** Only the selected physical action changes board. All original effects remain authored data. */
private fun GameCatalog.chapterGameSpec(revision: ChapterGameRevision): EventSpec {
    val event = content.events.single { it.id == revision.oldId }
    val choices = content.choices.filter { it.eventId == event.id }.sortedBy { it.position }
    val policy = policies.getValue(event.id)
    val card = cards.getValue(event.id)
    fun key(id: String) = id.removePrefix("${event.id}:").also { require(it != id) }
    fun id(oldId: String) = "${revision.newId}:${key(oldId)}"
    require(choices.any { key(it.id) == revision.choiceKey })
    return EventSpec(
        definition = event.copy(id = revision.newId),
        choices = choices.map { choice -> EventChoiceSpec(
            key = key(choice.id), text = choice.text, moneyDelta = choice.moneyDelta,
            budgetSection = choice.budgetSection, petStateAfter = choice.petStateAfter, goalImpact = choice.goalImpact,
            energyCost = policy.choiceEnergyCosts[choice.id],
            gameKind = if (!revision.deed && key(choice.id) == revision.choiceKey) revision.kind
                else policy.choiceGameKinds[choice.id],
            facts = policy.factsByChoiceId[choice.id], feedsPet = choice.id in policy.feedsPetChoiceIds,
            disabled = choice.id in policy.disabledChoiceIds, recap = card.summaryByChoiceId[choice.id],
            destination = policy.choiceDestinations[choice.id],
            energyRestore = policy.choiceEnergyRestores[choice.id] ?: 0,
            itemEffects = content.choiceItemEffects.filter { it.choiceId == choice.id }.sortedBy { it.position }.map {
                ChoiceItemSpec("${revision.newId}:choice-effect:${it.id}", it.itemId, it.operation)
            },
        ) },
        policy = policy.copy(
            deedGameKind = if (revision.deed) revision.kind else policy.deedGameKind,
            choiceEnergyCosts = emptyMap(), choiceGameKinds = emptyMap(), factsByChoiceId = emptyMap(),
            feedsPetChoiceIds = emptySet(), disabledChoiceIds = emptySet(), choiceDestinations = emptyMap(),
            choiceEnergyRestores = emptyMap(),
            scheduling = policy.scheduling.copy(family = policy.scheduling.family ?: event.id,
                previousEventIds = policy.scheduling.previousEventIds + event.id),
        ),
        card = card.copy(summaryByChoiceId = emptyMap(), presentation = card.presentation.copy(
            actionLabels = card.presentation.actionLabels.mapKeys { id(it.key) },
            outcomeLabels = card.presentation.outcomeLabels.mapKeys { id(it.key) },
            media = card.presentation.media.copy(
                actionAudio = card.presentation.media.actionAudio.mapKeys { id(it.key) },
                game = EventGameMedia(revision.instruction, objectArtworkKey = card.presentation.media.game?.objectArtworkKey),
            ),
        )),
    )
}
