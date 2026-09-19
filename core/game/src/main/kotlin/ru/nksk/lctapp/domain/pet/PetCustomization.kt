package ru.nksk.lctapp.domain.pet

/** Temperament is a saved choice, not an automatic gameplay effect. */
enum class PetTemperament { Curious, Confident, Joyful }
enum class PetFur { Copper, Sand, Russet }
enum class PetAge { Cub }

data class PetCustomization(
    val name: String = "Рыжик",
    val temperament: PetTemperament = PetTemperament.Curious,
    val fur: PetFur = PetFur.Copper,
    val age: PetAge = PetAge.Cub,
)
