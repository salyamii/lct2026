package ru.nksk.lctapp.feature.parents.quests

import kotlinx.serialization.Serializable
import ru.nksk.lctapp.feature.parents.R

/** Authored presentation-only demos; these are not gameplay or backend quest IDs. */
@Serializable
enum class ParentQuest(
    val title: Int,
    val summary: Int,
    val description: Int,
    val steps: List<Int>,
    val completionMessage: Int,
) {
    SHOPPING(
        title = R.string.parents_shopping_title,
        summary = R.string.parents_shopping_summary,
        description = R.string.parents_shopping_description,
        steps = listOf(
            R.string.parents_shopping_step_choose,
            R.string.parents_shopping_step_compare,
            R.string.parents_shopping_step_discuss,
            R.string.parents_shopping_step_decide,
        ),
        completionMessage = R.string.parents_shopping_completed,
    ),
    WEEKEND(
        title = R.string.parents_weekend_title,
        summary = R.string.parents_weekend_summary,
        description = R.string.parents_weekend_description,
        steps = listOf(
            R.string.parents_weekend_step_budget,
            R.string.parents_weekend_step_compare,
            R.string.parents_weekend_step_choose,
            R.string.parents_weekend_step_reflect,
        ),
        completionMessage = R.string.parents_weekend_completed,
    ),
    SECOND_LIFE(
        title = R.string.parents_second_life_title,
        summary = R.string.parents_second_life_summary,
        description = R.string.parents_second_life_description,
        steps = listOf(
            R.string.parents_second_life_step_choose,
            R.string.parents_second_life_step_compare,
            R.string.parents_second_life_step_decide,
            R.string.parents_second_life_step_reflect,
        ),
        completionMessage = R.string.parents_second_life_completed,
    ),
}
