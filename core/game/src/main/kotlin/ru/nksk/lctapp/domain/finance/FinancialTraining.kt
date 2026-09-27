package ru.nksk.lctapp.domain.finance

import ru.nksk.lctapp.domain.analytics.AssessmentTask
import ru.nksk.lctapp.domain.analytics.LedgerEntry
import ru.nksk.lctapp.domain.analytics.LedgerKind
import ru.nksk.lctapp.domain.analytics.LedgerQuestion

/** Four frozen questions per topic; only the first can carry existing local progression evidence. */
object FinancialTraining {
    /** Training also remains available once the last goal period has closed. */
    fun standalone(kind: FinancialQuestionKind, seriesId: String): FinancialQuestion {
        val id = "$seriesId:question"
        val source = listOf("training:$seriesId:example")
        val first = when (kind) {
            FinancialQuestionKind.PLAN_REVIEW -> FinancialQuestion(id, kind,
                "Представь: получили 40 монет. На еду оставили 25, на приятные покупки — 10. Сколько осталось распределить?",
                listOf(FinancialAnswerOption("5", "5 монет"), FinancialAnswerOption("15", "15 монет"),
                    FinancialAnswerOption("40", "40 монет")), "5", "Уже распределили 25 + 10 = 35 монет. Из 40 осталось ещё 5.",
                sourceActionIds = source, version = 2)
            FinancialQuestionKind.CONSEQUENCE -> FinancialQuestion(id, kind,
                "Представь: у нас 30 монет, а на еду нужно 25. Игра на ярмарке стоит 7. Что произойдёт, если сыграем?",
                listOf(FinancialAnswerOption("short", "На еду не хватит 2 монет"),
                    FinancialAnswerOption("covered", "На еду всё ещё хватит"),
                    FinancialAnswerOption("automatic", "Копилка сама оплатит игру")), "short",
                "Останется 30 − 7 = 23 монеты. На еду нужно 25 — не хватает 2.",
                sourceActionIds = source, version = 2)
            FinancialQuestionKind.TRANSACTION_ACCOUNTING -> {
                val task = AssessmentTask.ReadLedger(40, 0, listOf(
                    LedgerEntry("$seriesId:example:food", LedgerKind.AVAILABLE_EXPENSE, 5),
                    LedgerEntry("$seriesId:example:repair", LedgerKind.AVAILABLE_EXPENSE, 8),
                    LedgerEntry("$seriesId:example:saving", LedgerKind.DEPOSIT, 10),
                ), LedgerQuestion.EXPENSE, 0)
                FinancialQuestion(id, kind, "Представь: обед стоил 5 монет, ремонт рюкзака — 8. Ещё 10 положили в копилку. Сколько потратили?",
                    listOf(FinancialAnswerOption("13", "13 монет"), FinancialAnswerOption("23", "23 монеты"),
                        FinancialAnswerOption("10", "10 монет")), "13",
                    "Обед и ремонт стоили 5 + 8 = 13 монет. Десять монет в копилке по-прежнему наши — это не трата.",
                    sourceActionIds = source, version = 2, ledgerTask = task)
            }
            FinancialQuestionKind.SAVING_PRACTICE -> FinancialQuestion(id, kind,
                "Копим на новое снаряжение. Как постепенно собрать нужную сумму?",
                listOf(FinancialAnswerOption("repeat", "Каждый раз оставлять на нужное, а часть полученных монет сберегать"),
                    FinancialAnswerOption("circle", "Брать из копилки и сразу возвращать те же монеты"),
                    FinancialAnswerOption("wish", "Просто записать большую сумму в план")), "repeat",
                "Каждый раз оставляем на еду, а часть новых монет откладываем. Так копилка растёт.",
                sourceActionIds = source, version = 2)
        }
        return start(first, seriesId)
    }

