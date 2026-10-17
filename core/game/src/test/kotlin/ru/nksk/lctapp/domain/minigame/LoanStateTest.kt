package ru.nksk.lctapp.domain.minigame

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LoanStateTest {
    @Test
    fun create_shufflesFixedScenarios() {
        val state = LoanState.create(random = Random(21))
        assertEquals(LoanState.SCENARIO_COUNT, state.scenarios.size)
    }

    @Test
    fun answer_rightOption_countsCorrect() {
        val scenario = LoanScenario("подсказка", LoanOption("А", "деталь"), LoanOption("Б", "деталь"),
            firstIsRight = true, outcome = "итог")
        val answered = LoanState(listOf(scenario)).answer(pickedFirst = true)
        assertEquals(true, answered.lastCorrect)
        assertEquals(1, answered.correctAnswers)
    }

    @Test
    fun answer_twiceWithoutNext_isIgnored() {
        val scenario = LoanScenario("подсказка", LoanOption("А", "деталь"), LoanOption("Б", "деталь"),
            firstIsRight = true, outcome = "итог")
        val answered = LoanState(listOf(scenario)).answer(pickedFirst = true)
        assertEquals(answered, answered.answer(pickedFirst = false))
    }

    @Test
    fun afterLastScenario_gameFinishes() {
        var state: LoanState = LoanState(List(LoanState.SCENARIO_COUNT) {
            LoanScenario("п", LoanOption("А", "д"), LoanOption("Б", "д"), firstIsRight = false, outcome = "и")
        })
        repeat(LoanState.SCENARIO_COUNT) { state = state.answer(pickedFirst = false).next() }
        assertTrue(state.finished)
        assertNotNull(DeedGameScore.fromLoan(state))
    }

    @Test
    fun unfinishedGame_producesNoScore() {
        assertNull(DeedGameScore.fromLoan(LoanState.create().answer(pickedFirst = true)))
        assertFalse(LoanState.create().finished)
    }
}
