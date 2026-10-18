package ru.nksk.lctapp.domain.analytics

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

/** Optional display context resolved from the recorded event, never sent as a new contract field. */
data class SkillEventContext(val day: Int, val title: String)

/** Shared parent copy for observed episodes, not a local mastery assessment. */
@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class SkillExplanation(
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val version: Int = 1,
    val description: String,
    val summary: String,
    val evidence: List<SkillExplanationEvidence>,
)

@Serializable
data class SkillExplanationEvidence(
    val episodeId: String,
    val sourceEventIds: List<String>,
    val text: String,
)

/** Read-side validation shared by local and remote parent reports. */
fun SkillExplanation.isSupported(): Boolean =
    version == 1 && description.isExplanationText() && description.length in 1..400 &&
        summary.isExplanationText() && summary.length in 1..600 && evidence.size <= 2 &&
        evidence.map { it.episodeId }.distinct().size == evidence.size &&
        evidence.all { item ->
            item.episodeId.isExplanationText() && item.text.isExplanationText() && item.text.length in 1..400 &&
                item.sourceEventIds.isNotEmpty() && item.sourceEventIds.all(String::isExplanationText) &&
                item.sourceEventIds.distinct().size == item.sourceEventIds.size
        }

private fun String.isExplanationText(): Boolean = isNotBlank() && '\u0000' !in this

/** Does not alter observations, financial rules or the server's assessment policy. */
object SkillExplanations {
    fun explain(
        profile: SkillProfile,
        contextFor: (SkillObservation) -> SkillEventContext? = { null },
    ): SkillExplanation {
        val observations = profile.observations
            .filter { it.gameRunId == profile.gameRunId && it.skill == profile.skill }
            .groupBy { it.episodeId }
            .map { (_, copies) ->
                val distinct = copies.distinct()
                val first = distinct.first()
                if (distinct.size == 1 && first.ruleVersion == 1 &&
                    first.episodeId.isNotBlank() && first.sourceEventIds.any(String::isNotBlank)
                ) first else first.copy(
                    outcome = ObservationOutcome.INSUFFICIENT_DATA,
                    eligibility = ObservationEligibility.UNDETERMINED,
                    completion = EpisodeCompletion.COMPLETE,
                    reason = ObservationReason.INVALID_CONTEXT,
                    sourceEventIds = distinct.flatMap { it.sourceEventIds }.distinct(),
                    assistance = distinct.flatMap { it.assistance }.toSet(),
                    adultHelpKnown = distinct.all { it.adultHelpKnown },
                    learningContexts = distinct.flatMap { it.learningContexts }.toSet(),
                    measures = emptyMap(),
                )
            }
            .sortedBy { it.episodeId }
        val completed = observations.filter {
            it.isCompletedEvidence(ObservationOutcome.SUPPORTED) || it.isCompletedEvidence(ObservationOutcome.DIFFICULTY)
        }
        val sameCompletedReason = completed.map { it.reason to it.outcome }.distinct().size == 1
        val samePendingReason = observations.isNotEmpty() &&
            observations.all { it.completion == EpisodeCompletion.PENDING } &&
            observations.map { it.reason }.distinct().size == 1
        val additionalText = observations.associateWith { observation ->
            val summarized = if (samePendingReason) observations else completed
            val contextual = contextFor(observation)?.takeIf { it.day > 0 && it.title.isExplanationText() }?.let {
                "День ${it.day}, «${it.title}»: ${evidenceText(observation).replaceFirstChar { letter -> letter.lowercase() }}"
            }?.takeIf { it.length <= 400 }
            if (contextual != null) contextual
            else if ((samePendingReason || sameCompletedReason && observation in completed) &&
                summarized.flatMap { it.learningContexts }.toSet() == observation.learningContexts
            ) helpText(observation).takeIf(String::isNotBlank) else evidenceText(observation)
        }
        return SkillExplanation(
            description = description(profile.skill),
            summary = summary(profile.skill, observations),
            evidence = selectEvidence(observations.filter { additionalText[it] != null }, additionalText).map { observation ->
                SkillExplanationEvidence(
                    episodeId = observation.episodeId,
                    sourceEventIds = observation.sourceEventIds.filter(String::isNotBlank).distinct().sorted(),
                    text = requireNotNull(additionalText[observation]),
                )
            },
        )
    }

