package ru.nksk.lctapp.feature.parents.quests

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import ru.nksk.lctapp.feature.parents.R
import ru.nksk.lctapp.feature.parents.report.ParentReportPage

@Composable
fun ParentQuestEntry(quest: ParentQuest, onBack: () -> Unit) {
    val model: ParentQuestViewModel = hiltViewModel()
    val state by model.uiState.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED)
    ParentQuestScreen(
        quest = quest,
        state = state,
        onStepChecked = model::setStepChecked,
        onComplete = dropUnlessResumed { model.complete() },
        onBack = dropUnlessResumed { onBack() },
    )
}

@Composable
fun ParentQuestScreen(
    quest: ParentQuest,
    state: ParentQuestUiState,
    onStepChecked: (Int, Boolean) -> Unit,
    onComplete: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val steps = quest.steps
    ParentReportPage(modifier) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.parents_quests_back))
                }
                Text(stringResource(R.string.parents_quest_header), style = MaterialTheme.typography.titleLarge)
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(quest.title), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(quest.description), style = MaterialTheme.typography.bodyLarge)
            }
            Column(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(24.dp)).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.parents_quest_progress, state.completedSteps, steps.size),
                    style = MaterialTheme.typography.titleMedium,
                )
                LinearProgressIndicator(
                    progress = { state.completedSteps.toFloat() / steps.size },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.parents_quest_check_hint),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                steps.forEachIndexed { index, label ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
                            value = state.checkedSteps[index],
                            enabled = !state.completed,
                            role = Role.Checkbox,
                            onValueChange = { onStepChecked(index, it) },
                        ).padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Checkbox(checked = state.checkedSteps[index], onCheckedChange = null, enabled = !state.completed)
                        Text(stringResource(label), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    }
                }
            }
            Column(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondary, RoundedCornerShape(24.dp)).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.parents_quest_reward), style = MaterialTheme.typography.titleMedium)
                Box(
                    Modifier.size(96.dp).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.65f), RoundedCornerShape(20.dp))
                        .align(Alignment.CenterHorizontally).padding(8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.parents_quest_reward_placeholder),
                        style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                }
                Text(stringResource(R.string.parents_quest_accessory), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.parents_quest_reward_demo), style = MaterialTheme.typography.bodyMedium)
            }
            if (state.completed) {
                Text(stringResource(quest.completionMessage), style = MaterialTheme.typography.titleMedium)
                Button(onClick = onBack, shape = CircleShape, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text(stringResource(R.string.parents_quest_back_to_quests))
                }
            } else {
                Button(onClick = onComplete, enabled = state.canComplete, shape = CircleShape,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text(stringResource(R.string.parents_quest_complete))
                }
            }
        }
    }
}
