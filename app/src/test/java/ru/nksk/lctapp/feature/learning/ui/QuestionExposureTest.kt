package ru.nksk.lctapp.feature.learning.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionExposureTest {
    @Test fun promptAloneOrAChosenOptionDoesNotMeanTheWholeQuestionWasShown() {
        val exposure = QuestionExposure(listOf("first", "second", "third"))
        assertFalse(exposure.record(QuestionExposure.PROMPT, true))
        assertFalse(exposure.record(QuestionExposure.optionPart("first"), true))
        assertFalse(exposure.record(QuestionExposure.optionPart("second"), false))
        assertFalse(exposure.record(QuestionExposure.optionPart("third"), false))
    }

    @Test fun scrollingAccumulatesActualVisibilityAndReportsOnlyAfterTheLastMissingPart() {
        val exposure = QuestionExposure(listOf("first", "second", "third"))
        assertFalse(exposure.record(QuestionExposure.optionPart("third"), true))
        assertFalse(exposure.record(QuestionExposure.PROMPT, true))
        assertFalse(exposure.record(QuestionExposure.optionPart("first"), true))
        assertTrue(exposure.record(QuestionExposure.optionPart("second"), true))
        assertFalse(exposure.record(QuestionExposure.PROMPT, true))
        assertFalse(exposure.record(QuestionExposure.optionPart("second"), true))
    }

    @Test fun unknownOrPartlyClippedContentCannotCompleteEvidenceAndANewQuestionStartsEmpty() {
        val exposure = QuestionExposure(listOf("only"))
        assertFalse(exposure.record("unknown", true))
        assertFalse(exposure.record(QuestionExposure.PROMPT, false))
        assertFalse(exposure.record(QuestionExposure.optionPart("only"), true))
        assertTrue(exposure.record(QuestionExposure.PROMPT, true))

        val nextQuestion = QuestionExposure(listOf("only"))
        assertFalse(nextQuestion.record(QuestionExposure.optionPart("only"), true))
        assertFalse(QuestionExposure(emptyList()).record(QuestionExposure.PROMPT, true))
    }
}