    private fun description(skill: SkillId): String = when (skill) {
        SkillId.COMPARE_AMOUNTS -> "Учится сравнивать суммы: где больше, меньше или поровну."
        SkillId.PLAN_BUDGET -> "Учится распределять деньги, чтобы хватало на нужное и выбранные цели."
        SkillId.PRIORITIZE_NEEDS -> "Учится проверять, останутся ли деньги на необходимое после желанной покупки."
        SkillId.MAKE_MONEY_LAST -> "Учится рассчитывать траты до следующего поступления денег."
        SkillId.SAVE_FOR_GOAL -> "Учится понемногу откладывать на цель и сохранять накопленное."
        SkillId.DELAY_PURCHASE -> "Учится откладывать желанную покупку ради более важной цели."
        SkillId.BUILD_EMERGENCY_FUND -> "Учится оставлять деньги на неожиданные расходы."
        SkillId.ADAPT_AFTER_EXPENSE -> "Учится менять план после неожиданной траты, чтобы хватило на необходимое."
        SkillId.COMPARE_COSTS -> "Учится сравнивать, сколько денег, энергии и времени потребуют разные варианты."
        SkillId.PLAN_EXTRA_INCOME -> "Учится выбирать посильное дело и планировать использование заработка."
        SkillId.RECONSIDER_DECISION -> "Учится понимать последствия выбора и учитывать их в следующем решении."
        SkillId.UNDERSTAND_INCOME_AND_EXPENSES -> "Учится различать доходы, расходы и переводы между кошельком и копилкой."
    }

    private fun summary(skill: SkillId, observations: List<SkillObservation>): String {
        val supported = observations.filter { it.isCompletedEvidence(ObservationOutcome.SUPPORTED) }
        val difficulty = observations.filter { it.isCompletedEvidence(ObservationOutcome.DIFFICULTY) }
        if (supported.isNotEmpty() && difficulty.isNotEmpty()) {
            return withLearningContext(mixedSummary(skill, supported, difficulty), supported + difficulty)
        }
        val completed = supported.ifEmpty { difficulty }
        if (completed.isNotEmpty()) {
            val single = completed.size == 1
            val result = if (supported.isNotEmpty()) positiveSummary(skill, single, supported)
                else difficultySummary(skill, single, difficulty)
            val text = if (single || result.startsWith("несколько раз ")) result.replaceFirstChar { it.uppercase() }
                else "Несколько раз $result"
            return withLearningContext(text, completed)
        }
        if (observations.isNotEmpty() && observations.all { it.completion == EpisodeCompletion.PENDING }) {
            return if (skill == SkillId.RECONSIDER_DECISION &&
                observations.any { it.reason == ObservationReason.CORRECT_EXPLANATION }
            ) withLearningContext("Причину результата объяснил верно, но применение в следующем похожем выборе пока не видно.", observations)
            else withLearningContext(pendingText(skill), observations)
        }
        return when (skill) {
            SkillId.COMPARE_AMOUNTS -> "Пока мало данных о том, как ребёнок сравнивает суммы."
            SkillId.PLAN_BUDGET -> "Пока мало данных о том, как ребёнок распределяет деньги."
            SkillId.PRIORITIZE_NEEDS -> "Пока не видно, как ребёнок выбирает между желанной покупкой и необходимым."
            SkillId.MAKE_MONEY_LAST -> "Пока не видно, хватает ли денег на необходимое до следующего дохода."
            SkillId.SAVE_FOR_GOAL -> "Пока не видно повторяющихся накоплений на цель."
            SkillId.DELAY_PURCHASE -> "Пока не видно, откладывает ли ребёнок желанную покупку ради другой цели."
            SkillId.BUILD_EMERGENCY_FUND -> "Пока не видно, готовит ли ребёнок запас для неожиданных трат."
            SkillId.ADAPT_AFTER_EXPENSE -> "Пока не видно, как ребёнок меняет решения после неожиданной траты."
            SkillId.COMPARE_COSTS -> "Пока не видно выбора, который сохранял бы деньги, энергию и время на главное."
            SkillId.PLAN_EXTRA_INCOME -> "Пока не видно, удаётся ли довести план заработка до результата."
            SkillId.RECONSIDER_DECISION -> "Пока не видно, как объяснение последствий связано со следующим выбором."
            SkillId.UNDERSTAND_INCOME_AND_EXPENSES -> "Пока мало ответов, чтобы рассказать, как ребёнок различает поступления и траты."
        }
    }

