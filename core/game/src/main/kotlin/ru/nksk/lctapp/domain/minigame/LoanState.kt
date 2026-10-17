package ru.nksk.lctapp.domain.minigame

import kotlin.random.Random

/** Один из двух путей в сценарии «Финансовой ловушки». */
data class LoanOption(val title: String, val detail: String)

/**
 * Сценарий выбора: два пути с денежными последствиями, [firstIsRight] указывает выгодный.
 * [outcome] - итог верного выбора с числами, показывается после ответа.
 */
data class LoanScenario(
    val prompt: String,
    val first: LoanOption,
    val second: LoanOption,
    val firstIsRight: Boolean,
    val outcome: String,
) {
    init {
        require(prompt.isNotBlank() && outcome.isNotBlank() &&
            first.title.isNotBlank() && second.title.isNotBlank())
    }
}

/**
 * Чистое состояние выбора между «получить сейчас и вернуть больше» и «накопить самому»:
 * сценарии фиксированные, порядок каждого запуска новый. Финал после последнего сценария.
 */
data class LoanState(
    val scenarios: List<LoanScenario>,
    val current: Int = 0,
    val correctAnswers: Int = 0,
    val lastCorrect: Boolean? = null,
) {
    val scenario: LoanScenario get() = scenarios[current]
    val finished: Boolean get() = current >= scenarios.size

    /** true - выбран первый путь. Фиксирует результат и итог сценария, ждёт [next]. */
    fun answer(pickedFirst: Boolean): LoanState {
        if (finished || lastCorrect != null) return this
        val correct = scenario.firstIsRight == pickedFirst
        return copy(correctAnswers = if (correct) correctAnswers + 1 else correctAnswers, lastCorrect = correct)
    }

    fun next(): LoanState {
        if (finished || lastCorrect == null) return this
        return copy(current = current + 1, lastCorrect = null)
    }

    companion object {
        const val SCENARIO_COUNT = 4

        /** Фиксированные сценарии мира игры; случайным остаётся только порядок. */
        private val SCENARIOS = listOf(
            LoanScenario(
                prompt = "Торговец предлагает 100 монет сейчас - под расписку вернуть ему 150 после путешествия.",
                first = LoanOption("Взять монеты сейчас", "Получишь 100 монет, но вернёшь 150"),
                second = LoanOption("Накопить самому", "Отложишь 100 монет без долгов"),
                firstIsRight = false,
                outcome = "Заём стоит 50 монет: возвращать придётся на 50 больше, чем взял.",
            ),
            LoanScenario(
                prompt = "Нужна новая лейка за 30 монет, а в мешочке только 12. Сосед даёт в долг под расписку: вернуть 45.",
                first = LoanOption("Занять у соседа", "Получишь лейку сейчас, вернёшь 45"),
                second = LoanOption("Подождать и накопить", "Ещё 18 монет - и лейка своя"),
                firstIsRight = false,
                outcome = "Лейка обошлась бы в 30 монет, а по расписке придётся отдать 45 - на 15 больше.",
            ),
            LoanScenario(
                prompt = "До отплытия лодки три дня. Билет стоит 20 монет, но завтра в лавке скидка: тот же билет за 14.",
                first = LoanOption("Купить билет сразу", "Останешься с 0 монет до отплытия"),
                second = LoanOption("Купить завтра по скидке", "Сэкономишь 6 монет и вернёшься в лавку"),
                firstIsRight = false,
                outcome = "Скидка экономит 6 монет - они пригодятся на дорогу.",
            ),
            LoanScenario(
                prompt = "Ремонт крыльца стоит 60 монет. Мастер берёт только весь расчёт сразу; у тебя 40, но через неделю ждут награда за работу.",
                first = LoanOption("Отложить ремонт до награды", "Крыльцо подождёт неделю"),
                second = LoanOption("Занять 20 под расписку на 35", "Ремонт сейчас, вернёшь 35 вместо 20"),
                firstIsRight = true,
                outcome = "Занятые 20 монет превратились бы в 35 - лишние 15 монет за одну неделю.",
            ),
        )

        /** Фиксированные сценарии мира игры; create() перемешивает только их порядок. */
        fun fixedScenarios(): List<LoanScenario> = SCENARIOS

        fun create(random: Random = Random.Default): LoanState =
            LoanState(scenarios = SCENARIOS.shuffled(random))
    }
}
