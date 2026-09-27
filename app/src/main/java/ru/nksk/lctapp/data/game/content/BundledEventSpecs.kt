package ru.nksk.lctapp.data.game.content

import ru.nksk.lctapp.domain.content.EventSpec
import ru.nksk.lctapp.domain.engine.GameCatalog

/** Append complete immutable definitions; the compiled lists retain authored order. */
internal fun GameCatalog.withEventSpecs(specs: List<EventSpec>): GameCatalog {
    val compiled = specs.map(EventSpec::compile)
    val ids = compiled.map { it.definition.id }
    require(ids.distinct().size == ids.size && content.events.none { it.id in ids }) { "Event definitions cannot be replaced" }
    return copy(content = content.copy(events = content.events + compiled.map { it.definition },
        choices = content.choices + compiled.flatMap { it.choices },
        choiceItemEffects = content.choiceItemEffects + compiled.flatMap { it.choiceItemEffects }),
        policies = policies + compiled.associate { it.definition.id to it.policy },
        cards = cards + compiled.associate { it.definition.id to it.card })
}
