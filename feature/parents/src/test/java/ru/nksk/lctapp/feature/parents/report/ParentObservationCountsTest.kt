package ru.nksk.lctapp.feature.parents.report

import org.junit.Assert.assertEquals
import org.junit.Test

class ParentObservationCountsTest {
    @Test fun serverCountsExplainTheDisplayedAssessmentEvenWhenLocalHistoryIsNewer() {
        val skill = ParentSkill(
            id = "FIN-01", title = "Сравнивает денежные суммы",
            observedEpisodes = 10, supportedEpisodes = 9, difficultyEpisodes = 1,
            assessment = ParentSkillAssessment(
                ParentSkillStatus.MASTERED, "skills-mvp-v1",
                observationCounts = ParentObservationCounts(6, 6, 5, 1, 0, 0, 0, 5, 0),
            ),
        )
        assertEquals(6, skill.displayObservationCounts.observedEpisodes)
        assertEquals(5, skill.displayObservationCounts.supportedEpisodes)
        assertEquals(1, skill.displayObservationCounts.difficultyEpisodes)
        assertEquals(5, skill.displayObservationCounts.supportedWithoutGameHints)
        assertEquals(9, skill.supportedEpisodes)
    }

    @Test fun missingServerCountsPreserveOfflineLocalObservations() {
        val skill = ParentSkill("FIN-01", "Суммы", observedEpisodes = 3,
            supportedEpisodes = 2, difficultyEpisodes = 1, supportedWithoutGameHints = 2)
        assertEquals(3, skill.displayObservationCounts.observedEpisodes)
        assertEquals(2, skill.displayObservationCounts.supportedEpisodes)
        assertEquals(1, skill.displayObservationCounts.difficultyEpisodes)
    }

    @Test fun emptyServerCountsAreNotReplacedByLocalCounts() {
        val skill = ParentSkill("FIN-01", "Суммы", observedEpisodes = 3, supportedEpisodes = 3,
            assessment = ParentSkillAssessment(ParentSkillStatus.NO_DATA, "skills-mvp-v1",
                observationCounts = ParentObservationCounts(0, 0, 0, 0, 0, 0, 0, 0, 0)))
        assertEquals(0, skill.displayObservationCounts.observedEpisodes)
        assertEquals(0, skill.displayObservationCounts.supportedEpisodes)
    }
}
