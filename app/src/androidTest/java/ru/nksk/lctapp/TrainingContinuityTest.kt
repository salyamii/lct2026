package ru.nksk.lctapp

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import ru.nksk.lctapp.domain.finance.FinancialQuestionKind
import ru.nksk.lctapp.domain.finance.FinancialTraining
import ru.nksk.lctapp.feature.learning.ui.TrainingScreen
import ru.nksk.lctapp.feature.learning.ui.TrainingUiState

class TrainingContinuityTest {
    @get:Rule val compose = createComposeRule()

    @Test fun savingAndAwaitingTheNextQuestionKeepTheQuestionInPlace() {
        val question = FinancialTraining.standalone(FinancialQuestionKind.SAVING_PRACTICE, "continuity")
        val state = mutableStateOf(TrainingUiState(loading = false, hasGame = true, canReview = true,
            question = question))
        compose.setContent { MaterialTheme { TrainingScreen(state.value, onAction = {}, onBack = {}) } }
        val before = compose.onNodeWithText(question.prompt).fetchSemanticsNode().boundsInRoot

        compose.runOnIdle { state.value = state.value.copy(busy = true) }
        assertEquals(before, compose.onNodeWithText(question.prompt).fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithText("Сохраняем…").assertDoesNotExist()

        compose.runOnIdle {
            state.value = state.value.copy(busy = false, question = question.copy(
                answeredOptionId = question.correctAnswerId, attempts = 1))
        }
        assertEquals(before, compose.onNodeWithText(question.prompt).fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithText("Верно!").assertDoesNotExist()
        compose.onNodeWithText(question.options.first().text).assertExists()
    }
}
