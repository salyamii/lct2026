package ru.nksk.lctapp.domain.backend

import kotlinx.serialization.Serializable
import ru.nksk.lctapp.domain.analytics.AnalyticsFact
import ru.nksk.lctapp.domain.analytics.SkillEvaluator
import ru.nksk.lctapp.domain.analytics.SkillId
import ru.nksk.lctapp.domain.analytics.SkillObservation
import ru.nksk.lctapp.domain.analytics.SkillProfile
import ru.nksk.lctapp.domain.content.StoryContent
import ru.nksk.lctapp.domain.history.GameSnapshot
import ru.nksk.lctapp.domain.history.HistoryCodec
import ru.nksk.lctapp.domain.history.HistoryLearningProjection

/** Backend vocabulary only. Episode outcomes are not mastery statuses. */
@Serializable
enum class SkillStatus { MASTERED, PRACTICING, NO_DATA, HAS_PROBLEM }

/** Versioned, complete evidence projection for one run at a committed history boundary. */
@Serializable
data class AnalyticsUploadRequest(
    val batchId: String,
    val gameRunId: String,
    val throughHistorySequence: Long,
    val facts: List<AnalyticsFact>,
    val skills: List<SkillEvidenceDto>,
    val schemaVersion: Int = 1,
    val projectionVersion: Int = HistoryLearningProjection.VERSION,
    val evaluatorVersion: Int = 1,
) {
    init {
        require(batchId.isNotBlank() && gameRunId.isNotBlank())
        require(throughHistorySequence >= 0)
        require(schemaVersion > 0 && projectionVersion > 0 && evaluatorVersion > 0)
        require(facts.all { it.gameRunId == gameRunId && it.sequence <= throughHistorySequence })
        require(facts.map { it.eventId }.distinct().size == facts.size)
        require(skills.map { it.skillId }.toSet() == SkillId.entries.toSet() && skills.size == SkillId.entries.size)
        val factIds = facts.map { it.eventId }.toSet()
        require(skills.all { skill -> skill.observations.all { observation ->
            observation.gameRunId == gameRunId && observation.skill == skill.skillId &&
                observation.sourceEventIds.all { it in factIds }
        } })
    }
}

/** The client supplies evidence without inventing unapproved mastery thresholds. */
@Serializable
data class SkillEvidenceDto(
    val skillId: SkillId,
    val completedEpisodes: Int,
    val supportedEpisodes: Int,
    val difficultyEpisodes: Int,
    val neutralEpisodes: Int,
    val pendingEpisodes: Int,
    val incompleteEpisodes: Int,
    val supportedWithoutGameHints: Int,
    val assistedEpisodes: Int,
    val observations: List<SkillObservation>,
) {
    init {
        require(listOf(completedEpisodes, supportedEpisodes, difficultyEpisodes, neutralEpisodes,
            pendingEpisodes, incompleteEpisodes, supportedWithoutGameHints, assistedEpisodes).all { it >= 0 })
        require(observations.all { it.skill == skillId })
    }
}

/** The backend owns status policy; its version is independent of the client's evidence rules. */
@Serializable
data class SkillAssessmentDto(
    val skillId: SkillId,
    val status: SkillStatus,
    val policyVersion: String,
) {
    init { require(policyVersion.isNotBlank()) }
}

@Serializable
data class AnalyticsUploadResponse(
    val batchId: String,
    val gameRunId: String,
    val acceptedThroughHistorySequence: Long,
    val acceptedEventIds: List<String>,
    val schemaVersion: Int = 1,
) {
    init {
        require(batchId.isNotBlank() && gameRunId.isNotBlank() && acceptedThroughHistorySequence >= 0)
        require(schemaVersion > 0)
        require(acceptedEventIds.all(String::isNotBlank) && acceptedEventIds.distinct().size == acceptedEventIds.size)
    }
}

@Serializable
data class SkillAssessmentsResponse(
    val gameRunId: String,
    val basedOnHistorySequence: Long,
    val skills: List<SkillAssessmentDto>,
    val schemaVersion: Int = 1,
) {
    init {
        require(gameRunId.isNotBlank() && basedOnHistorySequence >= 0 && schemaVersion > 0)
        require(skills.map { it.skillId }.toSet() == SkillId.entries.toSet() && skills.size == SkillId.entries.size)
    }
}

/** Reconstructs evidence from exactly the same validated snapshot used for world backup. */
fun analyticsUploadRequest(
    batchId: String,
    snapshot: GameSnapshot,
    content: StoryContent,
): AnalyticsUploadRequest {
    HistoryCodec.validate(snapshot)
    return analyticsUploadRequest(batchId, snapshot.runId, snapshot.historySequence,
        HistoryLearningProjection.facts(snapshot.history, content))
}

/** [facts] must be the full HistoryLearningProjection result through the supplied boundary. */
fun analyticsUploadRequest(
    batchId: String,
    gameRunId: String,
    throughHistorySequence: Long,
    facts: List<AnalyticsFact>,
): AnalyticsUploadRequest {
    require(facts.all { it.gameRunId == gameRunId && it.sequence <= throughHistorySequence })
    val unique = facts.groupBy { it.eventId }.map { (id, copies) ->
        require(copies.all { it == copies.first() }) { "Conflicting analytics fact: $id" }
        copies.first()
    }.sortedWith(compareBy<AnalyticsFact> { it.sequence }.thenBy { it.eventId })
    return AnalyticsUploadRequest(
        batchId = batchId,
        gameRunId = gameRunId,
        throughHistorySequence = throughHistorySequence,
        facts = unique,
        skills = SkillEvaluator().project(gameRunId, unique).map(SkillProfile::toEvidenceDto),
    )
}

fun SkillProfile.toEvidenceDto(): SkillEvidenceDto = SkillEvidenceDto(
    skillId = skill,
    completedEpisodes = completedEpisodes,
    supportedEpisodes = supportedEpisodes,
    difficultyEpisodes = difficultyEpisodes,
    neutralEpisodes = neutralEpisodes,
    pendingEpisodes = pendingEpisodes,
    incompleteEpisodes = incompleteEpisodes,
    supportedWithoutGameHints = supportedWithoutGameHints,
    assistedEpisodes = assistedEpisodes,
    observations = observations,
)
