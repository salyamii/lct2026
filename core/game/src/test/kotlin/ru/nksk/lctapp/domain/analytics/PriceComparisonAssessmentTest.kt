package ru.nksk.lctapp.domain.analytics

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class PriceComparisonAssessmentTest {
    private val json = Json { encodeDefaults = true; classDiscriminator = "_type" }

    @Test fun bothTargetsCheckTheActualSelectedSide() {
        for (pickLarger in listOf(true, false)) {
            for ((left, right) in listOf(10L to 199L, 199L to 10L, 198L to 199L)) {
                val expected = if (if (pickLarger) left > right else left < right)
                    ComparisonSide.LEFT else ComparisonSide.RIGHT
                for (chosen in ComparisonSide.entries) {
                    assertEquals(chosen == expected,
                        AssessmentTask.CompareAmounts(left, right, chosen, pickLarger).isCorrect())
                }
            }
            assertTrue(AssessmentTask.CompareAmounts(100, 100, ComparisonSide.EQUAL, pickLarger).isCorrect())
            assertFalse(AssessmentTask.CompareAmounts(100, 100, ComparisonSide.LEFT, pickLarger).isCorrect())
        }
    }

    @Test fun legacyTaskKeepsItsExactCanonicalJsonAndLargerTarget() {
        val legacy = """{"_type":"compare_amounts","left":80,"right":20,"chosen":"LEFT","purpose":"COMPARE_AMOUNTS"}"""
        val task = json.decodeFromString<AssessmentTask>(legacy) as AssessmentTask.CompareAmounts
        assertTrue(task.pickLarger)
        assertTrue(task.isCorrect())
        assertEquals(legacy, json.encodeToString<AssessmentTask>(task))
    }

    @Test fun smallerTargetSurvivesSerialization() {
        val task: AssessmentTask = AssessmentTask.CompareAmounts(80, 20, ComparisonSide.RIGHT, pickLarger = false)
        val encoded = json.encodeToString(task)
        assertTrue(encoded.contains("\"pickLarger\":false"))
        assertEquals(task, json.decodeFromString<AssessmentTask>(encoded))
        assertTrue(json.decodeFromString<AssessmentTask>(encoded).isCorrect())
    }
}
