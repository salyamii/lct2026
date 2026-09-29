package ru.nksk.lctapp.app.parents

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.nksk.lctapp.app.createInitialGameState
import ru.nksk.lctapp.domain.analytics.AnalyticsFact
import ru.nksk.lctapp.domain.analytics.AnalyticsMode
import ru.nksk.lctapp.domain.analytics.DecisionContext
import ru.nksk.lctapp.domain.analytics.FactDetail
import ru.nksk.lctapp.domain.analytics.FinancialPosition
import ru.nksk.lctapp.domain.content.StoryContent
import ru.nksk.lctapp.domain.content.StoryContentRepository
import ru.nksk.lctapp.domain.economy.EconomyState
import ru.nksk.lctapp.domain.game.GameRepository
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.history.AuditEntry
import ru.nksk.lctapp.domain.history.AuditType
import ru.nksk.lctapp.domain.history.WorldRestoreBaseline
import ru.nksk.lctapp.domain.history.localGameGeneration
import ru.nksk.lctapp.domain.analytics.SkillId
import ru.nksk.lctapp.domain.backend.CloudSyncRepository
import ru.nksk.lctapp.domain.backend.CloudSyncResult
import ru.nksk.lctapp.domain.backend.CloudSyncState
import ru.nksk.lctapp.domain.backend.CloudRestorePreview
import ru.nksk.lctapp.domain.backend.SkillAssessmentDto
import ru.nksk.lctapp.domain.backend.SkillAssessmentsResponse
import ru.nksk.lctapp.domain.backend.SkillStatus
import ru.nksk.lctapp.domain.backend.SkillSyncPhase
import ru.nksk.lctapp.feature.parents.report.ParentSkillStatus
import ru.nksk.lctapp.feature.parents.report.ParentSyncPhase

class LocalParentReportRepositoryTest {
    @Test fun reportUsesCurrentLocalNameAndBothRealMoneyAccountsWithoutWriting() = runTest {
        val original = createInitialGameState()
        val game = original.copy(
            pet = original.pet.copy(name = "Лис"),
            economy = EconomyState(original.economy.plan, availableBalance = 73, savingsBalance = 27),
        )
        val games = ReadOnlyGames(game)

        val report = LocalParentReportRepository(games, ReadOnlyContent, ReadOnlyCloud(), ReadOnlyMaterials()).observeReport().first()

        assertEquals("Лис", report.pet?.name)
        assertEquals(100L, report.pet?.balance)
        assertEquals(73L, report.pet?.availableCoins)
        assertEquals(27L, report.pet?.savingsCoins)
        assertEquals(12, report.skills.size)
        assertTrue(report.skills.all { it.observedEpisodes == 0 })
        assertEquals(game, games.state.value)
    }

    @Test fun noSavedGameIsAnEmptyReportAndNeverInitializesAStartupFixture() = runTest {
        val report = LocalParentReportRepository(ReadOnlyGames(null), ReadOnlyContent, ReadOnlyCloud(), ReadOnlyMaterials()).observeReport().first()

        assertNull(report.pet)
        assertTrue(report.skills.isEmpty())
    }

    @Test fun onlyCurrentRunRealEvidenceAppearsAndRepeatedDeliveryDoesNotMultiplyIt() {
        val game = createInitialGameState()
        val old = purchaseFact("old", 2, "old-fact")
        val current = purchaseFact("current", 2, "current-fact")
        val demo = purchaseFact("current", 3, "demo-fact").copy(mode = AnalyticsMode.DEMO)
        val history = listOf(
            AuditEntry("old-start", 1, "old", AuditType.INITIALIZED, after = game),
            factEntry(old),
            AuditEntry("current-start", 1, "current", AuditType.INITIALIZED, after = game),
            factEntry(current), factEntry(current), factEntry(demo),
        )

        val report = checkNotNull(localParentReport(game, history, StoryContent()))
        val needs = report.skills.single { it.id == "FIN-03" }

        assertEquals(1, needs.observedEpisodes)
        assertEquals(1, needs.supportedEpisodes)
        assertEquals(0, needs.difficultyEpisodes)
        assertEquals(0, report.skills.single { it.id == "FIN-01" }.observedEpisodes)
    }

