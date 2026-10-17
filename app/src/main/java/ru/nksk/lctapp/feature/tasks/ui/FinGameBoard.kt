package ru.nksk.lctapp.feature.tasks.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.components.GameArtwork
import ru.nksk.lctapp.core.ui.theme.Nunito
import ru.nksk.lctapp.domain.minigame.DeedRewardPreview

/**
 * Общий каркас финансовых мини-игр: ночная сцена, шапка дела и кремовый лист
 * с индикатором награды - как у остальных механик дел.
 */
@Composable
internal fun FinGameBoard(
    deed: DeedGamePresentation?,
    title: String,
    sceneRes: Int,
    preview: DeedRewardPreview,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().background(DeedColors.Scene)) {
        Box {
            GameArtwork(deed?.sceneRes ?: sceneRes,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(150.dp),
                contentScale = ContentScale.Crop,
            )
            deed?.activityArtworkRes?.let { art ->
                GameArtwork(art, contentDescription = null,
                    modifier = Modifier.align(Alignment.BottomEnd).size(92.dp))
            }
            DeedHeader(deed?.title ?: title, onBack = onBack)
        }
        DeedGameSheet(deed, preview, modifier = Modifier.weight(1f), content = content)
    }
}

/** Блок обратной связи раунда: сообщение живёт до явного «Дальше» (MINIGAME-UI-D-003). */
@Composable
internal fun FinFeedback(
    text: String,
    buttonText: String,
    buttonEnabled: Boolean,
    onNext: () -> Unit,
) {
    val feedback = remember { BringIntoViewRequester() }
    LaunchedEffect(text) { if (buttonText.isNotBlank()) feedback.bringIntoView() }
    Column(Modifier.fillMaxWidth().bringIntoViewRequester(feedback)) {
        Text(
            text,
            modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
            minLines = 2,
            fontSize = 18.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = Nunito,
            color = DeedColors.Text,
        )
        Spacer(Modifier.height(8.dp))
        DeedButton(text = buttonText, onClick = onNext,
            modifier = Modifier.fillMaxWidth(), enabled = buttonEnabled)
    }
}
