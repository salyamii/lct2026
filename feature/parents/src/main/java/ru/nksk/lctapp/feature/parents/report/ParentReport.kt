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
data class ParentSkillAssessment(val status: ParentSkillStatus, val policyVersion: String)

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
)

@Immutable
sealed interface ParentReportUiState {
    data object Loading : ParentReportUiState
    data class Ready(val report: ParentReport) : ParentReportUiState
    data object Error : ParentReportUiState
}