    fun start(first: FinancialQuestion, seriesId: String): FinancialQuestion {
        require(first.series == null && first.answeredOptionId == null && first.attempts == 0)
        require(seriesId.isNotBlank())
        val variation = Math.floorMod(seriesId.hashCode(), 5).toLong()
        val builder = Questions(first.kind, seriesId, first.sourceActionIds.take(1), variation)
        val remaining = when (first.kind) {
            FinancialQuestionKind.PLAN_REVIEW -> builder.plan()
            FinancialQuestionKind.CONSEQUENCE -> builder.consequences()
            FinancialQuestionKind.TRANSACTION_ACCOUNTING -> builder.accounting()
            FinancialQuestionKind.SAVING_PRACTICE -> builder.saving()
        }
        val shift = Math.floorMod(seriesId.hashCode(), first.options.size)
        return first.copy(options = first.options.drop(shift) + first.options.take(shift),
            series = FinancialPracticeSeries(seriesId, 1, remainingQuestions = remaining))
    }

    fun advance(question: FinancialQuestion): FinancialQuestion {
        val series = requireNotNull(question.series)
        require(question.correct && !series.isLast)
        return series.remainingQuestions.first().copy(series = series.copy(
            questionNumber = series.questionNumber + 1,
            remainingQuestions = series.remainingQuestions.drop(1),
        ))
    }

