package ru.nksk.lctapp.feature.learning.ui

/** A prompt and all answer choices may be read in separate scroll positions. */
internal class QuestionExposure(optionIds: List<String>) {
    private val requiredParts = optionIds.map { optionPart(it) }.toSet() + PROMPT
    private val hasOptions = optionIds.isNotEmpty()
    private val seenParts = mutableSetOf<String>()
    private var reported = false

    /** True exactly once, after every required part has actually been fully visible. */
    fun record(part: String, fullyVisible: Boolean): Boolean {
        if (!fullyVisible || part !in requiredParts || reported || !hasOptions) return false
        seenParts += part
        if (!seenParts.containsAll(requiredParts)) return false
        reported = true
        return true
    }

    companion object {
        const val PROMPT = "prompt"
        fun optionPart(id: String) = "option:$id"
    }
}
