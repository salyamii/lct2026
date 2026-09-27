package ru.nksk.lctapp.data.game.content

import ru.nksk.lctapp.domain.engine.GameCatalog
import ru.nksk.lctapp.domain.minigame.DeedGameKind

/** Practical work uses existing boards. Reading, dialogue and travel keep their authored choices. */
internal fun GameCatalog.withStoryActionGames(): GameCatalog {
    val actions = mapOf(
        "G1.03:continue" to DeedGameKind.PRECISION, // Clean the plate.
        "N1.WHEEL:continue" to DeedGameKind.PRECISION,
        "G2.03:repair" to DeedGameKind.PRECISION, // The detour remains a separate route.
        "N3.ACCESS:continue" to DeedGameKind.MEMORY,
        "N3.WORKBENCH:continue" to DeedGameKind.PRECISION,
        "G3.06:continue" to DeedGameKind.MEMORY,
        "G3.08:continue" to DeedGameKind.PRECISION,
        "G3.09:continue" to DeedGameKind.PRECISION,
        "G3.10:continue" to DeedGameKind.MEMORY,
        "G3.11:continue" to DeedGameKind.PRECISION,
        "G4.02:continue" to DeedGameKind.MEMORY,
        "G4.08:continue" to DeedGameKind.MEMORY,
        "G5.08:continue" to DeedGameKind.PRECISION,
    )
    val repairs = mapOf(
        "figma-2297-2-v2:work" to DeedGameKind.PRECISION,
        "figma-2308-2-v2:work" to DeedGameKind.MEMORY,
        "figma-2313-2-v2:work" to DeedGameKind.PRECISION,
        "figma-2313-2-v1:clean" to DeedGameKind.PRECISION,
        "figma-2320-50-v2:work" to DeedGameKind.PRECISION,
        "figma-2320-146-v2:work" to DeedGameKind.PRECISION,
        "figma-2320-194-v2:work" to DeedGameKind.PRECISION,
        "figma-2320-290-v2:work" to DeedGameKind.MEMORY,
        "figma-2320-338-v2:work" to DeedGameKind.PRECISION,
        "figma-2326-64-v2:work" to DeedGameKind.MEMORY,
        "figma-2326-112-v2:work" to DeedGameKind.PRECISION,
        "$PLATE_CLEANING:work" to DeedGameKind.PRECISION,
        "figma-2326-256-v2:work" to DeedGameKind.PRECISION,
        "figma-2326-352-v2:work" to DeedGameKind.PRECISION,
        "figma-2326-448-v2:work" to DeedGameKind.PRECISION,
    )
    return copy(policies = policies.mapValues { (id, policy) ->
        val prefix = "${id.removePrefix("campaign-choice-v1:")}:"
        val games = actions.filterKeys { it.startsWith(prefix) }
            .mapKeys { (choice, _) -> "campaign-choice-v1:$choice" } + repairs.filterKeys { it.startsWith("$id:") }
        if (games.isEmpty()) policy else policy.copy(choiceGameKinds = games)
    })
}