    private class Questions(val kind: FinancialQuestionKind, val seriesId: String,
        val sources: List<String>, val variation: Long) {
        private var number = 1

        private fun choice(prompt: String, correct: String, wrong: String, other: String,
            explanation: String): FinancialQuestion {
            val id = "$seriesId:question:${++number}"
            val options = listOf(FinancialAnswerOption("correct", correct), FinancialAnswerOption("wrong", wrong),
                FinancialAnswerOption("other", other))
            val shift = Math.floorMod(id.hashCode(), options.size)
            return FinancialQuestion(id, kind, prompt, options.drop(shift) + options.take(shift), "correct", explanation,
                sourceActionIds = sources, version = 2)
        }

        private fun amount(prompt: String, expected: Long, wrong: Long, other: Long,
            explanation: String, task: AssessmentTask.ReadLedger? = null,
            unit: (Long) -> String = { coins(it) }): FinancialQuestion {
            val id = "$seriesId:question:${++number}"
            require(setOf(expected, wrong, other).size == 3 && minOf(expected, wrong, other) >= 0)
            val options = listOf(expected, wrong, other).map { FinancialAnswerOption(it.toString(), "$it ${unit(it)}") }
            val shift = Math.floorMod(id.hashCode(), options.size)
            return FinancialQuestion(id, kind, prompt, options.drop(shift) + options.take(shift), expected.toString(), explanation,
                sourceActionIds = sources, version = 2, ledgerTask = task)
        }

        fun plan(): List<FinancialQuestion> {
            val planned = 20 + variation * 5
            val spent = planned - 5
            val initial = 30 + variation * 5
            return listOf(
                amount("Представь: на еду оставили $planned монет и потратили $spent. Сколько осталось на еду?",
                    5, planned, planned + spent,
                    "Осталось $planned − $spent = 5 монет."),
                amount("Представь: у нас $initial монет. Заработали ещё 10. Сколько теперь можно распределить?",
                    initial + 10, initial, initial - 10,
                    "Теперь у нас $initial + 10 = ${initial + 10} монет."),
                choice("Представь: починили рюкзак и заплатили монетами из запаса. Что делаем с бюджетом?",
                    "Посмотрим, сколько осталось, и при необходимости распределим монеты заново",
                    "Вернём в бюджет прежние цифры, будто ничего не потратили",
                    "Забудем про еду и потратим всё оставшееся",
                    "После ремонта монет стало меньше. Посчитаем остаток и сначала оставим на нужное."),
            )
        }

        fun consequences(): List<FinancialQuestion> {
            val wallet = 20 + variation * 5
            val food = wallet - 5
            val price = 8L
            val reward = 5 + variation
            return listOf(
                choice("Представь: у нас $wallet монет, на еду нужно $food. Игрушка стоит $price. Что будет, если её купить?",
                    "На еду не хватит 3 монет",
                    "На еду хватит, ведь игрушку мы смогли оплатить",
                    "Монеты на еду сами появятся в копилке",
                    "Останется $wallet − $price = ${wallet - price} монет. На еду нужно $food — не хватает 3."),
                choice("За помощь в мастерской обещали $reward ${coins(reward)}. Работу ещё не сделали. Можно уже тратить награду?",
                    "Нет, сначала нужно выполнить работу и получить монеты",
                    "Да, обещанная награда уже лежит у нас",
                    "Да, копилка сама даст монеты за будущую работу",
                    "Награду получим после работы. Пока этих монет у нас нет."),
                choice("Представь: решили пройти мимо ярмарочной игры. Что произойдёт с монетами?",
                    "Они останутся у нас, и позже мы решим, на что их потратить",
                    "Они автоматически перейдут в копилку",
                    "Монет станет больше, потому что мы отказались от игры",
                    "За игру не платили, поэтому монеты остались у нас. В копилку они сами не переходят."),
            )
        }

        fun accounting(): List<FinancialQuestion> {
            val earned = 4 + variation
            val gift = 6L
            val deposit = 10 + variation
            val spending = 5L
            val withdrawal = 5 + variation
            val incomeTask = AssessmentTask.ReadLedger(20, 0, listOf(
                LedgerEntry("$seriesId:example:earned", LedgerKind.INCOME, earned),
                LedgerEntry("$seriesId:example:gift", LedgerKind.INCOME, gift),
                LedgerEntry("$seriesId:example:meal", LedgerKind.AVAILABLE_EXPENSE, spending),
            ), LedgerQuestion.INCOME, 0)
            val expenseTask = AssessmentTask.ReadLedger(30, 0, listOf(
                LedgerEntry("$seriesId:example:deposit", LedgerKind.DEPOSIT, deposit),
                LedgerEntry("$seriesId:example:purchase", LedgerKind.AVAILABLE_EXPENSE, spending),
            ), LedgerQuestion.EXPENSE, 0)
            val withdrawalTask = AssessmentTask.ReadLedger(10, 20, listOf(
                LedgerEntry("$seriesId:example:withdrawal", LedgerKind.WITHDRAWAL, withdrawal),
            ), LedgerQuestion.AVAILABLE_REMAINDER, 0)
            return listOf(
                amount("Представь: заработали $earned ${coins(earned)}, получили в подарок $gift и потратили $spending на еду. Сколько монет получили?",
                    earned + gift, earned + gift - spending, earned + gift + spending,
                    "Получили $earned + $gift = ${earned + gift} монет. На еду монеты потратили, поэтому её сюда не прибавляем.", incomeTask),
                amount("Представь: положили $deposit монет в копилку и купили обед за $spending. Сколько монет потратили на покупки?",
                    spending, deposit + spending, 0,
                    "Потратили $spending монет на обед. Монеты в копилке остались нашими.", expenseTask),
                amount("Представь: под рукой 10 монет, в копилке 20. Взяли из копилки $withdrawal ${coins(withdrawal)}. Сколько теперь под рукой?",
                    10 + withdrawal, 30, 10 - withdrawal,
                    "Под рукой стало 10 + $withdrawal = ${10 + withdrawal} монет. Мы взяли их из копилки, а не заработали.", withdrawalTask),
            )
        }

        fun saving(): List<FinancialQuestion> {
            val target = 40 + variation * 5
            val saved = 20L
            val wallet = 35 + variation * 5
            val food = wallet - 10
            val contribution = 5L
            val times = 4 + variation
            return listOf(
                amount("Представь: для цели нужно $target монет, а в копилке уже $saved. Сколько ещё осталось накопить?",
                    target - saved, target, target + saved,
                    "Осталось накопить $target − $saved = ${target - saved} монет."),
                amount("Представь: у нас $wallet монет. До следующей недели на еду нужно $food. Сколько можно отложить, чтобы на еду хватило?",
                    10, wallet, food,
                    "Оставляем $food монет на еду. В копилку можно положить $wallet − $food = 10 монет."),
                amount("Представь: осталось накопить ${times * contribution} монет. Каждый раз будем откладывать по $contribution. Сколько таких пополнений понадобится?",
                    times, times + 2, times * contribution,
                    "$times ${deposits(times)} по $contribution монет: $times × $contribution = ${times * contribution}.",
                    unit = { deposits(it) }),
            )
        }
    }

    private fun coins(amount: Long): String = when {
        amount % 100 in 11..14 -> "монет"
        amount % 10 == 1L -> "монета"
        amount % 10 in 2..4 -> "монеты"
        else -> "монет"
    }

    private fun deposits(amount: Long): String = when {
        amount % 100 in 11..14 -> "пополнений"
        amount % 10 == 1L -> "пополнение"
        amount % 10 in 2..4 -> "пополнения"
        else -> "пополнений"
    }
}
