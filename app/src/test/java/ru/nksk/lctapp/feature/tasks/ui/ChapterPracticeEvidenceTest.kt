package ru.nksk.lctapp.feature.tasks.ui

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.nksk.lctapp.domain.analytics.AnalyticsFact
import ru.nksk.lctapp.domain.analytics.FactDetail
import ru.nksk.lctapp.domain.analytics.LearningContext
import ru.nksk.lctapp.domain.analytics.SkillEvaluator
import ru.nksk.lctapp.domain.minigame.chapterone.ShoppingComparison
import ru.nksk.lctapp.domain.minigame.chapterone.ShoppingProgress
import ru.nksk.lctapp.domain.minigame.chapterone.ShoppingSide
import ru.nksk.lctapp.domain.minigame.chapterone.TripPacking
import ru.nksk.lctapp.domain.minigame.chapterone.TripSupply
import ru.nksk.lctapp.feature.storygames.ui.ShoppingComparisonAction
import ru.nksk.lctapp.feature.storygames.ui.ShoppingComparisonGameViewModel

class ChapterPracticeEvidenceTest {
    @Test fun partialProgressAndRestorationReuseTheSameAnswerIds() {
        val saved = SavedStateHandle()
        val model = ShoppingComparisonGameViewModel(saved)
        model.onAction(ShoppingComparisonAction.Choose(ShoppingSide.RIGHT))
        val first = ChapterPracticeEvidence.shopping(ShoppingProgress(model.seriesId, model.uiState.value))
        val restored = ShoppingComparisonGameViewModel(SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) }))
        val replay = ChapterPracticeEvidence.shopping(ShoppingProgress(restored.seriesId, restored.uiState.value))
        assertEquals(model.seriesId, restored.seriesId)
        assertEquals(first, replay)
        assertEquals("chapter-practice:event:shopping:${model.seriesId}:answer:0", first.single().eventId("event"))

        restored.onAction(ShoppingComparisonAction.Next)
        restored.onAction(ShoppingComparisonAction.Choose(ShoppingSide.LEFT))
        val next = ChapterPracticeEvidence.shopping(ShoppingProgress(restored.seriesId, restored.uiState.value))
        assertEquals(first.single(), next.first())
        assertEquals(2, next.map { it.eventId("event") }.distinct().size)
        assertNotEquals(first.single().eventId("event"), first.single().eventId("another-event"))
        assertNotEquals(model.seriesId, ShoppingComparisonGameViewModel(SavedStateHandle()).seriesId)
    }

    @Test fun rawDisplayedPricesAndChosenSidesAreNeverInverted() {
        val game = ShoppingComparison().choose(ShoppingSide.LEFT).next()
            .choose(ShoppingSide.RIGHT).next().choose(ShoppingSide.LEFT)
        val evidence = ChapterPracticeEvidence.shopping(ShoppingProgress("session", game))
        assertEquals(listOf(
            "shopping-choice-v1:round=same-map:left=6:right=4:chosen=LEFT",
            "shopping-choice-v1:round=same-lantern:left=2:right=5:chosen=RIGHT",
            "shopping-choice-v1:round=same-lens:left=3:right=3:chosen=LEFT",
        ), evidence.map { it.detail })
        assertTrue(evidence.map { fact(it) }.all { it.detail is FactDetail.Interaction })
    }

    @Test fun trainingDoesNotInventSkillMasteryOrPersonalSpending() {
        val shopping = ShoppingComparison().choose(ShoppingSide.RIGHT).next()
            .choose(ShoppingSide.LEFT).next().choose(ShoppingSide.RIGHT)
        val packed = TripPacking().toggle(TripSupply.MAP).toggle(TripSupply.LANTERN).finish()
        val evidence = ChapterPracticeEvidence.shopping(ShoppingProgress("series", shopping)) +
            ChapterPracticeEvidence.packing(packed)
        val facts = evidence.mapIndexed { index, item -> fact(item, index.toLong()) }
        assertTrue(facts.all { it.learningContext == LearningContext.TRAINING })
        assertTrue(facts.all { it.detail is FactDetail.Interaction })
        assertTrue(facts.all { it.context.before == null && it.context.after == null })
        assertTrue(SkillEvaluator().evaluate(facts).isEmpty())
        assertEquals("trip_packing", facts.last().contextFamily)
        assertEquals("shopping_comparison", facts.first().contextFamily)
    }

    @Test fun packingSnapshotKeepsCanonicalSelectionAndExactReserve() {
        val firstOrder = TripPacking().toggle(TripSupply.KEYCHAIN).toggle(TripSupply.LANTERN)
            .toggle(TripSupply.BUN).toggle(TripSupply.MAP).finish()
        val reordered = TripPacking().toggle(TripSupply.MAP).toggle(TripSupply.BUN)
            .toggle(TripSupply.LANTERN).toggle(TripSupply.KEYCHAIN).finish()
        val snapshot = ChapterPracticeEvidence.packing(firstOrder)
        assertEquals(snapshot, ChapterPracticeEvidence.packing(reordered))
        assertEquals("trip-packing-v1:budget=12:selected=MAP,LANTERN,BUN,KEYCHAIN:spent=9:reserve=3", snapshot.detail)
        assertEquals("chapter-practice:event:packing:plan:MAP,LANTERN,BUN,KEYCHAIN", snapshot.eventId("event"))
        assertEquals(snapshot, ChapterPracticeEvidence.packing(firstOrder.toggle(TripSupply.MAP)))
        assertEquals(3, firstOrder.reserve)
        assertFalse(snapshot.detail.contains("mastered", ignoreCase = true))
    }

    @Test fun aRevisedValidPlanHasItsOwnIdWhileTheSamePlanStillDeduplicates() {
        val essentials = TripPacking().toggle(TripSupply.MAP).toggle(TripSupply.LANTERN).finish()
        val withBun = TripPacking().toggle(TripSupply.BUN).toggle(TripSupply.LANTERN).toggle(TripSupply.MAP).finish()
        val sameWithBun = TripPacking().toggle(TripSupply.MAP).toggle(TripSupply.BUN).toggle(TripSupply.LANTERN).finish()
        val original = ChapterPracticeEvidence.packing(essentials)
        val revised = ChapterPracticeEvidence.packing(withBun)
        val reordered = ChapterPracticeEvidence.packing(sameWithBun)
        assertNotEquals(original.eventId("event"), revised.eventId("event"))
        assertEquals(revised.eventId("event"), reordered.eventId("event"))
        assertEquals(revised.detail, reordered.detail)
        assertEquals(6, essentials.reserve)
        assertEquals(5, withBun.reserve)
    }

    @Test(expected = IllegalArgumentException::class)
    fun unfinishedPackingCannotBeReportedAsAnAcceptedPlan() {
        ChapterPracticeEvidence.packing(TripPacking().toggle(TripSupply.MAP))
    }

    private fun fact(evidence: ChapterPracticeEvidence, sequence: Long = 1L): AnalyticsFact = evidence.fact(
        occurrence = "event", run = "run", sequence = sequence, day = 2, period = "week1",
        contentVersion = "catalog", rulesVersion = "rules",
    )
}
