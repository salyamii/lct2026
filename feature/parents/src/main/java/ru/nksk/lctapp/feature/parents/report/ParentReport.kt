package ru.nksk.lctapp.feature.parents.report

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.Flow

/** Local game evidence and server assessments for this installation. No selectable device identity. */
interface ParentReportRepository {
    fun observeReport(): Flow<ParentReport>
    /** Transport failures are reflected in assessmentSync; cancellation propagates to the caller. */
    suspend fun refreshAssessments()
}

@Immutable
data class ParentReport(
    /** Null means that onboarding has not created a saved game yet. */
    val pet: ParentPet? = null,
    val skills: List<ParentSkill> = emptyList(),
    val assessmentSync: ParentAssessmentSync = ParentAssessmentSync(),
    val materialsUnpublished: Boolean = false,
    val materialsRefreshing: Boolean = false,
    val materialsRefreshFailed: Boolean = false,
)

enum class ParentSyncPhase { IDLE, SYNCING, UNAVAILABLE, OFFLINE, ERROR }

@Immutable
data class ParentAssessmentSync(
    val phase: ParentSyncPhase = ParentSyncPhase.IDLE,
    /** The server has not assessed all actions in the current local history yet. */
    val isStale: Boolean = false,
)

enum class ParentSkillStatus { MASTERED, PRACTICING, NO_DATA, HAS_PROBLEM }

@Immutable
data class ParentSkillAssessment(
    val status: ParentSkillStatus,
    val policyVersion: String,
    val explanation: ParentSkillExplanation? = null,
    val assessmentReason: String? = null,
    val observationCounts: ParentObservationCounts? = null,
)

@Immutable
data class ParentObservationCounts(
    val observedEpisodes: Int,
    val completedEpisodes: Int,
    val supportedEpisodes: Int,
    val difficultyEpisodes: Int,
    val neutralEpisodes: Int,
    val pendingEpisodes: Int,
    val incompleteEpisodes: Int,
    val supportedWithoutGameHints: Int,
    val assistedEpisodes: Int,
)

@Immutable
data class ParentSkillExplanation(
    val description: String,
    val summary: String,
    val evidence: List<String>,
)

@Immutable
data class ParentPet(val name: String, val availableCoins: Long, val savingsCoins: Long) {
    val balance: Long get() = Math.addExact(availableCoins, savingsCoins)
}

/** Evidence counts describe game episodes; they never imply a mastery level or age norm. */
@Immutable
data class ParentSkill(
    val id: String,
    val title: String,
    val observedEpisodes: Int = 0,
    val supportedEpisodes: Int = 0,
    val difficultyEpisodes: Int = 0,
    val neutralEpisodes: Int = 0,
    val pendingEpisodes: Int = 0,
    val incompleteEpisodes: Int = 0,
    val supportedWithoutGameHints: Int = 0,
    val assistedEpisodes: Int = 0,
    /** Null means no server assessment was received, distinct from the server's NO_DATA. */
    val assessment: ParentSkillAssessment? = null,
    val material: ParentTopicMaterial? = null,
    /** Current local observations, kept separate from the server assessment's history boundary. */
    val localExplanation: ParentSkillExplanation? = null,
    val completedEpisodes: Int = 0,
)

/** Prefer the server's counts so the numbers explain the displayed server status. */
internal val ParentSkill.displayObservationCounts: ParentObservationCounts
    get() = assessment?.observationCounts ?: ParentObservationCounts(
        observedEpisodes, completedEpisodes, supportedEpisodes, difficultyEpisodes,
        neutralEpisodes, pendingEpisodes, incompleteEpisodes, supportedWithoutGameHints, assistedEpisodes,
    )

@Immutable
sealed interface ParentReportUiState {
    data object Loading : ParentReportUiState
    data class Ready(val report: ParentReport) : ParentReportUiState
    data object Error : ParentReportUiState
}

@Immutable
data class ParentTopicMaterial(
    val learningGoal: String,
    val story: String,
    val replaceWithParentStory: String,
    val conversationStarters: List<String>,
    val parentTakeaway: String,
    val researchBasis: String,
    val researchSources: List<String>,
)

/** Deduplicate exact wording only; retain separate recorded events. */
internal fun ParentSkillExplanation.paragraphs(): List<String> =
    (listOf(summary) + evidence).map(String::trim).filter(String::isNotBlank)
        .distinctBy { it.replace(Regex("\\s+"), " ") }.take(3)
