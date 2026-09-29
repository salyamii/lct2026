package ru.nksk.lctapp.feature.parents.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.nksk.lctapp.feature.parents.quests.ParentQuest

@Serializable
@SerialName("parent_report")
data object ParentReportRoute : NavKey

@Serializable
@SerialName("parent_topic")
data class ParentTopicRoute(val skillId: String) : NavKey

@Serializable
@SerialName("parent_quests")
data object ParentQuestsRoute : NavKey

@Serializable
@SerialName("parent_shopping_quest")
data object ParentShoppingQuestRoute : NavKey

@Serializable
@SerialName("parent_quest")
data class ParentQuestRoute(val quest: ParentQuest) : NavKey