    @Test fun aDelayedHistoryEmissionCannotAttachOldEvidenceToANewWorld() {
        val game = createInitialGameState()
        val updated = game.copy(pet = game.pet.copy(name = "Новое имя"))
        val oldHistory = listOf(AuditEntry("start", 1, "old", AuditType.INITIALIZED, after = game))

        assertNull(localParentReport(updated, oldHistory, StoryContent()))

        val updatedHistory = oldHistory + AuditEntry(
            "rename", 2, "old", AuditType.TECHNICAL_UPDATE, before = game, after = updated,
        )
        assertEquals("Новое имя", localParentReport(updated, updatedHistory, StoryContent())?.pet?.name)
    }

    @Test fun missingContextIsReportedAsIncompleteEvidenceRatherThanADifficulty() {
        val game = createInitialGameState()
        val fact = purchaseFact("current", 2, "incomplete").copy(context = DecisionContext())
        val history = listOf(
            AuditEntry("start", 1, "current", AuditType.INITIALIZED, after = game), factEntry(fact),
        )

        val skill = checkNotNull(localParentReport(game, history, StoryContent())).skills.single { it.id == "FIN-03" }

        assertEquals(1, skill.incompleteEpisodes)
        assertEquals(0, skill.difficultyEpisodes)
        assertEquals(0, skill.supportedEpisodes)
    }

    @Test fun serverAssessmentDoesNotReplaceLocalEvidenceOrMoney() {
        val game = createInitialGameState()
        val history = listOf(AuditEntry("start", 1, "current", AuditType.INITIALIZED, after = game))
        val local = checkNotNull(localParentReport(game, history, StoryContent()))
        val generation = localGameGeneration("current", null)
        val report = withServerAssessments(local, "current", 1, generation,
            CloudSyncState(skills = assessments("current", 1), skillsGeneration = generation))

        assertEquals(local.pet, report.pet)
        assertEquals(ParentSkillStatus.MASTERED, report.skills.first().assessment?.status)
        assertEquals("server-policy-v1", report.skills.first().assessment?.policyVersion)
        assertEquals(0, report.skills.first().supportedEpisodes)
        assertTrue(!report.assessmentSync.isStale)
    }

    @Test fun oldRunAndFutureAssessmentsAreNeverAttachedToCurrentGame() {
        val game = createInitialGameState()
        val history = listOf(AuditEntry("start", 1, "current", AuditType.INITIALIZED, after = game))
        val local = checkNotNull(localParentReport(game, history, StoryContent()))
        for (response in listOf(assessments("previous", 1), assessments("current", 2))) {
            val generation = localGameGeneration("current", null)
            val report = withServerAssessments(local, "current", 1, generation,
                CloudSyncState(skills = response, skillsGeneration = generation))
            assertTrue(report.skills.all { it.assessment == null })
        }
    }

    @Test fun earlierServerBoundaryIsLabeledStaleAndSurvivesOfflineRefresh() {
        val game = createInitialGameState()
        val history = listOf(AuditEntry("start", 1, "current", AuditType.INITIALIZED, after = game))
        val local = checkNotNull(localParentReport(game, history, StoryContent()))
        val generation = localGameGeneration("current", null)
        val report = withServerAssessments(local, "current", 3, generation,
            CloudSyncState(skills = assessments("current", 1), skillsGeneration = generation,
                skillsPhase = SkillSyncPhase.OFFLINE))

        assertEquals(ParentSkillStatus.MASTERED, report.skills.first().assessment?.status)
        assertTrue(report.assessmentSync.isStale)
        assertEquals(ParentSyncPhase.OFFLINE, report.assessmentSync.phase)
    }

    @Test fun restoringTheSameRunHidesRatingsFromTheReplacedWorld() {
        val local = checkNotNull(localParentReport(createInitialGameState(), emptyList(), StoryContent()))
        val report = withServerAssessments(local, "current", 10, localGameGeneration("current", "restore-new"),
            CloudSyncState(skills = assessments("current", 1), skillsGeneration = localGameGeneration("current", null)))
        assertTrue(report.skills.all { it.assessment == null })
    }

