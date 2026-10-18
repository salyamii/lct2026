package ru.nksk.lctapp.domain.survey

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SurveyInvitationPolicyTest {
    @Test fun firstReminderWaitsUntilTheEndOfTheFirstGameWeek() {
        (1..6).forEach { assertFalse(SurveyInvitationPolicy.canInvite(false, it, null)) }
        assertTrue(SurveyInvitationPolicy.canInvite(false, 7, null))
    }

    @Test fun aCalendarWeekBoundaryDoesNotShortenTheSevenDayInterval() {
        (8..14).forEach { assertFalse(SurveyInvitationPolicy.canInvite(false, it, 8)) }
        assertTrue(SurveyInvitationPolicy.canInvite(false, 15, 8))
    }

    @Test fun returningToAnOlderSaveCannotRepeatAReminder() {
        assertFalse(SurveyInvitationPolicy.canInvite(false, 7, 14))
        assertFalse(SurveyInvitationPolicy.canInvite(false, 14, 14))
        assertFalse(SurveyInvitationPolicy.canInvite(false, Int.MAX_VALUE - 1, Int.MAX_VALUE))
    }

    @Test fun completionPermanentlySuppressesInvitations() {
        assertFalse(SurveyInvitationPolicy.canInvite(true, 7, null))
        assertFalse(SurveyInvitationPolicy.canInvite(true, 100, 7))
    }
}
