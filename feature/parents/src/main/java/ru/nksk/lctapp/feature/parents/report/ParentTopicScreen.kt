package ru.nksk.lctapp.feature.parents.report

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.nksk.lctapp.feature.parents.R

@Composable
fun ParentTopicScreen(
    skillId: String,
    state: ParentReportUiState,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ParentReportPage(modifier) {
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.parents_report_back))
                    }
                    Text(stringResource(R.string.parents_report_topic), style = MaterialTheme.typography.titleLarge)
                }
            }
            when (state) {
                ParentReportUiState.Loading -> item { ParentReportLoading() }
                ParentReportUiState.Error -> item { ParentReportError(onRetry) }
                is ParentReportUiState.Ready -> {
                    val skill = state.report.skills.singleOrNull { it.id == skillId }
                    if (skill == null) {
                        item { ParentReportError(onRetry) }
                    } else {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(skill.title, style = MaterialTheme.typography.headlineSmall)
                                ParentSkillAssessmentLabel(skill.assessment)
                            }
                        }
                        item {
                            ParentAssessmentSyncCard(
                                sync = state.report.assessmentSync,
                                hasAssessments = skill.assessment != null,
                                onRefresh = onRefresh,
                            )
                        }
                        item { SkillEvidence(skill) }
                        item {
                            ParentReportNote(
                                stringResource(R.string.parents_report_questions_title),
                                stringResource(R.string.parents_report_questions_placeholder),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SkillEvidence(skill: ParentSkill) {
    Column(
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(24.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.parents_report_evidence_title), style = MaterialTheme.typography.titleMedium)
        if (skill.observedEpisodes == 0) {
            Text(
                stringResource(R.string.parents_report_no_evidence_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            EvidenceRow(stringResource(R.string.parents_report_supported), skill.supportedEpisodes)
            EvidenceRow(stringResource(R.string.parents_report_difficulties), skill.difficultyEpisodes)
            EvidenceRow(stringResource(R.string.parents_report_neutral), skill.neutralEpisodes)
            EvidenceRow(stringResource(R.string.parents_report_incomplete), skill.incompleteEpisodes)
            if (skill.pendingEpisodes > 0) EvidenceRow(stringResource(R.string.parents_report_pending), skill.pendingEpisodes)
            EvidenceRow(stringResource(R.string.parents_report_no_hints), skill.supportedWithoutGameHints)
            EvidenceRow(stringResource(R.string.parents_report_assisted), skill.assistedEpisodes)
        }
    }
}

@Composable
private fun EvidenceRow(label: String, count: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(count.toString(), style = MaterialTheme.typography.titleMedium)
    }
}
