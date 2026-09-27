package ru.nksk.lctapp.domain.analytics

import kotlinx.serialization.Serializable

@Serializable

enum class ObservationOutcome { SUPPORTED, DIFFICULTY, NEUTRAL, INSUFFICIENT_DATA }
@Serializable
enum class ObservationEligibility { ELIGIBLE, NOT_ELIGIBLE, UNDETERMINED }
@Serializable
enum class EpisodeCompletion { COMPLETE, PENDING }

@Serializable

enum class ObservationReason {
    CONTEXT_MISSING, PRESENTATION_MISSING, INVALID_CONTEXT, EPISODE_PENDING,
    CORRECT_COMPARISON, INCORRECT_COMPARISON, VALID_PLAN, KNOWN_NEED_OMITTED,
    ORIGINAL_SHORTFALL, INFEASIBLE_ALLOCATION, UNKNOWN_REVISION_CAUSE,
    NEEDS_COVERED, AVOIDABLE_NEEDS_GAP, FUNDING_PLAN_SELECTED, NO_AVAILABLE_CHOICE,
    INTERVAL_COVERED, NO_MANAGED_DECISION, SAVING_PROGRESS, SAVING_INTENTION_NOT_APPLIED, CIRCULAR_TRANSFER,
    NO_SAVING_OPPORTUNITY, PRIORITY_APPLIED, PRIORITY_CONFLICT_MISUNDERSTOOD, DESIRE_UNKNOWN, PRIORITY_UNKNOWN,
    RESERVE_PRESERVED, RESERVE_USED, RESERVE_OVERSTATED, NO_RESERVE_INTENTION,
    RECOVERY_COMPLETED, GAP_WORSENED, NO_EXTERNAL_EXPENSE, CHANGE_NOT_UNEXPECTED,
    PRIORITY_RESOURCE_PRESERVED, PRIORITY_RESOURCE_LOST, NO_RANKED_ALTERNATIVE,
    EARNING_PLAN_COMPLETED, EARNING_PLAN_INFEASIBLE, CORRECT_EXPLANATION,
    INCORRECT_EXPLANATION, EXPLANATION_APPLIED, CORRECT_LEDGER_ANSWER, INCORRECT_LEDGER_ANSWER,
}

@Serializable

data class SkillObservation(
    val gameRunId: String,
    val skill: SkillId,
    val episodeId: String,
    val outcome: ObservationOutcome,
    val eligibility: ObservationEligibility,
    val completion: EpisodeCompletion,
    val reason: ObservationReason,
    val sourceEventIds: List<String>,
    val assistance: Set<Assistance>,
    val adultHelpKnown: Boolean,
    val learningContexts: Set<LearningContext>,
    val contextFamilies: Set<String>,
    val measures: Map<String, Long> = emptyMap(),
    val ruleVersion: Int = 1,
) {
    val independentOfGameHints: Boolean
        get() = assistance.none { it != Assistance.INFORMATION_ONLY }
}

/** Counts and evidence only: no unapproved mastery thresholds, score or age norm. */
@Serializable
data class SkillProfile(
    val gameRunId: String,
    val skill: SkillId,
    val observations: List<SkillObservation>,
) {
    val completedEpisodes: Int get() = observations.count { it.completion == EpisodeCompletion.COMPLETE && it.eligibility == ObservationEligibility.ELIGIBLE }
    val supportedEpisodes: Int get() = observations.count { it.outcome == ObservationOutcome.SUPPORTED }
    val difficultyEpisodes: Int get() = observations.count { it.outcome == ObservationOutcome.DIFFICULTY }
    val neutralEpisodes: Int get() = observations.count { it.outcome == ObservationOutcome.NEUTRAL }
    val pendingEpisodes: Int get() = observations.count { it.completion == EpisodeCompletion.PENDING }
    val incompleteEpisodes: Int get() = observations.count { it.outcome == ObservationOutcome.INSUFFICIENT_DATA }
    val supportedWithoutGameHints: Int get() = observations.count { it.outcome == ObservationOutcome.SUPPORTED && it.independentOfGameHints }
    val assistedEpisodes: Int get() = observations.count { !it.independentOfGameHints }
    val contextFamilies: Set<String> get() = observations.flatMap { it.contextFamilies }.toSet()
    val hasObservations: Boolean get() = observations.isNotEmpty()
}