    private fun positiveSummary(skill: SkillId, single: Boolean, observations: List<SkillObservation>): String = when (skill) {
        SkillId.COMPARE_AMOUNTS -> if (single) "верно сравнил суммы." else "верно сравнивал суммы."
        SkillId.PLAN_BUDGET -> if (single) "оставил в бюджете достаточно денег на необходимое."
            else "оставлял в бюджете достаточно денег на необходимое."
        SkillId.PRIORITIZE_NEEDS -> when {
            observations.all { it.reason == ObservationReason.FUNDING_PLAN_SELECTED } ->
                if (single) "выбрал, откуда вовремя взять недостающие деньги на необходимое."
                else "выбирал, откуда вовремя взять недостающие деньги на необходимое."
            observations.all { it.reason == ObservationReason.NEEDS_COVERED } ->
                if (single) "после решения о покупке необходимое можно было оплатить."
                else "после решений о покупке сохранялась возможность оплатить необходимое."
            else -> "сохранял деньги на необходимое или выбирал, откуда вовремя взять недостающие."
        }
        SkillId.MAKE_MONEY_LAST -> "денег хватало на необходимое до следующего дохода."
        SkillId.SAVE_FOR_GOAL -> "сохранял деньги на цель после разных поступлений."
        SkillId.DELAY_PURCHASE -> if (single) "отложил желанную покупку, а позже направил деньги на выбранную цель."
            else "откладывал желанные покупки, а позже направлял деньги на выбранные цели."
        SkillId.BUILD_EMERGENCY_FUND -> when {
            observations.all { it.reason == ObservationReason.RESERVE_USED } ->
                if (single) "заранее оставленный запас помог оплатить неожиданный расход."
                else "заранее оставленный запас помогал оплачивать неожиданные расходы."
            observations.all { it.reason == ObservationReason.RESERVE_PRESERVED } ->
                if (single) "заранее оставленный запас сохранился до конца периода."
                else "заранее оставленный запас сохранялся до конца периода."
            else -> "заранее оставленный запас сохранялся или помогал с неожиданной тратой."
        }
        SkillId.ADAPT_AFTER_EXPENSE -> if (single) "после неожиданной траты сохранил возможность оплатить необходимое."
            else "после неожиданных трат сохранял возможность оплатить необходимое."
        SkillId.COMPARE_COSTS -> if (single) "выбрал вариант, который оставлял деньги, энергию и время на главное дело."
            else "выбирал варианты, с которыми оставались деньги, энергия и время на главное дело."
        SkillId.PLAN_EXTRA_INCOME -> if (single) "выполнил выбранное дело в срок, а после награды снова распорядился деньгами."
            else "выполнял выбранные дела в срок, а после награды снова распоряжался деньгами."
        SkillId.RECONSIDER_DECISION -> if (single) "верно назвал причину результата, а при следующем похожем выборе сохранил возможность сделать главное."
            else "верно называл причину результата, а при следующих похожих выборах сохранял возможность сделать главное."
        SkillId.UNDERSTAND_INCOME_AND_EXPENSES -> if (single) "верно ответил на вопросы по истории монет."
            else "верно отвечал на вопросы по истории монет."
    }

