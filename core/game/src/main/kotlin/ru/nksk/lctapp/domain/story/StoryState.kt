package ru.nksk.lctapp.domain.story

/** Chapter and goal are resolved through day -> chapter -> goal in the content catalog. */
@kotlinx.serialization.Serializable
data class StoryState(
    val currentDayId: String?,
    val nextScriptPosition: Int?,
    val activeEventId: String?,
    val decisions: List<StoryDecision>,
) {
    init {
        require(currentDayId != null || nextScriptPosition == null) {
            "A script position requires a current day"
        }
    }
}

/** Stable occurrence ID. Repeating a choice creates another occurrence, not another definition. */
@kotlinx.serialization.Serializable
data class StoryDecision(val id: String, val choiceId: String)
