package ru.nksk.lctapp.feature.menu.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import ru.nksk.lctapp.core.ui.theme.Nunito

@Composable
internal fun MenuText(
    text: String,
    size: Int,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    letterSpacing: Float = 0f,
    textAlign: TextAlign = TextAlign.Start,
) {
    Text(
        text = text, modifier = modifier, color = color,
        fontFamily = Nunito, fontWeight = FontWeight.ExtraBold,
        fontSize = size.sp, lineHeight = (size + 4).sp,
        letterSpacing = letterSpacing.sp, textAlign = textAlign,
    )
}
