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
        SkillId.COMPARE_AMOUNTS -> "Учится определять, где денег больше, меньше или поровну."
        SkillId.PLAN_BUDGET -> "Учится заранее делить деньги между нужными покупками, желаниями и накоплениями."
        SkillId.PRIORITIZE_NEEDS -> "Учится оставлять деньги на необходимое, когда хочется что-нибудь купить."
        SkillId.MAKE_MONEY_LAST -> "Учится тратить так, чтобы денег хватило до следующего дохода."
        SkillId.SAVE_FOR_GOAL -> "Учится регулярно откладывать на цель и не тратить накопленное."
        SkillId.DELAY_PURCHASE -> "Учится подождать с желанной покупкой ради того, что сейчас важнее."
        SkillId.BUILD_EMERGENCY_FUND -> "Учится оставлять запас денег на случай неожиданной траты."
        SkillId.ADAPT_AFTER_EXPENSE -> "Учится заново распределять деньги, когда появляется неожиданный расход."
        SkillId.COMPARE_COSTS -> "Учится учитывать не только цену, но и то, сколько сил и времени потребует дело."
        SkillId.PLAN_EXTRA_INCOME -> "Учится выбирать дело, которое успеет выполнить, и решать, на что пойдёт заработок."
        SkillId.RECONSIDER_DECISION -> "Учится разбирать последствия своих решений и учитывать этот опыт в следующий раз."
        SkillId.UNDERSTAND_INCOME_AND_EXPENSES -> "Учится понимать, откуда приходят деньги, на что уходят и чем перевод в копилку отличается от траты."
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
            ) withLearningContext("Верно объяснил, почему так получилось. Пока неизвестно, учтёт ли это в следующем похожем выборе.", observations)
            else withLearningContext(pendingText(skill), observations)
        }
        return when (skill) {
            SkillId.COMPARE_AMOUNTS -> "Пока мало ответов, чтобы понять, как ребёнок сравнивает суммы."
            SkillId.PLAN_BUDGET -> "Пока мало решений о деньгах, чтобы увидеть, как ребёнок их распределяет."
            SkillId.PRIORITIZE_NEEDS -> "Пока мало покупок, где видно, оставляет ли ребёнок деньги на необходимое."
            SkillId.MAKE_MONEY_LAST -> "Пока неизвестно, хватает ли денег на необходимое до следующего дохода."
            SkillId.SAVE_FOR_GOAL -> "Пока мало пополнений копилки, чтобы судить о накоплении."
            SkillId.DELAY_PURCHASE -> "Пока не было выбора «подождать ради цели или купить сейчас» — судить рано."
            SkillId.BUILD_EMERGENCY_FUND -> "Пока мало случаев с запасом — не видно, оставляет ли его ребёнок и как тратит."
            SkillId.ADAPT_AFTER_EXPENSE -> "Пока мало решений после неожиданных расходов."
            SkillId.COMPARE_COSTS -> "Пока мало выборов, где видно, взвешивает ли ребёнок цену вместе со силами и временем."
            SkillId.PLAN_EXTRA_INCOME -> "Пока неизвестно, удалось ли выполнить выбранное дело и распорядиться заработком."
            SkillId.RECONSIDER_DECISION -> "Пока мало похожих выборов подряд, чтобы увидеть, использует ли ребёнок прошлый опыт."
            SkillId.UNDERSTAND_INCOME_AND_EXPENSES -> "Пока мало ответов о доходах, расходах и переводах в копилку."
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
                if (single) "при выборе покупки оставил достаточно денег на необходимое."
                else "при выборе покупок оставлял достаточно денег на необходимое."
            else -> "оставлял деньги на необходимое или находил, откуда вовремя взять недостающие."
        }
        SkillId.MAKE_MONEY_LAST -> "денег хватало на необходимое до следующего дохода."
        SkillId.SAVE_FOR_GOAL -> "откладывал из разных поступлений и сохранял эти деньги для цели."
        SkillId.DELAY_PURCHASE -> if (single) "отложил желанную покупку, а позже направил деньги на выбранную цель."
            else "откладывал желанные покупки, а позже направлял деньги на выбранные цели."
        SkillId.BUILD_EMERGENCY_FUND -> when {
            observations.all { it.reason == ObservationReason.RESERVE_USED } ->
                if (single) "заранее оставленный запас помог оплатить неожиданный расход."
                else "заранее оставленный запас помогал оплачивать неожиданные расходы."
            observations.all { it.reason == ObservationReason.RESERVE_PRESERVED } ->
                if (single) "сохранил заранее оставленный запас."
                else "сохранял заранее оставленный запас."
            else -> "сохранял запас, который при необходимости помогал оплатить неожиданные расходы."
        }
        SkillId.ADAPT_AFTER_EXPENSE -> if (single) "после неожиданного расхода выбрал так, что необходимое всё ещё можно было оплатить."
            else "после неожиданных расходов выбирал так, что необходимое всё ещё можно было оплатить."
        SkillId.COMPARE_COSTS -> if (single) "выбрал вариант, после которого хватало денег, сил и времени на главное дело."
            else "выбирал варианты, после которых хватало денег, сил и времени на главное дело."
        SkillId.PLAN_EXTRA_INCOME -> if (single) "выполнил дело в срок и решил, как использовать заработанные деньги."
            else "выполнял дела в срок и решал, как использовать заработанные деньги."
        SkillId.RECONSIDER_DECISION -> if (single) "верно объяснил последствия решения и учёл их в следующем похожем выборе."
            else "верно объяснял последствия решений и учитывал их в следующих похожих выборах."
        SkillId.UNDERSTAND_INCOME_AND_EXPENSES -> if (single) "верно ответил на вопросы по истории монет."
            else "верно отвечал на вопросы по истории монет."
    }

    private fun difficultySummary(skill: SkillId, single: Boolean, observations: List<SkillObservation>): String = when (skill) {
        SkillId.COMPARE_AMOUNTS -> "ошибался при сравнении сумм."
        SkillId.PLAN_BUDGET -> when {
            observations.all { it.reason == ObservationReason.KNOWN_NEED_OMITTED } ->
                if (single) "оставил на необходимое меньше денег, чем требовалось."
                else "оставлял на необходимое меньше денег, чем требовалось."
            observations.all { it.reason == ObservationReason.INFEASIBLE_ALLOCATION } ->
                if (single) "распределил больше денег, чем было доступно."
                else "распределял больше денег, чем было доступно."
            else -> "распределял деньги так, что не хватало на необходимое или траты превышали доступную сумму."
        }
        SkillId.PRIORITIZE_NEEDS -> if (single) "необязательная покупка увеличила нехватку денег на необходимое."
            else "необязательные покупки увеличивали нехватку денег на необходимое."
        SkillId.MAKE_MONEY_LAST -> "не оплатил часть необходимого до следующего дохода, хотя мог этого избежать."
        SkillId.SAVE_FOR_GOAL -> "несколько раз отложил меньше запланированного, хотя деньги для этого были."
        SkillId.DELAY_PURCHASE -> if (single) "изменил решение о покупке, но неверно объяснил, как это повлияет на накопление."
            else "менял решения о покупках, но ошибался в объяснении того, как это повлияет на накопление."
        SkillId.BUILD_EMERGENCY_FUND -> if (single) "запланировал запас больше суммы, остававшейся после необходимых расходов."
            else "планировал запас больше суммы, остававшейся после необходимых расходов."
        SkillId.ADAPT_AFTER_EXPENSE -> if (single) "следующее решение после неожиданной траты увеличило нехватку денег на необходимое."
            else "следующие решения после неожиданных трат увеличивали нехватку денег на необходимое."
        SkillId.COMPARE_COSTS -> if (single) "выбрал вариант, после которого уже не мог выполнить главное дело. Другой доступный вариант позволял это сделать."
            else "выбирал варианты, после которых уже не мог выполнить главное дело, хотя были другие подходящие варианты."
        SkillId.PLAN_EXTRA_INCOME -> if (single) "выбрал дело, которое нельзя было выполнить к сроку."
            else "выбирал дела, которые нельзя было выполнить к сроку."
        SkillId.RECONSIDER_DECISION -> if (single) "неверно объяснил, почему решение привело к такому результату."
            else "ошибался, объясняя последствия своих решений."
        SkillId.UNDERSTAND_INCOME_AND_EXPENSES -> "ошибался в вопросах о том, откуда пришли и куда ушли деньги."
    }

    private fun mixedSummary(
        skill: SkillId,
        supported: List<SkillObservation>,
        difficulty: List<SkillObservation>,
    ): String = when (skill) {
        SkillId.COMPARE_AMOUNTS -> "Не всегда сравнивает суммы верно: есть и правильные ответы, и ошибки."
        SkillId.PLAN_BUDGET -> "Не всегда удаётся составить подходящий план. В одних случаях денег на необходимое хватало, в других ${difficultySummary(skill, false, difficulty)}"
        SkillId.PRIORITIZE_NEEDS -> "Не всегда оставляет достаточно на необходимое: иногда желанная покупка увеличивает нехватку денег. В других случаях нужные деньги удавалось сохранить."
        SkillId.MAKE_MONEY_LAST -> "Не всегда удаётся оплатить необходимое до следующего дохода. Есть и удачные примеры, и случаи, когда часть расходов оставалась без оплаты."
        SkillId.SAVE_FOR_GOAL -> "Иногда сохраняет деньги на цель, а иногда откладывает меньше, чем планировал, хотя денег для этого хватает."
        SkillId.DELAY_PURCHASE -> "Иногда откладывает покупку ради цели. Но не всегда верно объясняет, как новое решение о покупке повлияет на накопление."
        SkillId.BUILD_EMERGENCY_FUND -> "В одних случаях ${positiveSummary(skill, false, supported)} В других ${difficultySummary(skill, false, difficulty)}"
        SkillId.ADAPT_AFTER_EXPENSE -> "После неожиданного расхода не всегда удаётся выбрать удачно: одни решения сохраняли деньги на необходимое, другие увеличивали нехватку."
        SkillId.COMPARE_COSTS -> "Не всегда учитывает, хватит ли денег, сил и времени на главное дело. В одних случаях удавалось их сохранить, в других выбранный вариант мешал выполнить дело."
        SkillId.PLAN_EXTRA_INCOME -> "Не всегда выбирает посильное дело. Одни дела завершал в срок, а для других уже при выборе не хватало сил или времени."
        SkillId.RECONSIDER_DECISION -> "Иногда верно объясняет последствия решения и учитывает их в следующий раз. В других случаях ошибается в объяснении причин."
        SkillId.UNDERSTAND_INCOME_AND_EXPENSES -> "Не всегда верно отвечает, откуда пришли и куда ушли деньги: есть и правильные ответы, и ошибки."
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
            contexts == setOf(LearningContext.COUNTERFACTUAL) -> "При разборе другого выбора "
            contexts.isNotEmpty() && LearningContext.GAME !in contexts -> "В тренировках и при разборе других решений: "
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
                "Об этом выборе пока слишком мало известно, чтобы делать вывод."
            else -> reasonText(observation)
        }
        val contextual = when {
            observation.learningContexts == setOf(LearningContext.COUNTERFACTUAL) ->
                "При разборе другого выбора: ${core.replaceFirstChar { it.lowercase() }}"
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
            if (Assistance.ADULT_REPORTED in observation.assistance) add("указана помощь взрослого")
        }
        // No hints recorded does not establish absence of adult help or an independent decision.
        // One sentence: evidence text is limited to two dots in total.
        return if (help.isEmpty()) "" else "${help.joinToString("; ").replaceFirstChar { it.uppercase() }}."
    }

    private fun reasonText(observation: SkillObservation): String = with(observation) {
        when (reason) {
            ObservationReason.CONTEXT_MISSING -> "Мало видно, как именно шёл этот выбор, поэтому вывода пока нет."
            ObservationReason.PRESENTATION_MISSING ->
                "Неизвестно, видел ли ребёнок нужную информацию перед выбором."
            ObservationReason.INVALID_CONTEXT ->
                "По записи об этом выборе пока нельзя сделать вывод."
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
                    "Не всё необходимое было оплачено. Пока неизвестно, мог ли ребёнок этого избежать."
                SkillId.SAVE_FOR_GOAL ->
                    "Ребёнок пополнил копилку, но пока неизвестно, хватало ли оставшихся денег на необходимое."
                SkillId.BUILD_EMERGENCY_FUND -> "Денег на необходимое не хватало ещё до выбора запаса."
                SkillId.ADAPT_AFTER_EXPENSE ->
                    "После неожиданной траты денег по-прежнему не хватало. Пока неизвестно, мог ли ребёнок исправить это своим выбором."
                else -> "На необходимое не хватало денег, но пока нельзя сказать, что причиной стала эта покупка."
            }
            ObservationReason.INFEASIBLE_ALLOCATION -> "Распределил больше денег, чем было доступно."
            ObservationReason.UNKNOWN_REVISION_CAUSE -> when (skill) {
                SkillId.PLAN_BUDGET ->
                    "На необходимое оставил меньше, чем требовалось. Почему план изменился, пока неизвестно."
                SkillId.DELAY_PURCHASE -> "Позже изменил решение, но причина этого изменения неизвестна."
                SkillId.BUILD_EMERGENCY_FUND -> "Часть запаса не сохранилась, но куда она ушла, пока неясно."
                else -> "Пока неясно, почему распорядился деньгами иначе."
            }
            ObservationReason.NEEDS_COVERED ->
                "После решения о покупке денег на необходимое хватало."
            ObservationReason.AVOIDABLE_NEEDS_GAP -> when (skill) {
                SkillId.MAKE_MONEY_LAST ->
                    "Не оплатил часть необходимого до следующего дохода, хотя игра показывала, как этого избежать."
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
                "За это время в истории нет денежных решений ребёнка. Пока делать вывод рано."
            ObservationReason.SAVING_PROGRESS -> "Откладывал из разных поступлений и сохранил эти деньги для цели."
            ObservationReason.SAVING_INTENTION_NOT_APPLIED ->
                "Несколько раз отложил меньше запланированного, хотя деньги для пополнения были."
            ObservationReason.CIRCULAR_TRANSFER ->
                "Забрал из копилки столько же или больше, чем положил. Накопления от этих переводов не выросли."
            ObservationReason.NO_SAVING_OPPORTUNITY -> {
                if (measures["incomeWindows"] == 1L && (measures["netContribution"] ?: 0) > 0) {
                    "Сохранил деньги на цель после одного поступления. Пока рано говорить, что это стало привычкой."
                } else "Пока мало примеров пополнения копилки, чтобы судить о накоплении."
            }
            ObservationReason.PRIORITY_APPLIED ->
                "Сначала отложил желанную покупку, а позже использовал монеты для выбранной цели."
            ObservationReason.PRIORITY_CONFLICT_MISUNDERSTOOD ->
                "Изменил решение о покупке, но неверно объяснил, как это повлияет на выбранную цель."
            ObservationReason.DESIRE_UNKNOWN ->
                "Пропустил покупку, но неизвестно, хотел ли её приобрести."
            ObservationReason.PRIORITY_UNKNOWN ->
                "Пока неизвестно, что ребёнок хотел сохранить или на что собирался потратить деньги."
            ObservationReason.RESERVE_PRESERVED -> "Сохранил заранее оставленный запас."
            ObservationReason.RESERVE_USED -> "Заранее оставленный запас помог оплатить неожиданный расход."
            ObservationReason.RESERVE_OVERSTATED ->
                "На запас выделил больше, чем оставалось после необходимых расходов."
            ObservationReason.NO_RESERVE_INTENTION ->
                "При распределении денег не оставил запас на неожиданную трату."
            ObservationReason.RECOVERY_COMPLETED ->
                "После неожиданного расхода выбрал так, что необходимое всё ещё можно было оплатить."
            ObservationReason.GAP_WORSENED ->
                "После следующего решения нехватка денег на необходимое выросла. Другой доступный вариант позволял этого избежать."
            ObservationReason.NO_EXTERNAL_EXPENSE ->
                "Неожиданных расходов не было, поэтому пока нельзя судить, как ребёнок с ними справится."
            ObservationReason.CHANGE_NOT_UNEXPECTED ->
                "Об этом расходе было известно заранее. Он не показывает, как ребёнок справляется с неожиданными тратами."
            ObservationReason.PRIORITY_RESOURCE_PRESERVED ->
                "Выбрал вариант, после которого хватало денег, сил и времени на главное дело."
            ObservationReason.PRIORITY_RESOURCE_LOST ->
                "После этого выбора главное дело стало недоступно. Другой вариант позволял его выполнить."
            ObservationReason.NO_RANKED_ALTERNATIVE -> when (skill) {
                SkillId.RECONSIDER_DECISION ->
                    "Пока нельзя понять, учёл ли ребёнок прошлый опыт в следующем похожем выборе."
                else -> "Пока нельзя определить, какой вариант лучше помогал выполнить главное дело."
            }
            ObservationReason.EARNING_PLAN_COMPLETED ->
                "Выполнил дело в срок и решил, как использовать заработанные деньги."
            ObservationReason.EARNING_PLAN_INFEASIBLE ->
                "Выбрал дело, которое нельзя было успеть выполнить к сроку."
            ObservationReason.CORRECT_EXPLANATION ->
                "Верно объяснил, почему так получилось. Пока неизвестно, учтёт ли это в следующем похожем выборе."
            ObservationReason.INCORRECT_EXPLANATION ->
                "Неверно объяснил, почему решение привело к такому результату."
            ObservationReason.EXPLANATION_APPLIED ->
                "Верно объяснил последствия решения и учёл их в следующем похожем выборе."
            ObservationReason.CORRECT_LEDGER_ANSWER -> "С первой попытки верно ответил, откуда пришли и куда ушли деньги."
            ObservationReason.INCORRECT_LEDGER_ANSWER -> "С первой попытки не на все вопросы о доходах, расходах и переводах ответил верно."
        }
    }

    private fun pendingText(skill: SkillId): String = when (skill) {
        SkillId.SAVE_FOR_GOAL -> "Накопление ещё продолжается. Пока рано подводить итог."
        SkillId.BUILD_EMERGENCY_FUND -> "Запас оставлен. Пока неизвестно, сохранится ли он или понадобится на неожиданный расход."
        SkillId.MAKE_MONEY_LAST -> "Следующий доход ещё впереди. Пока неизвестно, хватит ли денег на необходимое."
        SkillId.DELAY_PURCHASE -> "Покупка отложена. Пока неизвестно, пойдут ли эти деньги на выбранную цель."
        SkillId.PLAN_EXTRA_INCOME -> "Пока неизвестно, удалось ли выполнить дело и решить, куда направить заработок."
        SkillId.RECONSIDER_DECISION -> "Пока нет следующего похожего выбора, чтобы увидеть, учтён ли прошлый опыт."
        SkillId.COMPARE_AMOUNTS, SkillId.UNDERSTAND_INCOME_AND_EXPENSES -> "Серия вопросов ещё не завершена."
        SkillId.PLAN_BUDGET, SkillId.PRIORITIZE_NEEDS, SkillId.ADAPT_AFTER_EXPENSE,
        SkillId.COMPARE_COSTS -> "Ситуация ещё не завершена."
    }
}
