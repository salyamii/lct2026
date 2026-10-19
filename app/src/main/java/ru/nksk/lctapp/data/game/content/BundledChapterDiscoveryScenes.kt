package ru.nksk.lctapp.data.game.content

import ru.nksk.lctapp.domain.content.ChoiceItemSpec
import ru.nksk.lctapp.domain.content.EventChoiceSpec
import ru.nksk.lctapp.domain.content.EventSpec
import ru.nksk.lctapp.domain.engine.EventGameMedia
import ru.nksk.lctapp.domain.engine.GameCatalog
import ru.nksk.lctapp.domain.minigame.DeedGameKind

internal const val CHAPTER_INTRO_EVENT = "campaign-choice-v2:G1.01"
internal const val LORE_PLATE_GAME_EVENT = "campaign-choice-v3:G1.03"
internal const val CURRENT_CHAPTER_FINALE_EVENT = "campaign-choice-v3:G1.12"

private data class DiscoveryRevision(
    val previous: String,
    val current: String,
    val games: Map<String, DeedGameKind>,
    val instruction: String,
    val earlierVersions: Set<String> = emptySet(),
)

private val discoveryRevisions = listOf(
    DiscoveryRevision("campaign-choice-v1:G1.01", CHAPTER_INTRO_EVENT,
        mapOf("continue" to DeedGameKind.OBSERVATORY_OPENING), "Открой купол и подготовь телескоп."),
    DiscoveryRevision(LORE_PLATE_CLEANING, LORE_PLATE_GAME_EVENT,
        mapOf("pay" to DeedGameKind.PLATE_SOLUTION, "continue" to DeedGameKind.PLATE_HAND),
        "Сотри налёт с пластины.", setOf(LEGACY_LORE_PLATE_CLEANING)),
    DiscoveryRevision("campaign-choice-v1:G1.05", "campaign-choice-v2:G1.05",
        mapOf("continue" to DeedGameKind.LIGHT_INSCRIPTION), "Освети надпись фонарём."),
    DiscoveryRevision("campaign-choice-v1:G1.07", "campaign-choice-v2:G1.07",
        mapOf("continue" to DeedGameKind.SERVICE_EMBLEM), "Переверни эмблему и сравни знаки."),
    DiscoveryRevision("campaign-choice-v1:G1.09", "campaign-choice-v2:G1.09",
        mapOf("continue" to DeedGameKind.SIGNAL_POST), "Направь линзы на купол."),
    DiscoveryRevision(ARRIVAL_CHAPTER_FINALE_EVENT, CURRENT_CHAPTER_FINALE_EVENT,
        mapOf("continue" to DeedGameKind.CHAPTER_FINALE), "Собери находки и соедини маршруты.",
        setOf("campaign-choice-v1:G1.12")),
)

/** New offers gain tactile scenes; offered historical cards and their costs remain intact. */
internal fun GameCatalog.withChapterDiscoveryScenes(): GameCatalog {
    val revisions = discoveryRevisions.associateBy { it.previous }
    val campaign = checkNotNull(storyCampaign)
    val compiled = withEventSpecs(discoveryRevisions.map { discoverySpec(it) })
    val aliases = campaign.completionAliases.toMutableMap()
    discoveryRevisions.forEach { revision ->
        val versions = revision.earlierVersions + revision.previous + revision.current
        // Aliases are one hop. Every version must recognise all the others directly.
        val choices = compiled.content.choices.filter { it.eventId in versions }.map { it.id }.toSet()
        val inherited = versions.flatMap { campaign.completionAliases[it].orEmpty() }.toSet()
        versions.forEach { version ->
            aliases[version] = aliases[version].orEmpty() + inherited + choices
        }
    }
    fun current(id: String) = revisions[id]?.current ?: id
    return compiled.copy(
        content = compiled.content.copy(eventItemEffects = compiled.content.eventItemEffects +
            content.eventItemEffects.filter { it.eventId in revisions }.map { effect ->
                val id = current(effect.eventId)
                effect.copy(id = "$id:event-effect:${effect.id}", eventId = id)
            }),
        introductionId = current(introductionId),
        goals = goals.map { it.copy(introductionEventId = current(it.introductionEventId)) },
        dailyEventPool = dailyEventPool.map(::current),
        oneTimeEventIds = oneTimeEventIds.map(::current).toSet(),
        storyCampaign = campaign.copy(
            acts = campaign.acts.map { act -> act.copy(
                eventIds = act.eventIds.map(::current), finaleId = current(act.finaleId)) },
            completionAliases = aliases,
        ),
    )
}

private fun GameCatalog.discoverySpec(revision: DiscoveryRevision): EventSpec {
    val event = content.events.single { it.id == revision.previous }
    val policy = policies.getValue(event.id)
    val card = cards.getValue(event.id)
    val choices = content.choices.filter { it.eventId == event.id }.sortedBy { it.position }
    fun key(id: String) = id.removePrefix("${event.id}:").also { require(it != id) }
    fun currentChoice(id: String) = "${revision.current}:${key(id)}"
    require(choices.map { key(it.id) }.containsAll(revision.games.keys))
    return EventSpec(
        definition = event.copy(id = revision.current),
        choices = choices.map { choice -> EventChoiceSpec(
            key = key(choice.id), text = choice.text, moneyDelta = choice.moneyDelta,
            budgetSection = choice.budgetSection, petStateAfter = choice.petStateAfter,
            goalImpact = choice.goalImpact, energyCost = policy.choiceEnergyCosts[choice.id],
            energyRestore = policy.choiceEnergyRestores[choice.id] ?: 0,
            gameKind = revision.games[key(choice.id)] ?: policy.choiceGameKinds[choice.id],
            facts = policy.factsByChoiceId[choice.id], feedsPet = choice.id in policy.feedsPetChoiceIds,
            disabled = choice.id in policy.disabledChoiceIds, destination = policy.choiceDestinations[choice.id],
            recap = card.summaryByChoiceId[choice.id],
            itemEffects = content.choiceItemEffects.filter { it.choiceId == choice.id }.sortedBy { it.position }.map {
                ChoiceItemSpec("${revision.current}:choice-effect:${it.id}", it.itemId, it.operation)
            },
        ) },
        policy = policy.copy(
            choiceEnergyCosts = emptyMap(), choiceEnergyRestores = emptyMap(), choiceGameKinds = emptyMap(),
            factsByChoiceId = emptyMap(), feedsPetChoiceIds = emptySet(), disabledChoiceIds = emptySet(),
            choiceDestinations = emptyMap(),
            scheduling = policy.scheduling.copy(family = policy.scheduling.family ?: event.id,
                previousEventIds = policy.scheduling.previousEventIds + event.id + revision.earlierVersions),
        ),
        card = card.copy(summaryByChoiceId = emptyMap(), presentation = card.presentation.copy(
            actionLabels = card.presentation.actionLabels.mapKeys { currentChoice(it.key) },
            outcomeLabels = card.presentation.outcomeLabels.mapKeys { currentChoice(it.key) },
            media = card.presentation.media.copy(
                actionAudio = card.presentation.media.actionAudio.mapKeys { currentChoice(it.key) },
                game = EventGameMedia(revision.instruction,
                    objectArtworkKey = card.presentation.media.game?.objectArtworkKey),
            ),
        )),
    )
}
