package ru.nksk.lctapp.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import ru.nksk.lctapp.R

@Suppress("DiscouragedApi")
@Composable
internal fun introductionIcons(): Map<String, Int> {
    if (LocalInspectionMode.current) {
        val resources = LocalContext.current.resources
        return listOf("basket", "diamond", "goal", "reserve").associateWith {
            resources.getIdentifier("introduction_$it", "drawable", "ru.nksk.lctapp").also { id -> check(id != 0) }
        }
    }
    return mapOf("basket" to R.drawable.introduction_basket, "diamond" to R.drawable.introduction_diamond,
        "goal" to R.drawable.introduction_goal, "reserve" to R.drawable.introduction_reserve)
}
