package ru.nksk.lctapp.domain.analytics

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillExplanationsTest {
    @Test fun everySkillHasDistinctShortDescriptionWithoutInventingObservations() {
        val explanations = SkillId.entries.map { skill ->
            SkillExplanations.explain(SkillProfile("run", skill, emptyList()))
        }
        assertEquals(12, explanations.map { it.description }.distinct().size)
        assertEquals(12, explanations.map { it.summary }.distinct().size)
        explanations.forEach {
            assertTrue(it.description.startsWith("Учится "))
            assertTrue(it.description.length < 140)
            assertTrue(it.summary.startsWith("Пока "))
            assertFalse(it.summary.contains("не умеет"))
            assertTrue(it.evidence.isEmpty())
        }
    }

    @Test fun mixedEpisodesKeepSuccessAndDifficultyWithOriginalSourcesRegardlessOfInputOrder() {
        val positive = observation("a", reason = ObservationReason.CORRECT_COMPARISON)
        val anotherPositive = positive.copy(episodeId = "b", sourceEventIds = listOf("b1"))
        val difficulty = observation("z", outcome = ObservationOutcome.DIFFICULTY,
            reason = ObservationReason.INCORRECT_COMPARISON, sourceEventIds = listOf("z2", "z1", "z2"))
        val observations = listOf(positive, anotherPositive, difficulty, positive)
        val explanation = explain(observations)
        assertEquals(explanation, explain(observations.reversed()))
        assertEquals(listOf("a", "z"), explanation.evidence.map { it.episodeId })
        assertEquals(listOf("z1", "z2"), explanation.evidence.last().sourceEventIds)
        assertEquals("Не всегда сравнивает суммы верно: есть и правильные ответы, и ошибки.", explanation.summary)
    }

    @Test fun laterCorrectAnswerDoesNotHideDifficultyOnTheFirstAttempt() {
        val first = fact("quiz", 1, FactDetail.QuestionAnswer(
            "q1", 1, AssessmentTask.CompareAmounts(2, 5, ComparisonSide.LEFT),
        ))
        val repeated = first.copy(eventId = "retry", sequence = 2, detail = FactDetail.QuestionAnswer(
            "q1", 2, AssessmentTask.CompareAmounts(2, 5, ComparisonSide.RIGHT),
        ))
        val second = fact("quiz", 3, FactDetail.QuestionAnswer(
            "q2", 1, AssessmentTask.CompareAmounts(8, 3, ComparisonSide.LEFT),
        ))
        val profile = SkillEvaluator().project("run", listOf(first, repeated, second))
            .single { it.skill == SkillId.COMPARE_AMOUNTS }
        val explanation = SkillExplanations.explain(profile)
        assertEquals("Ошибался при сравнении сумм.", explanation.summary)
        assertTrue(explanation.evidence.isEmpty())
        assertEquals(setOf(first.eventId, repeated.eventId, second.eventId), profile.observations.single().sourceEventIds.toSet())
    }

    @Test fun oneRetainedSavingsPeriodIsNotPresentedAsRepeatedSaving() {
        val explanation = evaluated(SkillId.SAVE_FOR_GOAL, listOf(
            saving(1, "week1", SavingMovementKind.DEPOSIT, 10),
            fact("saving", 2, FactDetail.SavingCycleClosed("goal", historyComplete = true))
                .copy(actor = AnalyticsActor.SYSTEM),
        ))
        assertEquals("Пока мало пополнений копилки, чтобы судить о накоплении.", explanation.summary)
        assertTrue(explanation.evidence.single().text.contains("после одного поступления"))
        assertTrue(explanation.evidence.single().text.contains("Пока рано говорить, что это стало привычкой"))
        assertFalse(explanation.evidence.single().text.contains("не умеет"))
    }

    @Test fun savingExplanationDescribesRetainedSavingsInsteadOfListingTransfers() {
        val explanation = evaluated(SkillId.SAVE_FOR_GOAL, listOf(
            saving(1, "week1", SavingMovementKind.DEPOSIT, 10),
            saving(2, "week2", SavingMovementKind.DEPOSIT, 10),
            saving(3, "week2", SavingMovementKind.WITHDRAWAL, 5),
            fact("saving", 4, FactDetail.SavingCycleClosed("goal", historyComplete = true))
                .copy(actor = AnalyticsActor.SYSTEM),
        ))
        assertEquals("Откладывал из разных поступлений и сохранял эти деньги для цели.", explanation.summary)
        assertTrue(explanation.evidence.isEmpty())
        assertFalse(explanation.summary.any(Char::isDigit))
        assertFalse(explanation.summary.contains("самостоятель"))
    }

    @Test fun reservePaymentIsDescribedWithoutInventingASeparateChildChoice() {
        val explanation = evaluated(SkillId.BUILD_EMERGENCY_FUND, listOf(fact(
            "reserve", 1, FactDetail.ReserveDecision("reserve", 10, 6, 4, true,
                listOf(ReserveApplication("repair", 4))),
        )))
        val text = explanation.summary
        assertEquals("Заранее оставленный запас помог оплатить неожиданный расход.", explanation.summary)
        assertTrue(explanation.evidence.isEmpty())
        assertFalse(text.contains("еду") || text.contains("цель") || text.contains("копилк"))
        assertFalse(text.any(Char::isDigit))
        assertFalse(text.contains("решил"))
        assertFalse(text.contains("самостоятель"))
    }

    @Test fun earningExplanationConnectsCompletionAndLaterChoiceWithoutListingReward() {
        val explanation = evaluated(SkillId.PLAN_EXTRA_INCOME, listOf(
            fact("earning", 1, FactDetail.EarningPlan("offer", "goal", 1, 2, 1, 5, 99)),
            fact("earning", 2, FactDetail.EarningCompleted("offer", 2, 7, true))
                .copy(actor = AnalyticsActor.SYSTEM),
        ))
        assertEquals("Выполнил дело в срок и решил, как использовать заработанные деньги.",
            explanation.summary)
        assertTrue(explanation.evidence.isEmpty())
        assertFalse(explanation.summary.any(Char::isDigit))
    }

    @Test fun pendingAndIneligibleEpisodesCannotBecomeSuccessesBecauseOfTheirOutcomeField() {
        val pending = observation("pending", reason = ObservationReason.CORRECT_COMPARISON)
            .copy(completion = EpisodeCompletion.PENDING)
        val ineligible = observation("ineligible", reason = ObservationReason.CORRECT_COMPARISON)
            .copy(eligibility = ObservationEligibility.UNDETERMINED)
        val explanation = explain(listOf(pending, ineligible))
        assertEquals("Пока мало ответов, чтобы понять, как ребёнок сравнивает суммы.", explanation.summary)
        assertTrue(explanation.evidence.any { it.text == "Серия вопросов ещё не завершена." })
        assertTrue(explanation.evidence.any { it.text == "Об этом выборе пока слишком мало известно, чтобы делать вывод." })
        assertFalse(explanation.evidence.any { it.text.contains("верно сравнил") })
    }

    @Test fun conflictingCopiesDoNotArbitrarilyChooseAResultOrCountTwoEpisodes() {
        val positive = observation("same", reason = ObservationReason.CORRECT_COMPARISON)
        val conflicting = positive.copy(outcome = ObservationOutcome.DIFFICULTY,
            reason = ObservationReason.INCORRECT_COMPARISON, sourceEventIds = listOf("other-source"))
        val explanation = explain(listOf(positive, conflicting))
        assertEquals(1, explanation.evidence.size)
        assertEquals("Пока мало ответов, чтобы понять, как ребёнок сравнивает суммы.", explanation.summary)
        assertTrue(explanation.evidence.single().text.contains("пока нельзя сделать вывод"))
        assertEquals(setOf("same-source", "other-source"), explanation.evidence.single().sourceEventIds.toSet())
    }

    @Test fun foreignRunSkillAndUnlinkedEvidenceAreNotAttributedToThisChildProfile() {
        val source = observation("source", reason = ObservationReason.CORRECT_COMPARISON)
        val explanation = explain(listOf(
            source.copy(gameRunId = "another-run"),
            source.copy(skill = SkillId.PLAN_BUDGET),
            source.copy(sourceEventIds = emptyList()),
        ))
        assertTrue(explanation.evidence.isEmpty())
        assertEquals("Пока мало ответов, чтобы понять, как ребёнок сравнивает суммы.", explanation.summary)
    }

    @Test fun revealedAnswerAndReportedAdultHelpRemainVisibleWithoutAssumingIndependence() {
        val base = observation("answer", reason = ObservationReason.CORRECT_COMPARISON)
        val assisted = base.copy(assistance = setOf(Assistance.ANSWER_REVEALED, Assistance.ADULT_REPORTED))
        val assistedText = explain(listOf(assisted)).evidence.single().text
        assertTrue(assistedText.contains("Правильный ответ был показан"))
        assertTrue(assistedText.contains("помощь взрослого"))
        val unknownHelp = explain(listOf(base.copy(adultHelpKnown = false))).summary
        assertFalse(unknownHelp.contains("самостоятель"))
        assertFalse(unknownHelp.contains("без помощи"))
        assertFalse(unknownHelp.contains("взрослого"))
    }

    @Test fun correctExplanationWithoutLaterApplicationStillWaitsForEvidence() {
        val answer = fact("explanation", 1, FactDetail.QuestionAnswer(
            "why", 1, AssessmentTask.ExplainCause("expense", "expense", listOf("purchase")),
            comparisonFamily = "expense-family",
        )).copy(learningContext = LearningContext.COUNTERFACTUAL)
        val explanation = evaluated(SkillId.RECONSIDER_DECISION, listOf(answer))
        assertEquals("При разборе другого выбора верно объяснил, почему так получилось. Пока неизвестно, учтёт ли это в следующем похожем выборе.",
            explanation.summary)
        assertTrue(explanation.evidence.isEmpty())
    }

    @Test fun existingShortfallIsNotRewordedAsAChildsMistake() {
        val explanation = SkillExplanations.explain(SkillProfile("run", SkillId.PLAN_BUDGET, listOf(
            observation("budget", skill = SkillId.PLAN_BUDGET, outcome = ObservationOutcome.NEUTRAL,
                reason = ObservationReason.ORIGINAL_SHORTFALL).copy(measures = mapOf("baselineShortfall" to 21)),
        )))
        assertEquals("Денег на необходимое не хватало ещё до распределения.", explanation.evidence.single().text)
        assertEquals("Пока мало решений о деньгах, чтобы увидеть, как ребёнок их распределяет.", explanation.summary)
    }

    @Test fun unknownRuleVersionAndInvalidMeasurementsNeverInventAmounts() {
        val future = observation("future", reason = ObservationReason.CORRECT_COMPARISON).copy(ruleVersion = 99)
        assertEquals("Пока мало ответов, чтобы понять, как ребёнок сравнивает суммы.", explain(listOf(future)).summary)
        val badMeasures = observation("bad-measure", reason = ObservationReason.CORRECT_COMPARISON)
            .copy(measures = mapOf("correctFirstAnswers" to -9, "questions" to 2))
        val text = explain(listOf(badMeasures)).summary
        assertFalse(text.contains("-9"))
        assertFalse(text.contains("из 2"))
    }

    @Test fun eachReasonHasReadableBoundedCopyAndTheDtoRoundTrips() {
        ObservationReason.entries.forEach { reason ->
            val observation = observation("reason-${reason.name}", reason = reason, outcome = outcomeFor(reason))
                .copy(completion = if (reason == ObservationReason.EPISODE_PENDING ||
                    reason == ObservationReason.CORRECT_EXPLANATION) EpisodeCompletion.PENDING else EpisodeCompletion.COMPLETE)
            val explanation = explain(listOf(observation))
            val text = explanation.evidence.joinToString(" ") { it.text }.ifEmpty { explanation.summary }
            assertTrue(reason.name, text.isNotBlank() && text.length < 300)
            assertFalse(reason.name, text.contains(reason.name))
            assertFalse(reason.name, text.contains('→') || text.contains("->"))
            assertEquals(explanation, Json.decodeFromString<SkillExplanation>(Json.encodeToString(explanation)))
            assertEquals(1, explanation.version)
            assertTrue(explanation.isSupported())
        }
    }

    @Test fun wireAlwaysCarriesExplanationVersionEvenWhenDefaultFieldsAreOmitted() {
        val explanation = SkillExplanations.explain(SkillProfile("run", SkillId.PLAN_BUDGET, emptyList()))
        val json = Json { encodeDefaults = false }
        assertTrue(json.encodeToString(explanation).contains("\"version\":1"))
        assertFalse(explanation.copy(version = 0).isSupported())
        assertFalse(explanation.copy(version = 2).isSupported())
    }

    @Test fun validationBoundsCopyAndRequiresDistinctLinkedEpisodes() {
        val valid = explanationWithEvidence()
        val item = valid.evidence.single()
        assertTrue(valid.isSupported())
        assertTrue(valid.copy(description = "д".repeat(400), summary = "с".repeat(600),
            evidence = listOf(item.copy(text = "т".repeat(400)))).isSupported())
        assertFalse(valid.copy(description = " ").isSupported())
        assertFalse(valid.copy(description = "д".repeat(401)).isSupported())
        assertFalse(valid.copy(summary = "").isSupported())
        assertFalse(valid.copy(summary = "с".repeat(601)).isSupported())
        assertFalse(valid.copy(evidence = listOf(item.copy(text = "\n"))).isSupported())
        assertFalse(valid.copy(evidence = listOf(item.copy(text = "т".repeat(401)))).isSupported())
        assertFalse(valid.copy(evidence = listOf(item.copy(episodeId = " "))).isSupported())
        assertFalse(valid.copy(evidence = listOf(item, item)).isSupported())
        assertFalse(valid.copy(evidence = (1..3).map { item.copy(episodeId = "episode-$it") }).isSupported())
        assertFalse(valid.copy(evidence = listOf(item.copy(sourceEventIds = emptyList()))).isSupported())
        assertFalse(valid.copy(evidence = listOf(item.copy(sourceEventIds = listOf("source", "source")))).isSupported())
        assertFalse(valid.copy(evidence = listOf(item.copy(sourceEventIds = listOf("source", " ")))).isSupported())
    }

    @Test fun validationDoesNotLimitTheNumberOrLengthOfEvidenceSourceIds() {
        val valid = explanationWithEvidence()
        val item = valid.evidence.single().copy(
            episodeId = "long-episode-".repeat(500),
            sourceEventIds = (1..1_000).map { "source-$it-" + "a".repeat(500) },
        )
        assertTrue(valid.copy(evidence = listOf(item)).isSupported())
    }

    @Test fun validationRejectsNulInEveryExplanationString() {
        val valid = explanationWithEvidence()
        val item = valid.evidence.single()
        assertFalse(valid.copy(description = "Описание\u0000").isSupported())
        assertFalse(valid.copy(summary = "Наблюдение\u0000").isSupported())
        assertFalse(valid.copy(evidence = listOf(item.copy(text = "Пример\u0000"))).isSupported())
        assertFalse(valid.copy(evidence = listOf(item.copy(episodeId = "episode\u0000"))).isSupported())
        assertFalse(valid.copy(evidence = listOf(item.copy(sourceEventIds = listOf("source\u0000")))).isSupported())
    }

    @Test fun eachReasonWithContextAndAllHelpStillFitsTwoSentences() {
        ObservationReason.entries.forEach { reason ->
            val evidence = observation("reason-${reason.name}", reason, outcomeFor(reason)).copy(
                learningContexts = setOf(LearningContext.COUNTERFACTUAL),
                assistance = Assistance.entries.toSet(),
                completion = if (reason == ObservationReason.CORRECT_EXPLANATION ||
                    reason == ObservationReason.EPISODE_PENDING) EpisodeCompletion.PENDING else EpisodeCompletion.COMPLETE,
            )
            val explanation = explain(listOf(evidence))
            val text = explanation.evidence.single().text
            assertTrue(reason.name, text.count { it == '.' } <= 2)
            assertTrue(reason.name, explanation.summary.count { it == '.' } <= 2)
            assertTrue(reason.name, explanation.isSupported())
        }
    }

    @Test fun savingsShortfallWithMissingAfterStateDoesNotInventAnActualLackOfMoney() {
        val first = saving(1, "week1", SavingMovementKind.DEPOSIT, 10)
        val explanation = evaluated(SkillId.SAVE_FOR_GOAL, listOf(
            first.copy(context = first.context.copy(after = null)),
            saving(2, "week2", SavingMovementKind.DEPOSIT, 10),
            fact("saving", 3, FactDetail.SavingCycleClosed("goal", historyComplete = true)),
        ))
        assertTrue(explanation.evidence.single().text.contains("нельзя уверенно сказать"))
        assertFalse(explanation.evidence.single().text.contains("не хватало"))
        assertEquals("Пока мало пополнений копилки, чтобы судить о накоплении.", explanation.summary)
    }

    @Test fun partialPlannedDepositsAreNotRewordedAsNoDeposits() {
        val explanation = evaluated(SkillId.SAVE_FOR_GOAL, listOf(
            fact("saving", 1, FactDetail.SavingIntentionResolved("goal", "week1", 10, 5, true, true)),
            fact("saving", 2, FactDetail.SavingIntentionResolved("goal", "week2", 10, 5, true, true)),
            fact("saving", 3, FactDetail.SavingCycleClosed("goal", historyComplete = true)),
        ))
        assertTrue(explanation.summary.contains("меньше запланированного"))
        assertFalse(explanation.summary.contains("не были отложены"))
    }

    @Test fun unconfirmedWeeklyNeedsDoNotMeanTheChildHadTooLittleMoney() {
        val explanation = evaluated(SkillId.MAKE_MONEY_LAST, listOf(fact(
            "week", 1, FactDetail.IncomeWindow("week1", true, true, listOf("plan"), knownNeedsMet = false),
        )))
        assertTrue(explanation.evidence.single().text.contains("не видно оплаты"))
        assertFalse(explanation.evidence.single().text.contains("не хватало"))
        assertEquals("Пока неизвестно, хватает ли денег на необходимое до следующего дохода.", explanation.summary)
    }

    @Test fun missedDeadlineDoesNotInventAnEnergyShortage() {
        val explanation = evaluated(SkillId.PLAN_EXTRA_INCOME, listOf(fact(
            "earning", 1, FactDetail.EarningPlan("offer", "goal", 3, 2, 1, 5, 10),
        )))
        assertTrue(explanation.summary.contains("нельзя было выполнить к сроку"))
        assertFalse(explanation.summary.contains("энерги"))
    }

    @Test fun everySkillDescribesOneObservedChoiceWithoutTurningItIntoAHabit() {
        val positiveSummaries = mutableListOf<String>()
        val difficultySummaries = mutableListOf<String>()
        skillReasons().forEach { (skill, positiveReason, difficultyReason) ->
            listOf(positiveReason to ObservationOutcome.SUPPORTED,
                difficultyReason to ObservationOutcome.DIFFICULTY).forEach { (reason, outcome) ->
                val source = observation("one", reason, outcome, skill)
                val explanation = SkillExplanations.explain(SkillProfile("run", skill, listOf(source, source)))
                assertFalse(skill.name, explanation.summary.startsWith("В этой ситуации "))
                assertTrue(skill.name, explanation.summary.first().isUpperCase())
                assertFalse(skill.name, listOf("обычно", "чаще", "склонен", "понимает", "проверяет", "помнит")
                    .any { it in explanation.summary })
                assertTrue(skill.name, explanation.evidence.isEmpty())
                if (outcome == ObservationOutcome.SUPPORTED) positiveSummaries.add(explanation.summary)
                else difficultySummaries.add(explanation.summary)
            }
        }
        assertEquals(12, positiveSummaries.distinct().size)
        assertEquals(12, difficultySummaries.distinct().size)
    }

    @Test fun repeatedChoicesAffectTheSummaryWithoutRepeatingTheSameParagraph() {
        val first = observation("one", ObservationReason.CORRECT_COMPARISON)
        val second = first.copy(episodeId = "two", sourceEventIds = listOf("two-source"))
        val explanation = explain(listOf(first, second))
        assertEquals("Несколько раз верно сравнивал суммы.", explanation.summary)
        assertTrue(explanation.evidence.isEmpty())
    }

    @Test fun everyMixedSkillKeepsBothSidesAndLinksToDifferentEpisodes() {
        val summaries = skillReasons().map { (skill, positiveReason, difficultyReason) ->
            val observations = listOf(
                observation("positive", positiveReason, ObservationOutcome.SUPPORTED, skill),
                observation("difficulty", difficultyReason, ObservationOutcome.DIFFICULTY, skill),
            )
            val explanation = SkillExplanations.explain(SkillProfile("run", skill, observations))
            assertEquals(skill.name, listOf("positive", "difficulty"), explanation.evidence.map { it.episodeId })
            assertEquals(skill.name, 2, explanation.evidence.map { it.text }.distinct().size)
            assertTrue(skill.name, explanation.summary.count { it == '.' } <= 2)
            assertFalse(skill.name, explanation.summary.startsWith("В этой ситуации "))
            explanation.summary
        }
        assertEquals(12, summaries.distinct().size)
    }

    @Test fun additionalEvidenceDoesNotRepeatIdenticalTextFromDifferentEpisodes() {
        val first = observation("one", ObservationReason.ORIGINAL_SHORTFALL,
            ObservationOutcome.NEUTRAL, SkillId.PLAN_BUDGET)
        val second = first.copy(episodeId = "two", sourceEventIds = listOf("two-source"))
        val explanation = SkillExplanations.explain(SkillProfile("run", SkillId.PLAN_BUDGET, listOf(second, first)))
        assertEquals(listOf("one"), explanation.evidence.map { it.episodeId })
    }

    @Test fun helpRemainsAsAdditionalEvidenceWithoutRepeatingTheChoice() {
        val source = observation("one", ObservationReason.CORRECT_COMPARISON)
            .copy(assistance = setOf(Assistance.HINT))
        val explanation = explain(listOf(source))
        assertEquals("Верно сравнил суммы.", explanation.summary)
        assertEquals("В игре была подсказка.", explanation.evidence.single().text)
        assertEquals(source.sourceEventIds, explanation.evidence.single().sourceEventIds)
    }

    @Test fun trainingSuccessStaysInTrainingWhenTheRepeatedEvidenceIsRemoved() {
        val source = observation("one", ObservationReason.CORRECT_COMPARISON)
            .copy(learningContexts = setOf(LearningContext.TRAINING))
        val explanation = explain(listOf(source))
        assertEquals("На тренировке верно сравнил суммы.", explanation.summary)
        assertTrue(explanation.evidence.isEmpty())
    }

    @Test fun choosingFutureFundingDoesNotClaimThatTheExpenseWasAlreadyPaid() {
        val source = observation("funding", ObservationReason.FUNDING_PLAN_SELECTED,
            skill = SkillId.PRIORITIZE_NEEDS)
        val explanation = SkillExplanations.explain(SkillProfile("run", source.skill, listOf(source)))
        assertEquals("Выбрал, откуда вовремя взять недостающие деньги на необходимое.", explanation.summary)
        assertFalse(explanation.summary.contains("оплатил"))
        assertTrue(explanation.evidence.isEmpty())
    }

    @Test fun noReasonTurnsFinancialMeasurementsIntoALedgerForTheParent() {
        ObservationReason.entries.forEach { reason ->
            val source = observation("reason-${reason.name}", reason, outcomeFor(reason)).copy(
                measures = mapOf("actualReward" to 7, "maximumReward" to 99, "netContribution" to 15,
                    "incomeWindows" to 2, "usedForUnexpectedExpense" to 4, "remainingReserve" to 6,
                    "correctFirstAnswers" to 1, "questions" to 2, "baselineShortfall" to 21),
            )
            val explanation = explain(listOf(source))
            val visible = listOf(explanation.description, explanation.summary) + explanation.evidence.map { it.text }
            assertFalse(reason.name, visible.any { it.any(Char::isDigit) })
        }
    }

    @Test fun actualEventContextAddsAnExampleWithoutChangingTheObservationLinks() {
        val source = observation("reserve", ObservationReason.RESERVE_USED,
            skill = SkillId.BUILD_EMERGENCY_FUND, sourceEventIds = listOf("derived-reserve", "plan"))
        val profile = SkillProfile("run", source.skill, listOf(source))
        val explanation = SkillExplanations.explain(profile) { SkillEventContext(3, "Лямка не выдержала") }
        assertEquals("Заранее оставленный запас помог оплатить неожиданный расход.", explanation.summary)
        assertEquals("День 3, «Лямка не выдержала»: заранее оставленный запас помог оплатить неожиданный расход.",
            explanation.evidence.single().text)
        assertEquals("reserve", explanation.evidence.single().episodeId)
        assertEquals(listOf("derived-reserve", "plan"), explanation.evidence.single().sourceEventIds)
        assertTrue(explanation.isSupported())
    }

    @Test fun aResolvedEventDoesNotLoseTheTrainingOrHelpQualification() {
        val source = observation("training", ObservationReason.CORRECT_COMPARISON).copy(
            learningContexts = setOf(LearningContext.TRAINING), assistance = setOf(Assistance.ADULT_REPORTED),
        )
        val explanation = SkillExplanations.explain(SkillProfile("run", source.skill, listOf(source))) {
            SkillEventContext(4, "Лавка у причала")
        }
        val evidence = explanation.evidence.single().text
        assertTrue(evidence.startsWith("День 4, «Лавка у причала»: в тренировке:"))
        assertTrue(evidence.endsWith("Указана помощь взрослого."))
        assertEquals("На тренировке верно сравнил суммы.", explanation.summary)
    }

    @Test fun overlongOrInvalidContextIsOmittedWithoutTruncatingHelp() {
        val source = observation("training", ObservationReason.CORRECT_COMPARISON).copy(
            assistance = setOf(Assistance.ANSWER_REVEALED, Assistance.ADULT_REPORTED),
        )
        val profile = SkillProfile("run", source.skill, listOf(source))
        val ordinary = SkillExplanations.explain(profile)
        listOf(SkillEventContext(1, "Название".repeat(100)), SkillEventContext(0, "Событие"),
            SkillEventContext(1, " "), SkillEventContext(1, "Название\u0000")).forEach { context ->
            assertEquals(ordinary, SkillExplanations.explain(profile) { context })
        }
        assertEquals("Правильный ответ был показан; указана помощь взрослого.", ordinary.evidence.single().text)
    }

    @Test fun contextlessResultDoesNotReferToAnUnseenSituation() {
        skillReasons().forEach { (skill, reason, _) ->
            val source = observation("source", reason, skill = skill)
            val profile = SkillProfile("run", skill, listOf(source))
            val explanation = SkillExplanations.explain(profile)
            assertFalse(skill.name, explanation.summary.contains("этой ситуации") || explanation.summary.contains("этих ситуациях"))
            assertTrue(skill.name, explanation.evidence.isEmpty())
        }
    }

    @Test fun differentActualEventsRemainDifferentExamplesOfTheSameOutcome() {
        val first = observation("a", ObservationReason.RESERVE_USED, skill = SkillId.BUILD_EMERGENCY_FUND)
        val second = first.copy(episodeId = "b", sourceEventIds = listOf("b-source"))
        val explanation = SkillExplanations.explain(SkillProfile("run", first.skill, listOf(first, second))) {
            if (it.episodeId == "a") SkillEventContext(3, "Лямка не выдержала") else SkillEventContext(6, "Фонарь разбился")
        }
        assertEquals(2, explanation.evidence.size)
        assertTrue(explanation.evidence.first().text.startsWith("День 3, «Лямка не выдержала»:"))
        assertTrue(explanation.evidence.last().text.startsWith("День 6, «Фонарь разбился»:"))
    }

    private fun skillReasons() = listOf(
        Triple(SkillId.COMPARE_AMOUNTS, ObservationReason.CORRECT_COMPARISON, ObservationReason.INCORRECT_COMPARISON),
        Triple(SkillId.PLAN_BUDGET, ObservationReason.VALID_PLAN, ObservationReason.KNOWN_NEED_OMITTED),
        Triple(SkillId.PRIORITIZE_NEEDS, ObservationReason.NEEDS_COVERED, ObservationReason.AVOIDABLE_NEEDS_GAP),
        Triple(SkillId.MAKE_MONEY_LAST, ObservationReason.INTERVAL_COVERED, ObservationReason.AVOIDABLE_NEEDS_GAP),
        Triple(SkillId.SAVE_FOR_GOAL, ObservationReason.SAVING_PROGRESS, ObservationReason.SAVING_INTENTION_NOT_APPLIED),
        Triple(SkillId.DELAY_PURCHASE, ObservationReason.PRIORITY_APPLIED, ObservationReason.PRIORITY_CONFLICT_MISUNDERSTOOD),
        Triple(SkillId.BUILD_EMERGENCY_FUND, ObservationReason.RESERVE_USED, ObservationReason.RESERVE_OVERSTATED),
        Triple(SkillId.ADAPT_AFTER_EXPENSE, ObservationReason.RECOVERY_COMPLETED, ObservationReason.GAP_WORSENED),
        Triple(SkillId.COMPARE_COSTS, ObservationReason.PRIORITY_RESOURCE_PRESERVED, ObservationReason.PRIORITY_RESOURCE_LOST),
        Triple(SkillId.PLAN_EXTRA_INCOME, ObservationReason.EARNING_PLAN_COMPLETED, ObservationReason.EARNING_PLAN_INFEASIBLE),
        Triple(SkillId.RECONSIDER_DECISION, ObservationReason.EXPLANATION_APPLIED, ObservationReason.INCORRECT_EXPLANATION),
        Triple(SkillId.UNDERSTAND_INCOME_AND_EXPENSES, ObservationReason.CORRECT_LEDGER_ANSWER, ObservationReason.INCORRECT_LEDGER_ANSWER),
    )

    private fun explanationWithEvidence() = explain(listOf(observation(
        "valid", reason = ObservationReason.CONTEXT_MISSING, outcome = ObservationOutcome.INSUFFICIENT_DATA,
    )))

    private fun explain(observations: List<SkillObservation>) =
        SkillExplanations.explain(SkillProfile("run", SkillId.COMPARE_AMOUNTS, observations))

    private fun evaluated(skill: SkillId, facts: List<AnalyticsFact>) = SkillExplanations.explain(
        SkillEvaluator().project("run", facts).single { it.skill == skill },
    )

    private fun observation(
        episode: String,
        reason: ObservationReason,
        outcome: ObservationOutcome = ObservationOutcome.SUPPORTED,
        skill: SkillId = SkillId.COMPARE_AMOUNTS,
        sourceEventIds: List<String> = listOf("$episode-source"),
    ) = SkillObservation(
        gameRunId = "run", skill = skill, episodeId = episode, outcome = outcome,
        eligibility = when (outcome) {
            ObservationOutcome.SUPPORTED, ObservationOutcome.DIFFICULTY -> ObservationEligibility.ELIGIBLE
            ObservationOutcome.NEUTRAL -> ObservationEligibility.NOT_ELIGIBLE
            ObservationOutcome.INSUFFICIENT_DATA -> ObservationEligibility.UNDETERMINED
        },
        completion = EpisodeCompletion.COMPLETE, reason = reason, sourceEventIds = sourceEventIds,
        assistance = emptySet(), adultHelpKnown = false, learningContexts = setOf(LearningContext.GAME),
        contextFamilies = setOf("test"),
    )

    private fun fact(episode: String, sequence: Long, detail: FactDetail) = AnalyticsFact(
        eventId = "$episode-$sequence", gameRunId = "run", episodeId = episode,
        actionId = "action-$sequence", sequence = sequence, detail = detail,
        context = DecisionContext(
            presentationId = "shown-$sequence", informationPresented = true, complete = true,
            before = FinancialPosition(100, 0, 20), after = FinancialPosition(90, 10, 20),
            alternativeAvailable = true,
        ),
    )

    private fun saving(sequence: Long, window: String, kind: SavingMovementKind, amount: Long) = fact(
        "saving", sequence, FactDetail.SavingMovement("operation-$sequence", "goal", window, kind, amount),
    )

    private fun outcomeFor(reason: ObservationReason): ObservationOutcome = when (reason) {
        ObservationReason.CORRECT_COMPARISON, ObservationReason.VALID_PLAN, ObservationReason.NEEDS_COVERED,
        ObservationReason.FUNDING_PLAN_SELECTED, ObservationReason.INTERVAL_COVERED,
        ObservationReason.SAVING_PROGRESS, ObservationReason.PRIORITY_APPLIED,
        ObservationReason.RESERVE_PRESERVED, ObservationReason.RESERVE_USED,
        ObservationReason.RECOVERY_COMPLETED, ObservationReason.PRIORITY_RESOURCE_PRESERVED,
        ObservationReason.EARNING_PLAN_COMPLETED, ObservationReason.EXPLANATION_APPLIED,
        ObservationReason.CORRECT_LEDGER_ANSWER -> ObservationOutcome.SUPPORTED
        ObservationReason.INCORRECT_COMPARISON, ObservationReason.KNOWN_NEED_OMITTED,
        ObservationReason.INFEASIBLE_ALLOCATION, ObservationReason.AVOIDABLE_NEEDS_GAP,
        ObservationReason.SAVING_INTENTION_NOT_APPLIED, ObservationReason.PRIORITY_CONFLICT_MISUNDERSTOOD,
        ObservationReason.RESERVE_OVERSTATED, ObservationReason.GAP_WORSENED,
        ObservationReason.PRIORITY_RESOURCE_LOST, ObservationReason.EARNING_PLAN_INFEASIBLE,
        ObservationReason.INCORRECT_EXPLANATION, ObservationReason.INCORRECT_LEDGER_ANSWER -> ObservationOutcome.DIFFICULTY
        ObservationReason.CONTEXT_MISSING, ObservationReason.PRESENTATION_MISSING,
        ObservationReason.INVALID_CONTEXT, ObservationReason.EPISODE_PENDING,
        ObservationReason.CORRECT_EXPLANATION -> ObservationOutcome.INSUFFICIENT_DATA
        ObservationReason.ORIGINAL_SHORTFALL, ObservationReason.UNKNOWN_REVISION_CAUSE,
        ObservationReason.NO_AVAILABLE_CHOICE, ObservationReason.NO_MANAGED_DECISION,
        ObservationReason.CIRCULAR_TRANSFER, ObservationReason.NO_SAVING_OPPORTUNITY,
        ObservationReason.DESIRE_UNKNOWN, ObservationReason.PRIORITY_UNKNOWN,
        ObservationReason.NO_RESERVE_INTENTION, ObservationReason.NO_EXTERNAL_EXPENSE,
        ObservationReason.CHANGE_NOT_UNEXPECTED, ObservationReason.NO_RANKED_ALTERNATIVE -> ObservationOutcome.NEUTRAL
    }
}