    @Test fun restoredReportShowsCurrentServerAssessmentBeyondTheLocalHistorySequence() = runTest {
        val report = restoredReport(sourceSequence = 900, baselineSequence = 2, assessmentSequence = 901)

        assertEquals(ParentSkillStatus.MASTERED, report.skills.first().assessment?.status)
        assertFalse(report.assessmentSync.isStale)
    }

    @Test fun restoredReportMarksOnlyAnEarlierServerCursorAsStale() = runTest {
        val stale = restoredReport(sourceSequence = 900, baselineSequence = 2, assessmentSequence = 900)
        val current = restoredReport(sourceSequence = 10, baselineSequence = 21, assessmentSequence = 11)

        assertEquals(ParentSkillStatus.MASTERED, stale.skills.first().assessment?.status)
        assertTrue(stale.assessmentSync.isStale)
        assertEquals(ParentSkillStatus.MASTERED, current.skills.first().assessment?.status)
        assertFalse(current.assessmentSync.isStale)
    }

    @Test fun restoredReportRejectsAssessmentBeyondServerCursorEvenWhenLocalHistoryIsAhead() = runTest {
        val report = restoredReport(sourceSequence = 10, baselineSequence = 21, assessmentSequence = 12)

        assertTrue(report.skills.all { it.assessment == null })
    }

    @Test fun everyServerStatusIsPreservedAndNoDataIsDifferentFromMissingAssessment() {
        val local = checkNotNull(localParentReport(createInitialGameState(), emptyList(), StoryContent()))
        val generation = localGameGeneration("current", null)
        val response = SkillAssessmentsResponse("current", 1, SkillId.entries.mapIndexed { index, skill ->
            SkillAssessmentDto(skill, SkillStatus.entries[index % 4], "policy")
        })
        val report = withServerAssessments(local, "current", 1, generation,
            CloudSyncState(skills = response, skillsGeneration = generation))
        assertEquals(listOf(ParentSkillStatus.MASTERED, ParentSkillStatus.PRACTICING,
            ParentSkillStatus.NO_DATA, ParentSkillStatus.HAS_PROBLEM), report.skills.take(4).map { it.assessment?.status })
        assertTrue(local.skills.all { it.assessment == null })
    }

    @Test fun explicitRefreshUsesOnlySkillSynchronization() = runTest {
        val cloud = ReadOnlyCloud()
        LocalParentReportRepository(ReadOnlyGames(createInitialGameState()), ReadOnlyContent, cloud, ReadOnlyMaterials())
            .refreshAssessments()
        assertEquals(1, cloud.skillRefreshes)
    }

    @Test fun materialsMapBySkillIdWithoutReplacingEvidenceOrAssessments() {
        val local = checkNotNull(localParentReport(createInitialGameState(), emptyList(), StoryContent()))
        val materials = ru.nksk.lctapp.domain.backend.ParentMaterialsCatalog("version", (1..12).reversed().map {
            ru.nksk.lctapp.domain.backend.ParentSkillMaterialDto("FIN-%02d".format(it), "Цель $it", "История",
                "Пример", List(5) { "Вопрос $it?" }, "Вывод", "Основание", listOf("https://example.org/source"))
        })
        val report = withParentMaterials(local, ru.nksk.lctapp.domain.backend.ParentMaterialsState(materials))
        assertEquals(local.pet, report.pet)
        assertEquals("Цель 1", report.skills.first().material?.learningGoal)
        assertEquals(local.skills, report.skills.map { it.copy(material = null) })
    }