    private fun difficultySummary(skill: SkillId, single: Boolean, observations: List<SkillObservation>): String = when (skill) {
        SkillId.COMPARE_AMOUNTS -> "при сравнении сумм были ошибки."
        SkillId.PLAN_BUDGET -> when {
            observations.all { it.reason == ObservationReason.KNOWN_NEED_OMITTED } ->
                if (single) "оставил на необходимое меньше денег, чем требовалось."
                else "оставлял на необходимое меньше денег, чем требовалось."
            observations.all { it.reason == ObservationReason.INFEASIBLE_ALLOCATION } ->
                if (single) "распределил больше денег, чем было доступно."
                else "распределял больше денег, чем было доступно."
            else -> "планы не сходились с необходимыми расходами или доступной суммой."
        }
        SkillId.PRIORITIZE_NEEDS -> if (single) "необязательная покупка увеличила нехватку денег на необходимое."
            else "необязательные покупки увеличивали нехватку денег на необходимое."
        SkillId.MAKE_MONEY_LAST -> "часть необходимого осталась без оплаты до следующего дохода, хотя был доступен другой способ."
        SkillId.SAVE_FOR_GOAL -> "несколько раз отложил меньше запланированного, хотя деньги для этого были."
        SkillId.DELAY_PURCHASE -> if (single) "после смены решения неверно объяснил его связь с выбранной целью."
            else "после смены решений ошибался в объяснении их связи с выбранной целью."
        SkillId.BUILD_EMERGENCY_FUND -> if (single) "запланировал запас больше суммы, остававшейся после необходимых расходов."
            else "планировал запас больше суммы, остававшейся после необходимых расходов."
        SkillId.ADAPT_AFTER_EXPENSE -> if (single) "следующее решение после неожиданной траты увеличило нехватку денег на необходимое."
            else "следующие решения после неожиданных трат увеличивали нехватку денег на необходимое."
        SkillId.COMPARE_COSTS -> if (single) "выбрал вариант, с которым главное дело стало недоступно, хотя был другой подходящий вариант."
            else "выбирал варианты, с которыми главное дело становилось недоступно, хотя были подходящие варианты."
        SkillId.PLAN_EXTRA_INCOME -> if (single) "выбрал дело, которое нельзя было выполнить к сроку."
            else "выбирал дела, которые нельзя было выполнить к сроку."
        SkillId.RECONSIDER_DECISION -> if (single) "неверно назвал причину результата решения."
            else "ошибался в объяснении причин результата решений."
        SkillId.UNDERSTAND_INCOME_AND_EXPENSES -> "в ответах по истории монет были ошибки."
    }

    private fun mixedSummary(
        skill: SkillId,
        supported: List<SkillObservation>,
        difficulty: List<SkillObservation>,
    ): String = when (skill) {
        SkillId.COMPARE_AMOUNTS -> "В одних заданиях сравнивал суммы верно, в других ошибался."
        SkillId.PLAN_BUDGET -> "В одних планах оставлял достаточно на необходимое. В других ${difficultySummary(skill, false, difficulty)}"
        SkillId.PRIORITIZE_NEEDS -> "При выборе покупки иногда сохранял возможность оплатить необходимое, а иногда покупка увеличивала нехватку денег."
        SkillId.MAKE_MONEY_LAST -> "В одни периоды денег хватало на необходимое до следующего дохода, в другие часть расходов оставалась без оплаты."
        SkillId.SAVE_FOR_GOAL -> "В одних случаях сохранял накопления после разных поступлений, в других откладывал меньше своего плана, хотя мог его выполнить."
        SkillId.DELAY_PURCHASE -> "Иногда откладывал желанную покупку и затем направлял деньги на цель. В других случаях неверно объяснял, как смена решения влияет на цель."
        SkillId.BUILD_EMERGENCY_FUND -> "В одних случаях ${positiveSummary(skill, false, supported)} В других ${difficultySummary(skill, false, difficulty)}"
        SkillId.ADAPT_AFTER_EXPENSE -> "После одних неожиданных трат сохранял возможность оплатить необходимое, после других следующее решение увеличивало нехватку денег."
        SkillId.COMPARE_COSTS -> "В одних случаях выбирал вариант, с которым мог выполнить главное дело, в других терял эту возможность, хотя был другой подходящий вариант."
        SkillId.PLAN_EXTRA_INCOME -> "Одни планы заработка доводил до результата, а другие выбранные дела нельзя было успеть выполнить к сроку."
        SkillId.RECONSIDER_DECISION -> "Иногда верно называл причину результата и сохранял возможность сделать главное при следующем похожем выборе. В других случаях ошибался в объяснении причины."
        SkillId.UNDERSTAND_INCOME_AND_EXPENSES -> "В одних вопросах по истории монет отвечал верно, в других ошибался."
    }

