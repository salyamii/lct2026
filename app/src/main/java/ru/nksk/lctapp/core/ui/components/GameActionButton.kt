package ru.nksk.lctapp.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.nksk.lctapp.core.ui.theme.AdventureLime
import ru.nksk.lctapp.core.ui.theme.Nunito
import ru.nksk.lctapp.core.ui.theme.Rubik

internal enum class GameActionStyle { PRIMARY, SECONDARY, QUIET }

internal object GameActionButtonDefaults {
    val PrimaryText = TextStyle(fontFamily = Rubik, fontWeight = FontWeight.ExtraBold,
        fontSize = 16.sp, lineHeight = 22.sp)
    val QuietText = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold,
        fontSize = 15.sp, lineHeight = 21.sp)
}

/** Shared interaction rules; feature wrappers keep their approved shape, color and typography. */
@Composable
internal fun GameActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    interactionBlocked: Boolean = false,
    style: GameActionStyle = GameActionStyle.PRIMARY,
    minHeight: Dp = 56.dp,
    shape: Shape = RoundedCornerShape(28.dp),
    textStyle: TextStyle = GameActionButtonDefaults.PrimaryText,
    contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
    containerColor: Color = when (style) {
        GameActionStyle.PRIMARY -> AdventureLime
        GameActionStyle.SECONDARY -> Color.White
        GameActionStyle.QUIET -> Color.Transparent
    },
    contentColor: Color = GameInk,
    borderColor: Color = GameInk.copy(alpha = .35f),
) {
    // A short write blocks input without flashing disabled colors or replacing the label.
    // Actual unavailability still has its own appearance; loading can show progress explicitly.
    val keepAppearance = loading || (enabled && interactionBlocked)
    val acceptsInput = enabled && !loading && !interactionBlocked
    val colors = ButtonDefaults.buttonColors(
        containerColor = containerColor,
        contentColor = contentColor,
        disabledContainerColor = when {
            keepAppearance -> containerColor
            style == GameActionStyle.QUIET -> Color.Transparent
            else -> GameDisabledButtonContainer
        },
        disabledContentColor = if (keepAppearance) contentColor else GameDisabledButtonContent,
    )
    val buttonModifier = modifier.fillMaxWidth().heightIn(min = minHeight).then(
        when {
            loading -> Modifier.semantics { stateDescription = "Выполняется" }
            enabled && interactionBlocked -> Modifier.semantics { stateDescription = "Временно недоступно" }
            else -> Modifier
        })
    val content: @Composable () -> Unit = {
        Box(contentAlignment = Alignment.Center) {
            Text(text, modifier = Modifier.alpha(if (loading) 0f else 1f),
                style = textStyle, textAlign = TextAlign.Center)
            if (loading) CircularProgressIndicator(Modifier.size(20.dp).clearAndSetSemantics {},
                color = LocalContentColor.current, strokeWidth = 2.dp)
        }
    }
    when (style) {
        GameActionStyle.PRIMARY -> Button(onClick, buttonModifier, enabled = acceptsInput,
            shape = shape, colors = colors, contentPadding = contentPadding) { content() }
        GameActionStyle.SECONDARY -> OutlinedButton(onClick, buttonModifier, enabled = acceptsInput,
            shape = shape, colors = colors, border = BorderStroke(1.dp, borderColor),
            contentPadding = contentPadding) { content() }
        GameActionStyle.QUIET -> TextButton(onClick, buttonModifier, enabled = acceptsInput,
            shape = shape, colors = colors, contentPadding = contentPadding) { content() }
    }
}
