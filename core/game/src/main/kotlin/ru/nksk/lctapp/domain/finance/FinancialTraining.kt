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
                "Представь: у тебя 40 монет. Ты отложил 25 на еду и 10 на игрушку. Сколько монет ещё не распределено?",
                listOf(FinancialAnswerOption("5", "5 монет"), FinancialAnswerOption("15", "15 монет"),
                    FinancialAnswerOption("40", "40 монет")), "5", "На еду и игрушку отложено 25 + 10 = 35 монет. Осталось распределить 40 − 35 = 5.",
                sourceActionIds = source, version = 2)
            FinancialQuestionKind.CONSEQUENCE -> FinancialQuestion(id, kind,
                "Представь: у тебя 30 монет. На еду до следующей недели нужно 25. Если заплатить 7 за игру на ярмарке, хватит ли оставшихся монет на еду?",
                listOf(FinancialAnswerOption("short", "На еду не хватит 2 монет"),
                    FinancialAnswerOption("covered", "На еду хватит, ещё и останется"),
                    FinancialAnswerOption("automatic", "Останется ровно 25 монет на еду")), "short",
                "После игры останется 30 − 7 = 23 монеты. На еду нужно 25, значит, не хватит 25 − 23 = 2.",
                sourceActionIds = source, version = 2)
            FinancialQuestionKind.TRANSACTION_ACCOUNTING -> {
                val task = AssessmentTask.ReadLedger(40, 0, listOf(
                    LedgerEntry("$seriesId:example:food", LedgerKind.AVAILABLE_EXPENSE, 5),
                    LedgerEntry("$seriesId:example:repair", LedgerKind.AVAILABLE_EXPENSE, 8),
                    LedgerEntry("$seriesId:example:saving", LedgerKind.DEPOSIT, 10),
                ), LedgerQuestion.EXPENSE, 0)
                FinancialQuestion(id, kind, "Представь: ты заплатил 5 монет за обед и 8 за ремонт рюкзака. Ещё 10 переложил в копилку. Сколько монет ушло на обед и ремонт вместе?",
                    listOf(FinancialAnswerOption("13", "13 монет"), FinancialAnswerOption("23", "23 монеты"),
                        FinancialAnswerOption("10", "10 монет")), "13",
                    "За обед и ремонт заплатили 5 + 8 = 13 монет. А 10 в копилке всё ещё твои: их никто не получил за покупку или работу.",
                    sourceActionIds = source, version = 2, ledgerTask = task)
            }
            FinancialQuestionKind.SAVING_PRACTICE -> FinancialQuestion(id, kind,
                "Представь: ты копишь на телескоп. Как добавлять монеты в копилку каждую неделю и оставлять деньги на еду?",
                listOf(FinancialAnswerOption("repeat", "Сначала оставить на еду, потом отложить часть остатка"),
                    FinancialAnswerOption("circle", "Вынимать и возвращать одни и те же монеты"),
                    FinancialAnswerOption("wish", "Только записывать желаемую сумму в план")), "repeat",
                "Сначала оставь на еду, затем положи часть остатка в копилку. Когда снова получишь деньги, повтори. Так постепенно накопишь на телескоп.",
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

    /** Rebuild only known example templates; callers still verify their frozen answer and task facts. */
    fun exampleWording(question: FinancialQuestion): FinancialQuestion? {
        if (question.version != 2) return null
        val series = question.series ?: return null
        if (series.questionNumber == 1) {
            return standalone(question.kind, series.id).takeIf {
                it.id == question.id && it.sourceActionIds == question.sourceActionIds
            }
        }
        val variation = Math.floorMod(series.id.hashCode(), 5).toLong()
        val builder = Questions(question.kind, series.id, question.sourceActionIds.take(1), variation)
        val examples = when (question.kind) {
            FinancialQuestionKind.PLAN_REVIEW -> builder.plan()
            FinancialQuestionKind.CONSEQUENCE -> builder.consequences()
            FinancialQuestionKind.TRANSACTION_ACCOUNTING -> builder.accounting()
            FinancialQuestionKind.SAVING_PRACTICE -> builder.saving()
        }
        return examples.getOrNull(series.questionNumber - 2)?.takeIf { it.id == question.id }
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
                amount("Представь: ты отложил на еду $planned монет и уже потратил $spent на обеды. Сколько осталось на еду?",
                    5, planned, planned + spent,
                    "На еду отложили $planned монет и потратили $spent. Осталось $planned − $spent = 5."),
                amount("Представь: у тебя $initial монет. Ты получил ещё 10 за помощь в мастерской и ничего не потратил. Сколько монет теперь у тебя?",
                    initial + 10, initial, initial - 10,
                    "Прибавляем заработанные 10 к тому, что было: $initial + 10 = ${initial + 10} монет."),
                choice("Представь: ты неожиданно заплатил за ремонт рюкзака. Монет стало меньше. Как теперь проверить, на что их хватит?",
                    "Посчитать остаток и обновить план расходов",
                    "Считать, что монет осталось столько же",
                    "Не учитывать ремонт в расходах",
                    "За ремонт уже заплатили — эти монеты потрачены. Сначала посчитай остаток, оставь на еду, а затем реши, сколько можно потратить на другие покупки."),
            )
        }

        fun consequences(): List<FinancialQuestion> {
            val wallet = 20 + variation * 5
            val food = wallet - 5
            val price = 8L
            val reward = 5 + variation
            return listOf(
                choice("Представь: у тебя $wallet монет. На еду нужно $food, а игрушка стоит $price. Если купить игрушку, что будет с деньгами на еду?",
                    "На еду не хватит 3 монет",
                    "Останется ровно $food монет на еду",
                    "На еду хватит, ещё и останется",
                    "После покупки останется $wallet − $price = ${wallet - price} ${coins(wallet - price)}. " +
                        "На еду нужно $food. Не хватает 3 монет: $food − ${wallet - price} = 3."),
                choice("Представь: за помощь в мастерской обещали $reward ${coins(reward)}. Ты ещё не выполнил работу. Когда можно потратить эти монеты?",
                    "После работы, когда получу награду",
                    "Сразу после обещания",
                    "Как только начну работу",
                    "Награду получим после работы. Пока этих монет у нас нет."),
                choice("Представь: у тебя $wallet монет. Ты прошёл мимо платной игры на ярмарке и ничего не купил. Что стало с монетами?",
                    "Осталось столько же: $wallet монет",
                    "Все монеты перешли в копилку",
                    "Монет стало больше",
                    "Ты ничего не оплатил и не заработал. Поэтому осталось столько же — $wallet монет. Отказ от покупки сохраняет деньги, но не добавляет новые."),
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
                amount("Представь: тебе заплатили $earned ${coins(earned)} за работу и подарили ещё $gift. Потом ты купил обед за $spending. Сколько новых монет ты получил от работы и подарка вместе?",
                    earned + gift, earned + gift - spending, earned + gift + spending,
                    "Работа и подарок принесли $earned + $gift = ${earned + gift} монет. Обед — расход. Здесь считаем полученные монеты, поэтому цену обеда не вычитаем.", incomeTask),
                amount("Представь: ты переложил $deposit монет в копилку и заплатил $spending за обед. Сколько монет ушло на покупку, а не осталось у тебя?",
                    spending, deposit + spending, 0,
                    "За обед отдали $spending монет — это расход. А $deposit в копилке всё ещё твои, поэтому к расходам их не прибавляем.", expenseTask),
                amount("Представь: с собой у тебя 10 монет, в копилке — 20. Ты взял из копилки $withdrawal ${coins(withdrawal)}. Сколько теперь монет с собой?",
                    10 + withdrawal, 30, 10 - withdrawal,
                    "К 10 монетам с собой добавились $withdrawal из копилки: 10 + $withdrawal = ${10 + withdrawal}. В копилке стало меньше, а новых денег не появилось.", withdrawalTask),
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
                amount("Представь: фонарь стоит $target монет. Ты уже накопил $saved. Сколько монет не хватает на фонарь?",
                    target - saved, target, target + saved,
                    "Из цены фонаря вычитаем накопленное: $target − $saved = ${target - saved} монет. Именно столько ещё нужно добавить."),
                amount("Представь: у тебя $wallet монет. До следующей недели на еду нужно $food. Других обязательных трат нет. Сколько можно отложить в копилку, оставив всю сумму на еду?",
                    10, wallet, food,
                    "Оставляем $food монет на еду. В копилку можно положить $wallet − $food = 10 монет."),
                amount("Представь: на фонарь не хватает ${times * contribution} монет. Ты будешь добавлять в копилку по $contribution и ничего из неё не брать. Сколько пополнений нужно?",
                    times, times + 2, times * contribution,
                    "Нужно $times ${deposits(times)} по $contribution монет: $times × $contribution = ${times * contribution}. После этого на фонарь хватит.",
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