    private fun selectEvidence(
        observations: List<SkillObservation>,
        text: Map<SkillObservation, String?>,
    ): List<SkillObservation> {
        val linked = observations.filter { it.episodeId.isNotBlank() && it.sourceEventIds.any(String::isNotBlank) }
        val supported = linked.firstOrNull { it.isCompletedEvidence(ObservationOutcome.SUPPORTED) }
        val difficulty = linked.firstOrNull { it.isCompletedEvidence(ObservationOutcome.DIFFICULTY) }
        // Mixed evidence must not become a success-only or difficulty-only story.
        if (supported != null && difficulty != null) return listOf(supported, difficulty)
        val ordered = linked.sortedWith(compareBy<SkillObservation> {
            when {
                it.isCompletedEvidence(ObservationOutcome.SUPPORTED) ||
                    it.isCompletedEvidence(ObservationOutcome.DIFFICULTY) -> 0
                it.completion == EpisodeCompletion.COMPLETE && it.outcome == ObservationOutcome.NEUTRAL -> 1
                it.completion == EpisodeCompletion.PENDING -> 2
                else -> 3
            }
        }.thenBy { it.episodeId })
        val first = ordered.firstOrNull() ?: return emptyList()
        val firstText = text[first]
        val distinctText = ordered.drop(1).filter { text[it] != firstText }
        val second = distinctText.firstOrNull { it.reason != first.reason } ?: distinctText.firstOrNull()
        return listOfNotNull(first, second)
    }

    private fun SkillObservation.isCompletedEvidence(outcome: ObservationOutcome): Boolean =
        completion == EpisodeCompletion.COMPLETE && eligibility == ObservationEligibility.ELIGIBLE &&
            this.outcome == outcome

    private fun withLearningContext(text: String, observations: List<SkillObservation>): String {
        val contexts = observations.flatMap { it.learningContexts }.toSet()
        val prefix = when {
            contexts == setOf(LearningContext.TRAINING) -> if (observations.size == 1) "На тренировке " else "На тренировках "
            contexts == setOf(LearningContext.COUNTERFACTUAL) -> "При разборе другого хода событий "
            contexts.isNotEmpty() && LearningContext.GAME !in contexts -> "В тренировке и разборе других решений: "
            else -> return text
        }
        return prefix + text.replaceFirstChar { it.lowercase() }
    }

    private fun evidenceText(observation: SkillObservation): String {
        val core = when {
            observation.completion == EpisodeCompletion.PENDING &&
                observation.reason != ObservationReason.CORRECT_EXPLANATION -> pendingText(observation.skill)
            observation.outcome in setOf(ObservationOutcome.SUPPORTED, ObservationOutcome.DIFFICULTY) &&
                observation.eligibility != ObservationEligibility.ELIGIBLE ->
                "По этой ситуации пока нельзя сказать, как ребёнок сделал выбор."
            else -> reasonText(observation)
        }
        val contextual = when {
            observation.learningContexts == setOf(LearningContext.COUNTERFACTUAL) ->
                "В разборе другого хода событий: ${core.replaceFirstChar { it.lowercase() }}"
            observation.learningContexts == setOf(LearningContext.TRAINING) ->
                "В тренировке: ${core.replaceFirstChar { it.lowercase() }}"
            else -> core
        }
        val help = helpText(observation)
        return if (help.isBlank()) contextual else "$contextual $help"
    }

    private fun helpText(observation: SkillObservation): String {
        val help = buildList {
            if (Assistance.ANSWER_REVEALED in observation.assistance) add("правильный ответ был показан")
            else if (observation.assistance.any { it == Assistance.HINT || it == Assistance.WORKED_EXAMPLE }) {
                add("в игре была подсказка")
            }
            if (Assistance.ADULT_REPORTED in observation.assistance) add("отмечена помощь взрослого")
        }
        // No hints recorded does not establish absence of adult help or an independent decision.
        return if (help.isEmpty()) "" else "${help.joinToString(", ").replaceFirstChar { it.uppercase() }}."
    }

