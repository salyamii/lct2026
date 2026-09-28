package ru.nksk.lctapp.domain.content

import ru.nksk.lctapp.domain.economy.BudgetSection
import ru.nksk.lctapp.domain.engine.EventCardCopy
import ru.nksk.lctapp.domain.engine.EventPolicy
import ru.nksk.lctapp.domain.location.GameLocation
import ru.nksk.lctapp.domain.minigame.DeedGameKind
import ru.nksk.lctapp.domain.pet.PetVisualState

/** A choice owns its effects, work, discoveries and recap instead of registering them in ID maps. */
data class EventChoiceSpec(
    val key: String,
    val text: String,
    val moneyDelta: Long = 0,
    val budgetSection: BudgetSection? = null,
    val petStateAfter: PetVisualState? = null,
    val goalImpact: GoalImpact = GoalImpact.NEUTRAL,
    /** Null inherits the event cost; an explicit zero remains an authored override. */
    val energyCost: Int? = null,
    val gameKind: DeedGameKind? = null,
    /** Null and an authored empty set have different historical policy shapes. */
    val facts: Set<String>? = null,
    val feedsPet: Boolean = false,
    val disabled: Boolean = false,
    val recap: String? = null,
    val itemEffects: List<ChoiceItemSpec> = emptyList(),
    /** Actual arrival after this choice succeeds; independent from the card's display scene. */
    val destination: GameLocation? = null,
)

data class ChoiceItemSpec(val id: String, val itemId: String, val operation: ItemOperation)

/** Pure authored source. Compilation changes representation only, never installed identities or rules. */
data class EventSpec(
    val definition: EventDefinition,
    val choices: List<EventChoiceSpec>,
    val policy: EventPolicy,
    val card: EventCardCopy,
) {
    fun compile(): CompiledEventSpec {
        require(definition.id.isNotBlank() && choices.isNotEmpty())
        require(choices.all { it.key.isNotBlank() } && choices.map { it.key }.distinct().size == choices.size)
        require(policy.choiceEnergyCosts.isEmpty() && policy.choiceGameKinds.isEmpty() && policy.factsByChoiceId.isEmpty() &&
            policy.feedsPetChoiceIds.isEmpty() && policy.disabledChoiceIds.isEmpty() && policy.choiceDestinations.isEmpty()) {
            "Choice rules belong in EventChoiceSpec"
        }
        require(card.summaryByChoiceId.isEmpty()) { "Choice recaps belong in EventChoiceSpec" }
        val ids = choices.associateWith { "${definition.id}:${it.key}" }
        val compiledChoices = choices.mapIndexed { index, choice ->
            EventChoiceDefinition(ids.getValue(choice), definition.id, index, choice.text, choice.moneyDelta,
                choice.budgetSection, choice.petStateAfter, choice.goalImpact)
        }
        val effects = choices.flatMap { choice -> choice.itemEffects.mapIndexed { index, item ->
            ChoiceItemEffect(item.id, ids.getValue(choice), index, item.itemId, item.operation)
        } }
        require(effects.map { it.id }.distinct().size == effects.size) { "Duplicate choice item effect identity" }
        return CompiledEventSpec(definition, compiledChoices,
            policy.copy(
                choiceEnergyCosts = choices.mapNotNull { choice -> choice.energyCost?.let { ids.getValue(choice) to it } }.toMap(),
                choiceGameKinds = choices.mapNotNull { choice -> choice.gameKind?.let { ids.getValue(choice) to it } }.toMap(),
                factsByChoiceId = choices.mapNotNull { choice -> choice.facts?.let { ids.getValue(choice) to it } }.toMap(),
                feedsPetChoiceIds = choices.filter { it.feedsPet }.map { ids.getValue(it) }.toSet(),
                disabledChoiceIds = choices.filter { it.disabled }.map { ids.getValue(it) }.toSet(),
                choiceDestinations = choices.mapNotNull { choice -> choice.destination?.let { ids.getValue(choice) to it } }.toMap(),
            ), card.copy(summaryByChoiceId = choices.mapNotNull { choice -> choice.recap?.let { ids.getValue(choice) to it } }.toMap()), effects)
    }
}

data class CompiledEventSpec(
    val definition: EventDefinition,
    val choices: List<EventChoiceDefinition>,
    val policy: EventPolicy,
    val card: EventCardCopy,
    val choiceItemEffects: List<ChoiceItemEffect>,
)
