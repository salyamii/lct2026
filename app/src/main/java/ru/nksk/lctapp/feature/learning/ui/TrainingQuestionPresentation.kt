package ru.nksk.lctapp.feature.learning.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.nksk.lctapp.domain.engine.GameSession
import ru.nksk.lctapp.domain.finance.FinancialQuestion

/** A question's wording belongs to its original boundary, not to each subsequent answer. */
internal class TrainingQuestionPresentation(private val session: GameSession) {
    private val mutex = Mutex()
    private var source: FinancialQuestion? = null
    private var wording: FinancialQuestion? = null
    private var currentQuestionId: String? = null

    /** This screen authored the question with current wording; only restored questions need legacy repair. */
    fun expectCurrentQuestion(id: String) {
        currentQuestionId = id
    }

    suspend fun display(question: FinancialQuestion?): FinancialQuestion? {
        if (question == null) return null
        return mutex.withLock {
            // Answer progress is deliberately excluded: retries must not reread or reinterpret history.
            val key = question.copy(answeredOptionId = null, usedHint = false, attempts = 0)
            if (source != key) {
                val currentWording = key.id == currentQuestionId
                val refreshed = withContext(Dispatchers.Default) {
                    if (currentWording) key else financialPracticeExamplePresentation(key) ?: run {
                        val history = session.history()
                        financialPracticePresentation(key, history, session.catalog)
                    }
                }
                source = key
                wording = refreshed
            }
            val displayed = checkNotNull(wording)
            question.copy(prompt = displayed.prompt, explanation = displayed.explanation, options = displayed.options)
        }
    }
}
