package ru.nksk.lctapp.domain.backend

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import ru.nksk.lctapp.domain.analytics.SkillId

/** Editorial material is independent of a child's world and mastery assessment. */
@Serializable
data class ParentSkillMaterialDto(
    val skillId: String,
    val learningGoal: String,
    val story: String,
    val replaceWithParentStory: String,
    val conversationStarters: List<String>,
    val parentTakeaway: String,
    val researchBasis: String,
    val researchSources: List<String>,
) {
    init {
        require(skillId in SkillId.entries.map { it.id })
        require(listOf(learningGoal, story, replaceWithParentStory, parentTakeaway, researchBasis).all { it.isNotBlank() })
        require(conversationStarters.size == 5 && conversationStarters.all { it.isNotBlank() })
        require(researchSources.isNotEmpty() && researchSources.all {
            it.startsWith("https://") && it.removePrefix("https://").substringBefore('/').isNotBlank()
        })
    }
}

@Serializable
enum class ParentMaterialsPublicationStatus { UNPUBLISHED, PUBLISHED }

@Serializable
data class ParentMaterialsCatalog(
    val contentVersion: String,
    val skills: List<ParentSkillMaterialDto>,
    val schemaVersion: Int = 1,
    val publicationStatus: ParentMaterialsPublicationStatus = ParentMaterialsPublicationStatus.PUBLISHED,
) {
    init {
        require(schemaVersion == 1 && contentVersion.isNotBlank())
        when (publicationStatus) {
            ParentMaterialsPublicationStatus.UNPUBLISHED -> require(skills.isEmpty())
            ParentMaterialsPublicationStatus.PUBLISHED -> require(skills.size == SkillId.entries.size &&
                skills.map { it.skillId }.toSet() == SkillId.entries.map { it.id }.toSet())
        }
    }
}

data class ParentMaterialsState(
    val catalog: ParentMaterialsCatalog? = null,
    val isRefreshing: Boolean = false,
    val refreshFailed: Boolean = false,
)

interface ParentMaterialsRepository {
    fun observe(): Flow<ParentMaterialsState>
    suspend fun refresh()
}
