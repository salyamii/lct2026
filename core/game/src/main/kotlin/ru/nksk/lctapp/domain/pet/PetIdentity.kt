package ru.nksk.lctapp.domain.pet

object PetDefaults {
    const val FOX_NAME = "Рыжик"
}

/** Saved life stage. Advancing it requires an authored rule; elapsed days do not change it. */
enum class PetAge { CUB, TEEN, ADULT, SENIOR }

/** Saved fur palette, independent of age, emotion and the open cosmetic identifier. */
enum class PetColor { COPPER, SAND, DARK_RUSSET }

/** User names are plain, single-line text. No declension or interpretation as a template. */
fun isValidPetName(name: String): Boolean = name.isNotBlank() && name.none {
    it.isISOControl() || it == '\u2028' || it == '\u2029'
}

/**
 * Single-pass literal substitution: inserted user text is never parsed again.
 * Avoid potentially failing static initialization here: PetState also uses this file
 * to validate a name during app startup, before any text needs rendering.
 * The old nominative name is supported only for installed immutable legacy content.
 */
fun renderPetText(template: String, petName: String): String = buildString {
    val slot = "{petName}"
    val legacyName = PetDefaults.FOX_NAME
    var position = 0
    while (position < template.length) {
        val matchLength = when {
            template.startsWith(slot, position) -> slot.length
            template.startsWith(legacyName, position) &&
                (position == 0 || !isNameWordCodePoint(template.codePointBefore(position))) &&
                (position + legacyName.length == template.length ||
                    !isNameWordCodePoint(template.codePointAt(position + legacyName.length))) -> legacyName.length
            else -> 0
        }
        if (matchLength > 0) {
            append(petName)
            position += matchLength
        } else {
            append(template[position++])
        }
    }
}

private fun isNameWordCodePoint(value: Int): Boolean = value == '_'.code || Character.isLetter(value) ||
    when (Character.getType(value)) {
        Character.DECIMAL_DIGIT_NUMBER.toInt(), Character.LETTER_NUMBER.toInt(), Character.OTHER_NUMBER.toInt() -> true
        else -> false
    }
