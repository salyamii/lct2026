package ru.nksk.lctapp.domain.analytics

import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test

class SkillIdTest {
    private val contract = linkedMapOf(
        "COMPARE_AMOUNTS" to "FIN-01",
        "PLAN_BUDGET" to "FIN-02",
        "PRIORITIZE_NEEDS" to "FIN-03",
        "MAKE_MONEY_LAST" to "FIN-04",
        "SAVE_FOR_GOAL" to "FIN-05",
        "DELAY_PURCHASE" to "FIN-06",
        "BUILD_EMERGENCY_FUND" to "FIN-07",
        "ADAPT_AFTER_EXPENSE" to "FIN-08",
        "COMPARE_COSTS" to "FIN-09",
        "PLAN_EXTRA_INCOME" to "FIN-10",
        "RECONSIDER_DECISION" to "FIN-11",
        "UNDERSTAND_INCOME_AND_EXPENSES" to "FIN-12",
    )

    @Test fun allTwelveNamesAndWireCodesExactlyMatchTheBackendContract() {
        assertEquals(contract.keys.toList(), SkillId.entries.map { it.name })
        assertEquals(12, SkillId.entries.map { it.id }.distinct().size)
        for ((name, code) in contract) {
            val skill = SkillId.valueOf(name)
            assertEquals(code, skill.id)
            assertEquals("\"$code\"", Json.encodeToString(skill))
            assertEquals(skill, Json.decodeFromString<SkillId>("\"$code\""))
        }
    }

    @Test fun unknownCodesAndKotlinNamesAreRejectedInsteadOfBeingGuessed() {
        for (wire in listOf("FIN-00", "FIN-13", "FIN-99", "fin-01", "COMPARE_AMOUNTS", "")) {
            try {
                Json.decodeFromString<SkillId>("\"$wire\"")
                fail("Unsupported skill was accepted: $wire")
            } catch (_: SerializationException) {
                // Strict wire contract: an unknown backend value must not silently change a skill.
            }
        }
    }

    @Test fun computedProfilesAndObservationsUseTheSameFinCodeSerializer() {
        val observation = SkillObservation("run", SkillId.RECONSIDER_DECISION, "episode",
            ObservationOutcome.SUPPORTED, ObservationEligibility.ELIGIBLE, EpisodeCompletion.COMPLETE,
            ObservationReason.EXPLANATION_APPLIED, listOf("event"), emptySet(), false,
            setOf(LearningContext.GAME), setOf("choice"))
        val profile = SkillProfile("run", observation.skill, listOf(observation))
        val encoded = Json.encodeToString(profile)
        assertEquals("FIN-11", Json.parseToJsonElement(encoded).jsonObject.getValue("skill").jsonPrimitive.content)
        assertEquals(profile, Json.decodeFromString<SkillProfile>(encoded))
        assertFalse(encoded.contains("RECONSIDER_DECISION"))
    }
}