    @Test fun unpublishedMaterialsDoNotBecomeAnAssessmentOrTransportError() {
        val local = checkNotNull(localParentReport(createInitialGameState(), emptyList(), StoryContent()))
        val unpublished = ru.nksk.lctapp.domain.backend.ParentMaterialsCatalog("unpublished", emptyList(),
            publicationStatus = ru.nksk.lctapp.domain.backend.ParentMaterialsPublicationStatus.UNPUBLISHED)
        val report = withParentMaterials(local, ru.nksk.lctapp.domain.backend.ParentMaterialsState(unpublished))
        assertTrue(report.materialsUnpublished)
        assertFalse(report.materialsRefreshFailed)
        assertEquals(local.skills, report.skills)
        assertEquals(local.assessmentSync, report.assessmentSync)
        assertEquals(local.pet, report.pet)
    }

    private fun assessments(runId: String, sequence: Long) = SkillAssessmentsResponse(
        runId, sequence, SkillId.entries.map { SkillAssessmentDto(it, SkillStatus.MASTERED, "server-policy-v1") },
    )

    private suspend fun restoredReport(sourceSequence: Long, baselineSequence: Long, assessmentSequence: Long):
        ru.nksk.lctapp.feature.parents.report.ParentReport {
        val game = createInitialGameState()
        val history = listOf(
            AuditEntry("start", 1, "current", AuditType.INITIALIZED, after = game),
            AuditEntry("restore", baselineSequence, "current", AuditType.RESTORED, after = game,
                worldRestore = WorldRestoreBaseline(sourceSequence, "remote-generation", "checksum", "restore-request")),
            AuditEntry("after-restore", baselineSequence + 1, "current", AuditType.TECHNICAL_UPDATE,
                before = game, after = game),
        )
        val cloud = ReadOnlyCloud().apply {
            state.value = CloudSyncState(skills = assessments("current", assessmentSequence),
                skillsGeneration = localGameGeneration("current", "restore"))
        }
        return LocalParentReportRepository(ReadOnlyGames(game, history), ReadOnlyContent, cloud, ReadOnlyMaterials())
            .observeReport().first()
    }

    private class ReadOnlyMaterials : ru.nksk.lctapp.domain.backend.ParentMaterialsRepository {
        override fun observe() = MutableStateFlow(ru.nksk.lctapp.domain.backend.ParentMaterialsState())
        override suspend fun refresh() = Unit
    }

    private class ReadOnlyCloud : CloudSyncRepository {
        override val state = MutableStateFlow(CloudSyncState())
        var skillRefreshes = 0
        override suspend fun refreshSkills(): CloudSyncResult { skillRefreshes++; return CloudSyncResult.SUCCESS }
        override suspend fun synchronize(): CloudSyncResult = error("Report must not start full synchronization")
        override suspend fun prepareRestore(): CloudRestorePreview = error("Report must not download a world")
        override suspend fun restore(previewId: String) = error("Report must not restore a world")
        override fun dismissRestore(previewId: String) = Unit
    }

    private fun purchaseFact(run: String, sequence: Long, id: String) = AnalyticsFact(
        eventId = id, gameRunId = run, episodeId = id, actionId = id, sequence = sequence,
        detail = FactDetail.OptionalPurchase("cap", 25, purchased = true),
        context = DecisionContext(
            presentationId = "shown-$id", informationPresented = true, complete = true,
            before = FinancialPosition(80, 0, 25), after = FinancialPosition(55, 0, 25),
            alternativeAvailable = true,
        ),
    )

    private fun factEntry(fact: AnalyticsFact) = AuditEntry(
        fact.eventId, fact.sequence, fact.gameRunId, AuditType.FACTS, facts = listOf(fact),
    )

    private class ReadOnlyGames(game: GameState?, initialHistory: List<AuditEntry> = emptyList()) : GameRepository {
        val state = MutableStateFlow(game)
        private val history = MutableStateFlow(initialHistory)
        override fun observe() = state
        override fun observeHistory() = history
        override suspend fun read() = state.value
        override suspend fun initializeIfAbsent(initial: GameState): GameState = error("Report must not initialize the game")
        override suspend fun update(transform: (GameState) -> GameState): GameState = error("Report must not write the game")
    }

    private object ReadOnlyContent : StoryContentRepository {
        override suspend fun read() = StoryContent()
        override suspend fun install(content: StoryContent) = error("Report must not install content")
    }
}
