package ru.nksk.lctapp.feature.storygames.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.components.GameArtwork
import ru.nksk.lctapp.core.ui.components.GameNavigationHeader
import ru.nksk.lctapp.core.ui.components.GamePlaque
import ru.nksk.lctapp.core.ui.theme.Nunito

/** One bounded play area: games share the app's artwork, controls and safe insets. */
@Composable
internal fun ChapterGameScaffold(
    title: String,
    instruction: String,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    onHome: () -> Unit = onExit,
    homeEnabled: Boolean = true,
    footer: @Composable () -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.fillMaxSize().background(Color(0xFF13182C))) {
        GameArtwork(R.drawable.location_observatory_stage, null,
            Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
            Color(0x990F1830), Color(0xCC101729), Color(0xEE101729)))))
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GameNavigationHeader(title, onExit, onHome,
                Modifier.fillMaxWidth().padding(top = 4.dp), homeEnabled = homeEnabled)
            if (instruction.isNotBlank()) GamePlaque(
                Modifier.fillMaxWidth(), light = true, fixedEdges = true,
            ) {
                Text(instruction, color = Color(0xFF25203C), fontFamily = Nunito,
                    fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 20.sp,
                    textAlign = TextAlign.Center)
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center,
                content = content)
            Box(Modifier.fillMaxWidth().padding(bottom = 8.dp)) { footer() }
        }
    }
}
