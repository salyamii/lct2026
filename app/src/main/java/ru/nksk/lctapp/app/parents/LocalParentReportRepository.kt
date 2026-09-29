package ru.nksk.lctapp.app.parents

import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import ru.nksk.lctapp.domain.backend.ParentMaterialsRepository
import ru.nksk.lctapp.domain.backend.ParentMaterialsState
import ru.nksk.lctapp.domain.backend.ParentMaterialsPublicationStatus
import ru.nksk.lctapp.feature.parents.report.ParentTopicMaterial
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import ru.nksk.lctapp.domain.backend.CloudSyncRepository
import ru.nksk.lctapp.domain.backend.CloudSyncState
import ru.nksk.lctapp.domain.backend.SkillStatus
import ru.nksk.lctapp.domain.backend.SkillSyncPhase
import ru.nksk.lctapp.domain.analytics.SkillEvaluator
import ru.nksk.lctapp.domain.analytics.SkillId
import ru.nksk.lctapp.domain.content.StoryContent
import ru.nksk.lctapp.domain.content.StoryContentRepository
import ru.nksk.lctapp.domain.game.GameRepository
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.history.AuditEntry
import ru.nksk.lctapp.domain.history.AuditType
import ru.nksk.lctapp.domain.history.HistoryLearningProjection
import ru.nksk.lctapp.domain.history.localGameGeneration
import ru.nksk.lctapp.feature.parents.report.ParentPet
import ru.nksk.lctapp.feature.parents.report.ParentReport
import ru.nksk.lctapp.feature.parents.report.ParentReportRepository
import ru.nksk.lctapp.feature.parents.report.ParentSkill
import ru.nksk.lctapp.feature.parents.report.ParentAssessmentSync
import ru.nksk.lctapp.feature.parents.report.ParentSkillAssessment
import ru.nksk.lctapp.feature.parents.report.ParentSkillStatus
import ru.nksk.lctapp.feature.parents.report.ParentSyncPhase

/** App composition bridges the existing local aggregate to the isolated parent feature. */
internal class LocalParentReportRepository @Inject constructor(
    private val games: GameRepository,
    private val content: StoryContentRepository,
    private val cloud: CloudSyncRepository,
    private val materials: ParentMaterialsRepository,
) : ParentReportRepository {
    override fun observeReport(): Flow<ParentReport> = flow {
        val installedContent = content.read()
        val local = combine(games.observe(), games.observeHistory()) { game, history ->
            localParentReport(game, history, installedContent)?.let { report ->
                val runId = history.lastOrNull()?.runId
                val generation = runId?.let { id -> localGameGeneration(id,
                    history.lastOrNull { it.runId == id && it.type == AuditType.RESTORED }?.id) }
                val localSequence = history.lastOrNull()?.sequence ?: 0
                val baseline = history.lastOrNull { it.runId == runId && it.worldRestore != null }
                // Match CloudWorldRead.transportSequence: server assessments use the restored
                // source cursor, while the retained Room journal keeps its local numbering.
                val serverSequence = baseline?.let {
                    Math.addExact(checkNotNull(it.worldRestore).sourceHistorySequence, localSequence - it.sequence)
                } ?: localSequence
                LocalReportSnapshot(report, runId, serverSequence, generation)
            }
        }.filterNotNull().distinctUntilChanged()
        // A network progress change never reruns the expensive local history projection.
        emitAll(combine(local, cloud.state, materials.observe()) { snapshot, sync, topics ->
            withParentMaterials(withServerAssessments(snapshot.report, snapshot.runId, snapshot.historySequence, snapshot.generation, sync), topics)
        }.distinctUntilChanged())
    }.flowOn(Dispatchers.Default)

    override suspend fun refreshAssessments() = coroutineScope {
        launch { cloud.refreshSkills() }
        launch { materials.refresh() }
        Unit
    }
}

private data class LocalReportSnapshot(
    val report: ParentReport, val runId: String?, val historySequence: Long, val generation: String?,
)