    private fun reasonText(observation: SkillObservation): String = with(observation) {
        when (reason) {
            ObservationReason.CONTEXT_MISSING -> "Пока неизвестны все условия, в которых ребёнок делал выбор."
            ObservationReason.PRESENTATION_MISSING ->
                "Неизвестно, видел ли ребёнок нужную информацию перед выбором."
            ObservationReason.INVALID_CONTEXT ->
                "Эту ситуацию пока не получается надёжно разобрать."
            ObservationReason.EPISODE_PENDING -> pendingText(skill)
            ObservationReason.CORRECT_COMPARISON -> "В предложенных заданиях верно сравнил суммы с первой попытки."
            ObservationReason.INCORRECT_COMPARISON -> "Не все суммы сравнил верно с первой попытки."
            ObservationReason.VALID_PLAN ->
                "Оставил в бюджете достаточно денег на необходимое."
            ObservationReason.KNOWN_NEED_OMITTED ->
                "Оставил на необходимое меньше денег, чем требовалось, хотя мог оставить достаточно."
            ObservationReason.ORIGINAL_SHORTFALL -> when (skill) {
                SkillId.PLAN_BUDGET -> "Денег на необходимое не хватало ещё до распределения."
                SkillId.MAKE_MONEY_LAST ->
                    "За этот период не видно оплаты всего необходимого, но это само по себе не говорит об ошибке ребёнка."
                SkillId.SAVE_FOR_GOAL ->
                    "После пополнения копилки нельзя уверенно сказать, хватало ли денег на необходимое."
                SkillId.BUILD_EMERGENCY_FUND -> "Денег на необходимое не хватало ещё до выбора запаса."
                SkillId.ADAPT_AFTER_EXPENSE ->
                    "После неожиданной траты и следующего выбора на необходимое оставалось мало денег, но это ещё не доказывает ошибку ребёнка."
                else -> "Нехватку на необходимое нельзя уверенно связать с решением о покупке."
            }
            ObservationReason.INFEASIBLE_ALLOCATION -> "Распределил больше денег, чем было доступно."
            ObservationReason.UNKNOWN_REVISION_CAUSE -> when (skill) {
                SkillId.PLAN_BUDGET ->
                    "На необходимое оставил меньше его стоимости, но одного этого недостаточно, чтобы назвать выбор ошибкой."
                SkillId.DELAY_PURCHASE -> "Позже изменил решение, но причина этого изменения неизвестна."
                SkillId.BUILD_EMERGENCY_FUND -> "Часть запаса не сохранилась, но куда она ушла, пока неясно."
                else -> "Пока неясно, почему распорядился деньгами иначе."
            }
            ObservationReason.NEEDS_COVERED ->
                "После решения о покупке денег на необходимое хватало."
            ObservationReason.AVOIDABLE_NEEDS_GAP -> when (skill) {
                SkillId.MAKE_MONEY_LAST ->
                    "До следующего дохода часть необходимого осталась без оплаты, хотя был показан доступный способ этого избежать."
                else -> "Необязательная покупка увеличила нехватку денег на необходимое, хотя был другой доступный вариант."
            }
            ObservationReason.FUNDING_PLAN_SELECTED ->
                "Выбрал, откуда вовремя взять недостающие деньги на необходимое."
            ObservationReason.NO_AVAILABLE_CHOICE -> when (skill) {
                SkillId.PLAN_EXTRA_INCOME -> "Дело было завершено позже указанного срока."
                else -> "Неясно, был ли у ребёнка доступный выбор."
            }
            ObservationReason.INTERVAL_COVERED ->
                "Между поступлениями денег смог оплатить необходимое."
            ObservationReason.NO_MANAGED_DECISION ->
                "В записи нет денежных решений ребёнка, по которым можно оценить этот период."
            ObservationReason.SAVING_PROGRESS -> "Монеты из разных поступлений удалось сохранить для цели."
            ObservationReason.SAVING_INTENTION_NOT_APPLIED ->
                "Несколько раз отложил меньше запланированного, хотя деньги для пополнения были."
            ObservationReason.CIRCULAR_TRANSFER ->
                "Из копилки забрал не меньше, чем положил, так что эти переводы не добавили накоплений."
            ObservationReason.NO_SAVING_OPPORTUNITY -> {
                if (measures["incomeWindows"] == 1L && (measures["netContribution"] ?: 0) > 0) {
                    "Монеты сохранились на цель после одного поступления, но повторения после нового поступления пока не видно."
                } else "Пока нет подходящих переводов, чтобы судить о накоплении."
            }
            ObservationReason.PRIORITY_APPLIED ->
                "Сначала отложил желанную покупку, а позже использовал монеты для выбранной цели."
            ObservationReason.PRIORITY_CONFLICT_MISUNDERSTOOD ->
                "После смены решения неверно объяснил, как оно влияет на прежнюю цель."
            ObservationReason.DESIRE_UNKNOWN ->
                "Пропустил покупку, но неизвестно, хотел ли её приобрести."
            ObservationReason.PRIORITY_UNKNOWN ->
                "В игре не записано, что было важнее при этом выборе."
            ObservationReason.RESERVE_PRESERVED -> "Заранее оставленный запас сохранился до конца периода."
            ObservationReason.RESERVE_USED -> "Заранее оставленный запас помог оплатить неожиданный расход."
            ObservationReason.RESERVE_OVERSTATED ->
                "Запланировал запас больше той суммы, которая оставалась после необходимых расходов."
            ObservationReason.NO_RESERVE_INTENTION ->
                "В этом плане запас на неожиданное не был предусмотрен."
            ObservationReason.RECOVERY_COMPLETED ->
                "После неожиданной траты следующее решение оставляло возможность оплатить необходимое."
            ObservationReason.GAP_WORSENED ->
                "После следующего выбора на необходимое стало не хватать ещё больше денег, хотя был другой доступный вариант."
            ObservationReason.NO_EXTERNAL_EXPENSE ->
                "Здесь нет неожиданной траты, чтобы увидеть реакцию на неё."
            ObservationReason.CHANGE_NOT_UNEXPECTED ->
                "Об этой трате было известно заранее, поэтому она не показывает реакцию на неожиданность."
            ObservationReason.PRIORITY_RESOURCE_PRESERVED ->
                "Выбрал вариант, после которого мог выполнить главное дело, сохранив нужные деньги, энергию и время."
            ObservationReason.PRIORITY_RESOURCE_LOST ->
                "Выбранный вариант мешал выполнить главное дело, хотя другой доступный вариант позволял его сделать."
            ObservationReason.NO_RANKED_ALTERNATIVE -> when (skill) {
                SkillId.RECONSIDER_DECISION ->
                    "Следующий похожий выбор пока не подтверждает, что объяснение получилось применить."
                else -> "По этим вариантам нельзя сказать, какой лучше сохранял возможность сделать главное."
            }
            ObservationReason.EARNING_PLAN_COMPLETED ->
                "Выполнил дело в срок и затем вернулся к решению, как распорядиться деньгами."
            ObservationReason.EARNING_PLAN_INFEASIBLE ->
                "Выбрал дело, которое нельзя было успеть выполнить к сроку."
            ObservationReason.CORRECT_EXPLANATION ->
                "Верно назвал причину результата, но пока не видно, что произошло при следующем похожем выборе."
            ObservationReason.INCORRECT_EXPLANATION ->
                "Неверно назвал причину, по которой решение привело к такому результату."
            ObservationReason.EXPLANATION_APPLIED ->
                "Верно назвал причину результата, а в следующей похожей ситуации сохранил возможность сделать главное."
            ObservationReason.CORRECT_LEDGER_ANSWER -> "В заданиях по истории монет верно ответил с первой попытки."
            ObservationReason.INCORRECT_LEDGER_ANSWER -> "Не на все вопросы по истории монет ответил верно с первой попытки."
        }
    }

