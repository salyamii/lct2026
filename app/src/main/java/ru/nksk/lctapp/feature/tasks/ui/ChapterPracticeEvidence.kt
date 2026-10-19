package ru.nksk.lctapp.feature.tasks.ui

import ru.nksk.lctapp.domain.analytics.AnalyticsFact
import ru.nksk.lctapp.domain.analytics.DecisionContext
import ru.nksk.lctapp.domain.analytics.FactDetail
import ru.nksk.lctapp.domain.analytics.LearningContext
import ru.nksk.lctapp.domain.minigame.DeedGameKind
import ru.nksk.lctapp.domain.minigame.chapterone.ShoppingProgress
import ru.nksk.lctapp.domain.minigame.chapterone.TripPacking
import ru.nksk.lctapp.domain.minigame.chapterone.TripSupply

/** Entrusted practice is observable without inventing personal spending or skill mastery.
 * Chapter observations keep the original prices and selected sides without
 * reinterpreting them as assessed comparison exercises.
 */
internal data class ChapterPracticeEvidence(
    val key: String,
    val series: String,
    val kind: DeedGameKind,
    val detail: String,
) {
    fun eventId(occurrence: String) = "chapter-practice:$occurrence:$key"

    fun fact(occurrence: String, run: String, sequence: Long, day: Int?, period: String?,
        contentVersion: String, rulesVersion: String) = AnalyticsFact(
        eventId = eventId(occurrence), gameRunId = run,
        episodeId = "chapter-practice:$occurrence:$series", actionId = eventId(occurrence),
        sequence = sequence, detail = FactDetail.Interaction(detail),
        context = DecisionContext(day = day, financialPeriodId = period),
        learningContext = LearningContext.TRAINING,
        contextFamily = if (kind == DeedGameKind.TRIP_PACKING) "trip_packing" else "shopping_comparison",
        contentVersion = contentVersion, gameRulesVersion = rulesVersion,
    )

    companion object {
        fun packing(state: TripPacking): ChapterPracticeEvidence {
            require(state.completed)
            val selected = TripSupply.entries.filter { it in state.selected }.joinToString(",") { it.name }
            // A recorded plan can survive a failed event completion. A later board may
            // legitimately choose different extras; preserve both plans without ID conflicts.
            return ChapterPracticeEvidence("packing:plan:$selected", "packing", DeedGameKind.TRIP_PACKING,
                "trip-packing-v1:budget=${TripPacking.BUDGET}:selected=$selected:spent=${state.spent}:reserve=${state.reserve}")
        }

        fun shopping(progress: ShoppingProgress): List<ChapterPracticeEvidence> {
            require(progress.seriesId.isNotBlank())
            return progress.state.answers.mapIndexed { index, answer ->
                ChapterPracticeEvidence("shopping:${progress.seriesId}:answer:$index", "shopping:${progress.seriesId}",
                    DeedGameKind.SHOPPING_COMPARISON,
                    "shopping-choice-v1:round=${answer.roundId}:left=${answer.leftPrice}:right=${answer.rightPrice}:chosen=${answer.selected.name}")
            }
        }
    }
}
