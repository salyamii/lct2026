package ru.nksk.lctapp.domain.backend

import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.domain.analytics.*

class AnalyticsContractTest {
    private val json = Json { encodeDefaults = true; classDiscriminator = "_type" }

    @Test fun statusesMatchBackendExactlyWithoutReusingEpisodeOutcomes() {
        assertEquals(listOf("MASTERED", "PRACTICING", "NO_DATA", "HAS_PROBLEM"), SkillStatus.entries.map { it.name })
        SkillStatus.entries.forEach { status ->
            assertEquals("\"${status.name}\"", json.encodeToString(status))
            assertEquals(status, json.decodeFromString<SkillStatus>("\"${status.name}\""))
        }
        try {
            json.decodeFromString<SkillStatus>("\"SUPPORTED\"")
            fail("A supported episode must not silently become a mastery status")
        } catch (_: SerializationException) {
            // Backend policy must make this decision explicitly.
        }
    }

    @Test fun emptyHistoryExportsEverySkillWithNoInventedAssessment() {
        val request = analyticsUploadRequest("device", "batch", "run", 0, emptyList())
        assertEquals(SkillId.entries.toList(), request.skills.map { it.skillId })
        assertTrue(request.skills.all { it.observations.isEmpty() && it.completedEpisodes == 0 })
        val encoded = json.encodeToString(request)
        assertFalse(encoded.contains("\"status\""))
        assertEquals(request, json.decodeFromString<AnalyticsUploadRequest>(encoded))
    }

    @Test fun duplicateDeliveryDoesNotMultiplyEvidenceAndIncompleteContextStaysIncomplete() {
        val fact = AnalyticsFact("event", "run", "episode", "action", 2,
            FactDetail.OptionalPurchase("boat", 5, true))
        val request = analyticsUploadRequest("device", "batch", "run", 2, listOf(fact, fact))
        assertEquals(1, request.facts.size)
        val skill = request.skills.single { it.skillId == SkillId.PRIORITIZE_NEEDS }
        assertEquals(0, skill.completedEpisodes)
        assertEquals(0, skill.difficultyEpisodes)
        assertEquals(1, skill.incompleteEpisodes)
        assertEquals(ObservationOutcome.INSUFFICIENT_DATA, skill.observations.single().outcome)
        val encoded = json.encodeToString(request)
        assertTrue(encoded.contains("\"skillId\":\"FIN-03\""))
        assertTrue(encoded.contains("\"_type\":\"optional_purchase\""))
        assertEquals(request, json.decodeFromString<AnalyticsUploadRequest>(encoded))
    }

    @Test(expected = IllegalArgumentException::class)
    fun evidenceFromAnotherRunCannotBeAttributedToThisChild() {
        analyticsUploadRequest("device", "batch", "run", 2, listOf(
            AnalyticsFact("event", "other-run", "episode", "action", 2, FactDetail.Interaction("open"))))
    }

    @Test(expected = IllegalArgumentException::class)
    fun evidenceAfterSnapshotBoundaryCannotBeUploadedWithAnOlderRevision() {
        analyticsUploadRequest("device", "batch", "run", 2, listOf(
            AnalyticsFact("event", "run", "episode", "action", 3, FactDetail.Interaction("open"))))
    }

    @Test fun backendStatusesRoundTripForAllTwelveSkills() {
        val response = SkillAssessmentsResponse("run", 8,
            SkillId.entries.mapIndexed { index, skill ->
                SkillAssessmentDto(skill, SkillStatus.entries[index % SkillStatus.entries.size], "agreed-policy-v1")
            })
        val encoded = json.encodeToString(response)
        assertEquals(response, json.decodeFromString<SkillAssessmentsResponse>(encoded))
        assertFalse(encoded.contains("COMPARE_AMOUNTS"))
    }
}
