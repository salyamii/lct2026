package ru.nksk.lctapp.domain.pet

/** Temperament is a saved choice, not an automatic gameplay effect. */
enum class PetTemperament { Curious, Confident, Joyful }
enum class PetFur { Copper, Sand, Russet }

data class PetCustomization(
    val name: String = "Рыжик",
    val temperament: PetTemperament = PetTemperament.Curious,
    val fur: PetFur = PetFur.Copper,
)

/** Draft values become the single saved pet identity only when onboarding completes. */
fun PetCustomization.toPetState(accessoryId: String): PetState = PetState(
    selectedLookId = accessoryId,
    visualState = PetVisualState.NORMAL,
    name = name.trim(),
    age = PetAge.CUB,
    color = when (fur) {
        PetFur.Copper -> PetColor.COPPER
        PetFur.Sand -> PetColor.SAND
        PetFur.Russet -> PetColor.DARK_RUSSET
    },
    temperament = temperament,
)
