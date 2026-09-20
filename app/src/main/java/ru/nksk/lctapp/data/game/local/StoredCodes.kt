package ru.nksk.lctapp.data.game.local

import ru.nksk.lctapp.domain.content.EventType
import ru.nksk.lctapp.domain.content.GoalImpact
import ru.nksk.lctapp.domain.content.ItemOperation
import ru.nksk.lctapp.domain.content.ItemCategory
import ru.nksk.lctapp.domain.economy.BudgetSection
import ru.nksk.lctapp.domain.pet.PetVisualState
import ru.nksk.lctapp.domain.pet.PetFur
import ru.nksk.lctapp.domain.pet.PetTemperament
import ru.nksk.lctapp.domain.pet.PetAge
import ru.nksk.lctapp.domain.pet.PetColor

/** Persisted strings are explicit contracts, independent of enum names and ordinal order. */
internal class StoredCode<T : Any>(private val values: Map<T, String>) {
    private val byCode = values.entries.associate { (value, code) -> code to value }
    fun encode(value: T): String = requireNotNull(values[value]) { "Unsupported value: $value" }
    fun decode(code: String): T = requireNotNull(byCode[code]) { "Unknown stored code: $code" }
}

internal object StoredCodes {
    val locationLighting = StoredCode(mapOf(
        ru.nksk.lctapp.domain.location.LocationLighting.DAY to "DAY",
        ru.nksk.lctapp.domain.location.LocationLighting.EVENING to "EVENING",
    ))
    val petFur = StoredCode(mapOf(
        PetFur.Copper to "Copper", PetFur.Sand to "Sand", PetFur.Russet to "Russet",
    ))
    val petTemperament = StoredCode(mapOf(
        PetTemperament.Curious to "Curious", PetTemperament.Confident to "Confident",
        PetTemperament.Joyful to "Joyful",
    ))
    val petColor = StoredCode(mapOf(
        PetColor.COPPER to "COPPER", PetColor.SAND to "SAND", PetColor.DARK_RUSSET to "DARK_RUSSET",
    ))
    val petAge = StoredCode(mapOf(
        PetAge.CUB to "CUB", PetAge.TEEN to "TEEN", PetAge.ADULT to "ADULT", PetAge.SENIOR to "SENIOR",
    ))
    val itemCategory = StoredCode(mapOf(
        ItemCategory.STORY to "STORY", ItemCategory.ACCESSORY to "ACCESSORY",
    ))
    val visual = StoredCode(mapOf(
        PetVisualState.NORMAL to "NORMAL", PetVisualState.HAPPY to "HAPPY",
        PetVisualState.UPSET to "UPSET", PetVisualState.THINKING to "THINKING",
        PetVisualState.WORRIED to "WORRIED", PetVisualState.TIRED to "TIRED",
        PetVisualState.HUNGRY to "HUNGRY", PetVisualState.NEEDS_HELP to "NEEDS_HELP",
    ))
    val event = StoredCode(mapOf(
        EventType.STATE to "STATE", EventType.RANDOM to "RANDOM", EventType.WANT to "WANT",
        EventType.EARNING to "EARNING", EventType.STORY to "STORY",
    ))
    val impact = StoredCode(mapOf(GoalImpact.BAD to "BAD", GoalImpact.GOOD to "GOOD", GoalImpact.NEUTRAL to "NEUTRAL"))
    val operation = StoredCode(mapOf(ItemOperation.ADD to "ADD", ItemOperation.REMOVE to "REMOVE"))
    val budget = StoredCode(mapOf(
        BudgetSection.NEEDS to "NEEDS", BudgetSection.WANTS to "WANTS",
        BudgetSection.SAVINGS to "SAVINGS", BudgetSection.RESERVE to "RESERVE",
    ))
}
