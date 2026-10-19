package ru.nksk.lctapp.feature.storygames.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.components.GameArtwork
import ru.nksk.lctapp.core.ui.components.GameInk
import ru.nksk.lctapp.core.ui.theme.Nunito
import ru.nksk.lctapp.core.ui.theme.GameLabelTextWrapping

@Composable
internal fun FinanceWorkbench(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.clip(RoundedCornerShape(24.dp))
        .border(2.dp, Color(0xFFA47A43), RoundedCornerShape(24.dp))) {
        GameArtwork(R.drawable.story_game_workbench_surface, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x333F2817), Color(0x99603518)))))
        content()
    }
}

@Composable
internal fun FinanceCaption(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = GameInk,
    fontSize: TextUnit = 14.sp,
) {
    Text(text, modifier, color, fontFamily = Nunito, fontWeight = FontWeight.ExtraBold,
        fontSize = fontSize, lineHeight = fontSize * 1.2f, textAlign = TextAlign.Center,
        style = GameLabelTextWrapping)
}

@Composable
internal fun FinanceCoinAmount(amount: Int, modifier: Modifier = Modifier, color: Color = GameInk) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        GameArtwork(R.drawable.menu_coin, null, Modifier.size(23.dp))
        FinanceCaption(amount.toString(), color = color, fontSize = 19.sp)
    }
}
