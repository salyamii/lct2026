package ru.nksk.lctapp.domain.survey

import kotlinx.coroutines.flow.Flow

data class SurveyInvitation(val runId: String, val day: Int)

/** Device preferences for this questionnaire, outside the saved game and cloud world. */
interface SurveyRepository {
    fun observeCompleted(): Flow<Boolean>
    suspend fun markCompleted()
    /** Claims a reminder atomically; only a finished game day can qualify. */
    suspend fun claimInvitation(): SurveyInvitation?
    /** Reads the saved identity and current run again for every opening. Never creates a game. */
    suspend fun formUrl(): String
}

object SurveyInvitationPolicy {
    const val DaysBetweenInvitations = 7

    fun canInvite(completed: Boolean, day: Int, lastInvitedDay: Int?): Boolean =
        !completed && day >= DaysBetweenInvitations &&
            (lastInvitedDay == null || day.toLong() - lastInvitedDay >= DaysBetweenInvitations)
}
