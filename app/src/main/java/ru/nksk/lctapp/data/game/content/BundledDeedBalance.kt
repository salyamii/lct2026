package ru.nksk.lctapp.data.game.content

import ru.nksk.lctapp.domain.engine.*

/** New identities preserve every already installed offer, reward and historical receipt. */
internal fun GameCatalog.withRebalancedDeeds(): GameCatalog {
    val aliases = deedPool.associateWith { "$it:balance-v2" }
    fun reward(id: String, old: Long): Long = when (policies.getValue(id).energyCost) {
        1 -> if (old >= 8) 5 else 4
        2 -> old.coerceIn(6, 8)
        3 -> if (old >= 10) 12 else 10
        else -> error("Only real deeds may have a reward schedule")
    }
    val oldChoices = content.choices.filter { it.eventId in aliases }
    val choiceAliases = oldChoices.associate { it.id to "${aliases.getValue(it.eventId)}:complete" }
    val newChoices = oldChoices.map { choice -> choice.copy(id = choiceAliases.getValue(choice.id),
        eventId = aliases.getValue(choice.eventId), moneyDelta = reward(choice.eventId, choice.moneyDelta),
        text = "Выполнить дело") }
    val newPolicies = aliases.map { (oldId, newId) ->
        val policy = policies.getValue(oldId)
        newId to policy.copy(
            choiceEnergyCosts = policy.choiceEnergyCosts.mapKeys { (id, _) -> choiceAliases.getValue(id) },
            factsByChoiceId = policy.factsByChoiceId.mapKeys { (id, _) -> choiceAliases.getValue(id) },
            scheduling = policy.scheduling.copy(cooldownDays = 2, family = oldId, previousEventIds = setOf(oldId)),
        )
    }.toMap()
    return copy(
        content = content.copy(
            events = content.events + content.events.filter { it.id in aliases }.map { it.copy(id = aliases.getValue(it.id)) },
            choices = content.choices + newChoices,
        ),
        policies = policies + newPolicies,
        cards = cards + aliases.map { (oldId, newId) ->
            val old = cards.getValue(oldId)
            val maximum = newChoices.single { it.eventId == newId }.moneyDelta
            newId to old.copy(impact = "Награда: до $maximum монет", summaryByChoiceId =
                old.summaryByChoiceId.mapKeys { (id, _) -> choiceAliases.getValue(id) })
        }.toMap(),
        deedPool = deedPool.map(aliases::getValue),
        storyCampaign = storyCampaign?.let { campaign -> campaign.copy(deedHints = campaign.deedHints.map { hint ->
            hint.copy(eventId = aliases[hint.eventId] ?: hint.eventId)
        }) },
    )
}
