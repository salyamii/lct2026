package ru.nksk.lctapp.feature.parents.report

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import ru.nksk.lctapp.feature.parents.R

/** Local observations and server assessments using the donor's parent theme. */
@Composable
fun ParentReportScreen(
    state: ParentReportUiState,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onOpenTopic: (String) -> Unit,
    onOpenQuests: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ParentReportPage(modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.parents_report_title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onClose) { Text(stringResource(R.string.parents_report_close)) }
                }
            }
            when (state) {
                ParentReportUiState.Loading -> item { ParentReportLoading() }
                ParentReportUiState.Error -> item { ParentReportError(onRetry) }
                is ParentReportUiState.Ready -> {
                    val report = state.report
                    val pet = report.pet
                    if (pet == null) {
                        item {
                            ParentReportNote(
                                stringResource(R.string.parents_report_no_game_title),
                                stringResource(R.string.parents_report_no_game_body),
                            )
                        }
                    } else {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(pet.name, style = MaterialTheme.typography.headlineMedium)
                                Text(
                                    stringResource(R.string.parents_report_local),
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                            }
                        }
                        item { ReportStats(pet, report.skills) }
                        item {
                            ParentAssessmentSyncCard(
                                sync = report.assessmentSync,
                                hasAssessments = report.skills.any { it.assessment != null },
                                onRefresh = onRefresh,
                            )
                        }
                        item { QuestCard(onOpenQuests) }
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(stringResource(R.string.parents_report_skills), style = MaterialTheme.typography.titleMedium)
                                Text(
                                    stringResource(R.string.parents_report_evidence_note),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        items(report.skills, key = { it.id }) { skill ->
                            SkillCard(skill, onOpen = dropUnlessResumed { onOpenTopic(skill.id) })
                        }
                    }
                }
            }
            item { Spacer(Modifier.size(4.dp)) }
        }
    }
}

@Composable
private fun ReportStats(pet: ParentPet, skills: List<ParentSkill>) {
    val assessed = skills.count { it.assessment != null }
    val mastered = skills.count { it.assessment?.status == ParentSkillStatus.MASTERED }
    val masteryValue = if (assessed == 0) {
        stringResource(R.string.parents_report_unknown_count)
    } else {
        stringResource(R.string.parents_report_skill_count, mastered, skills.size)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BoxWithConstraints {
            if (maxWidth < 360.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(stringResource(R.string.parents_report_balance), pet.balance.toString(), Modifier.fillMaxWidth())
                    StatTile(
                        stringResource(R.string.parents_report_mastered_skills),
                        masteryValue,
                        Modifier.fillMaxWidth(),
                    )
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    StatTile(stringResource(R.string.parents_report_balance), pet.balance.toString(), Modifier.weight(1f))
                    StatTile(
                        stringResource(R.string.parents_report_mastered_skills),
                        masteryValue,
                        Modifier.weight(1f),
                    )
                }
            }
        }
        Text(
            stringResource(R.string.parents_report_money_breakdown, pet.availableCoins, pet.savingsCoins),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (assessed in 1 until skills.size) {
            Text(
                stringResource(R.string.parents_report_assessed_count, assessed, skills.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(value, style = MaterialTheme.typography.headlineMedium)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SkillCard(skill: ParentSkill, onOpen: () -> Unit) {
    Card(
        onClick = onOpen,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ParentSkillAssessmentLabel(skill.assessment)
                Text(skill.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(
                        if (skill.observedEpisodes == 0) R.string.parents_report_no_observations
                        else R.string.parents_report_observations,
                        skill.observedEpisodes,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}

@Composable
internal fun ParentSkillAssessmentLabel(assessment: ParentSkillAssessment?) {
    val status = assessment?.status
    val (background, foreground) = when (status) {
        ParentSkillStatus.MASTERED -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        ParentSkillStatus.PRACTICING -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        ParentSkillStatus.HAS_PROBLEM -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        ParentSkillStatus.NO_DATA, null -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        stringResource(
            when (status) {
                ParentSkillStatus.MASTERED -> R.string.parents_report_status_mastered
                ParentSkillStatus.PRACTICING -> R.string.parents_report_status_practicing
                ParentSkillStatus.HAS_PROBLEM -> R.string.parents_report_status_has_problem
                ParentSkillStatus.NO_DATA -> R.string.parents_report_status_no_data
                null -> R.string.parents_report_assessment_unavailable
            },
        ),
        modifier = Modifier.background(background, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        style = MaterialTheme.typography.labelMedium,
        color = foreground,
    )
}

@Composable
internal fun ParentAssessmentSyncCard(
    sync: ParentAssessmentSync,
    hasAssessments: Boolean,
    onRefresh: () -> Unit,
) {
    val isSyncing = sync.phase == ParentSyncPhase.SYNCING
    val message = when (sync.phase) {
        ParentSyncPhase.IDLE -> if (hasAssessments) R.string.parents_report_sync_cached else R.string.parents_report_sync_not_received
        ParentSyncPhase.SYNCING -> if (hasAssessments) R.string.parents_report_sync_loading_cached else R.string.parents_report_sync_loading
        ParentSyncPhase.UNAVAILABLE -> if (hasAssessments) R.string.parents_report_sync_unavailable_cached else R.string.parents_report_sync_unavailable
        ParentSyncPhase.OFFLINE -> if (hasAssessments) R.string.parents_report_sync_offline_cached else R.string.parents_report_sync_offline
        ParentSyncPhase.ERROR -> if (hasAssessments) R.string.parents_report_sync_error_cached else R.string.parents_report_sync_error
    }
    Column(
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.parents_report_assessments_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (isSyncing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Text(
            stringResource(message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (sync.isStale) {
            Text(
                stringResource(R.string.parents_report_sync_stale),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(
            onClick = onRefresh,
            enabled = !isSyncing,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
            modifier = Modifier.align(Alignment.End),
        ) {
            Text(stringResource(R.string.parents_report_refresh))
        }
    }
}

@Composable
private fun QuestCard(onOpen: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(28.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.parents_report_quests_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.parents_report_quests_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = onOpen,
            shape = CircleShape,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 52.dp),
        ) { Text(stringResource(R.string.parents_report_create_quest), textAlign = TextAlign.Center) }
    }
}

@Composable
internal fun ParentReportPage(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(Modifier.widthIn(max = 720.dp).fillMaxSize().padding(horizontal = 20.dp, vertical = 8.dp)) { content() }
    }
}

@Composable
internal fun ParentReportLoading() {
    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
internal fun ParentReportError(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.parents_report_error), textAlign = TextAlign.Center)
        Button(onClick = onRetry) { Text(stringResource(R.string.parents_report_retry)) }
    }
}

@Composable
internal fun ParentReportNote(title: String, body: String) {
    Column(
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