    private fun pendingText(skill: SkillId): String = when (skill) {
        SkillId.SAVE_FOR_GOAL -> "Накопление ещё продолжается, поэтому пока рано говорить о его результате."
        SkillId.BUILD_EMERGENCY_FUND -> "Пока рано судить, сохранился ли запас или пригодился для неожиданной траты."
        SkillId.MAKE_MONEY_LAST -> "Следующее поступление ещё впереди, поэтому пока неизвестно, хватит ли денег на необходимое."
        SkillId.DELAY_PURCHASE -> "Покупка отложена, но пока не видно, пошли ли деньги на выбранную цель."
        SkillId.PLAN_EXTRA_INCOME -> "Пока не видно всего пути от выбора дела до дальнейшего решения о заработанных монетах."
        SkillId.RECONSIDER_DECISION -> "Пока нет следующего похожего выбора, чтобы посмотреть, как применилось объяснение."
        SkillId.COMPARE_AMOUNTS, SkillId.UNDERSTAND_INCOME_AND_EXPENSES -> "Серия вопросов ещё не завершена."
        SkillId.PLAN_BUDGET, SkillId.PRIORITIZE_NEEDS, SkillId.ADAPT_AFTER_EXPENSE,
        SkillId.COMPARE_COSTS -> "Ситуация ещё не завершена."
    }
}