internal fun withServerAssessments(
    local: ParentReport,
    runId: String?,
    historySequence: Long,
    generation: String?,
    sync: CloudSyncState,
): ParentReport {
    if (local.pet == null) return local
    val response = sync.skills?.takeIf {
        it.schemaVersion == 1 && it.gameRunId == runId && it.basedOnHistorySequence <= historySequence &&
            generation != null && sync.skillsGeneration == generation
    }
    val assessments = response?.skills?.associateBy { it.skillId.id }.orEmpty()
    return local.copy(
        skills = local.skills.map { skill ->
            skill.copy(assessment = assessments[skill.id]?.let { assessment ->
                ParentSkillAssessment(
                    status = when (assessment.status) {
                        SkillStatus.MASTERED -> ParentSkillStatus.MASTERED
                        SkillStatus.PRACTICING -> ParentSkillStatus.PRACTICING
                        SkillStatus.NO_DATA -> ParentSkillStatus.NO_DATA
                        SkillStatus.HAS_PROBLEM -> ParentSkillStatus.HAS_PROBLEM
                    },
                    policyVersion = assessment.policyVersion,
                )
            })
        },
        assessmentSync = ParentAssessmentSync(
            phase = when (sync.skillsPhase) {
                SkillSyncPhase.IDLE -> ParentSyncPhase.IDLE
                SkillSyncPhase.SYNCING -> ParentSyncPhase.SYNCING
                SkillSyncPhase.UNAVAILABLE -> ParentSyncPhase.UNAVAILABLE
                SkillSyncPhase.OFFLINE -> ParentSyncPhase.OFFLINE
                SkillSyncPhase.ERROR -> ParentSyncPhase.ERROR
            },
            isStale = response != null && response.basedOnHistorySequence < historySequence,
        ),
    )
}

/** Null suppresses the transient mismatch between two observers of one committed transaction. */
internal fun localParentReport(game: GameState?, history: List<AuditEntry>, content: StoryContent): ParentReport? {
    if (game == null) return ParentReport()
    val runId = history.lastOrNull()?.runId
    val currentHistory = history.filter { it.runId == runId }
    val checkpoint = currentHistory.lastOrNull { it.after != null }?.after
    if (checkpoint != null && checkpoint != game) return null

    val profiles = if (runId == null) emptyMap() else SkillEvaluator().project(
        runId, HistoryLearningProjection.facts(currentHistory, content),
    ).associateBy { it.skill }
    return ParentReport(
        pet = ParentPet(game.pet.name, game.economy.availableBalance, game.economy.savingsBalance),
        skills = SkillId.entries.map { id ->
            val profile = profiles[id]
            ParentSkill(
                id = id.id,
                title = id.parentTitle(),
                observedEpisodes = profile?.observations?.size ?: 0,
                supportedEpisodes = profile?.supportedEpisodes ?: 0,
                difficultyEpisodes = profile?.difficultyEpisodes ?: 0,
                neutralEpisodes = profile?.neutralEpisodes ?: 0,
                pendingEpisodes = profile?.pendingEpisodes ?: 0,
                incompleteEpisodes = profile?.incompleteEpisodes ?: 0,
                supportedWithoutGameHints = profile?.supportedWithoutGameHints ?: 0,
                assistedEpisodes = profile?.assistedEpisodes ?: 0,
            )
        },
    )
}

/** Parent-facing names from the existing FIN-01–FIN-12 analytics specification. */
private fun SkillId.parentTitle(): String = when (this) {
    SkillId.COMPARE_AMOUNTS -> "Сравнивает денежные суммы"
    SkillId.PLAN_BUDGET -> "Планирует бюджет на период"
    SkillId.PRIORITIZE_NEEDS -> "Учитывает обязательные нужды перед желаниями"
    SkillId.MAKE_MONEY_LAST -> "Следит, чтобы денег хватало до следующего дохода"
    SkillId.SAVE_FOR_GOAL -> "Последовательно собирает на выбранную цель"
    SkillId.DELAY_PURCHASE -> "Откладывает желанную покупку ради приоритета"
    SkillId.BUILD_EMERGENCY_FUND -> "Создаёт запас на непредвиденные расходы"
    SkillId.ADAPT_AFTER_EXPENSE -> "Перестраивает действия после неожиданной траты"
    SkillId.COMPARE_COSTS -> "Сопоставляет денежные и другие затраты"
    SkillId.PLAN_EXTRA_INCOME -> "Планирует дополнительный заработок"
    SkillId.RECONSIDER_DECISION -> "Разбирает финансовые последствия и меняет решение"
    SkillId.UNDERSTAND_INCOME_AND_EXPENSES -> "Понимает свои доходы и расходы"
}

internal fun withParentMaterials(report: ParentReport, materials: ParentMaterialsState): ParentReport {
    val bySkill = materials.catalog?.skills?.associateBy { it.skillId }.orEmpty()
    return report.copy(
        skills = report.skills.map { skill -> skill.copy(material = bySkill[skill.id]?.let {
            ParentTopicMaterial(it.learningGoal, it.story, it.replaceWithParentStory,
                it.conversationStarters, it.parentTakeaway, it.researchBasis, it.researchSources)
        }) },
        materialsUnpublished = materials.catalog?.publicationStatus == ParentMaterialsPublicationStatus.UNPUBLISHED,
        materialsRefreshing = materials.isRefreshing,
        materialsRefreshFailed = materials.refreshFailed,
    )
}
