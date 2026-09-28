package ru.nksk.lctapp.feature.tasks.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.nksk.lctapp.core.ui.components.GameRewardProgress
import ru.nksk.lctapp.core.ui.components.AdaptiveActionPanel
import ru.nksk.lctapp.domain.minigame.DeedRewardPreview

/** Keep the possible reward visible while long boards scroll on smaller phones. */
@Composable
internal fun DeedGameSheet(
    deed: DeedGamePresentation?,
    reward: DeedRewardPreview,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    DeedSheet(modifier) {
        AdaptiveActionPanel(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(0.dp),
            contentSpacing = 0.dp,
            actionSpacing = 0.dp,
            sectionSpacing = 12.dp,
            fillBody = true,
            actionsAtTop = true,
            actions = {
                if (deed != null && !deed.storyAction && deed.maximumReward > 0) {
                    GameRewardProgress(reward.reward(deed.maximumReward), deed.maximumReward)
                }
            },
            content = content,
        )
    }
}
