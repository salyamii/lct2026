package ru.nksk.lctapp.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import ru.nksk.lctapp.core.ui.theme.Nunito
import ru.nksk.lctapp.core.ui.theme.Rubik

/** A transfer editor embedded in its owning screen; it never dispatches or stores money. */
@Composable
internal fun InlineSavingsTransfer(
    input: String,
    maximum: Long,
    reserveBalance: Long,
    savingsBalance: Long,
    enabled: Boolean,
    onInputChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    interactionBlocked: Boolean = false,
) {
    val limit = minOf(maximum.coerceAtLeast(0), reserveBalance.coerceAtLeast(0),
        Long.MAX_VALUE - savingsBalance.coerceAtLeast(0))
    val amount = input.toLongOrNull()
    val valid = amount != null && amount in 1..limit
    val preview = amount?.takeIf { it in 0..limit } ?: 0L
    val current = (amount ?: 0L).coerceIn(0, limit)
    val focus = LocalFocusManager.current
    val limitText = "Можно отложить до $limit"
    val invalidInput = input.isNotEmpty() && (amount == null || amount < 0 || amount > limit)
    val balanceStyle = TextStyle(color = GameInk, fontFamily = Nunito, fontWeight = FontWeight.ExtraBold,
        fontSize = 16.sp, lineHeight = 20.sp, fontFeatureSettings = "tnum")
    // Reserve the whole possible amount, not the changing preview. Crossing 9/10 or
    // 99/100 cannot steal space from a label and add a line while the child types.
    val digits = maxOf(reserveBalance, savingsBalance + limit).toString().length
    val textMeasurer = rememberTextMeasurer()
    val balanceWidth = with(LocalDensity.current) {
        textMeasurer.measure("8".repeat(digits), balanceStyle).size.width.toDp() + 2.dp
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            TransferStep("−", "Уменьшить сумму на одну монету", enabled && current > 0, interactionBlocked) {
                onInputChange((current - 1).toString())
            }
            GamePlaque(Modifier.weight(1f).heightIn(min = 56.dp), light = true, fixedEdges = true,
                fillVisibleHeight = true, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                BasicTextField(value = input, onValueChange = { value ->
                    onInputChange(value.filter { it in '0'..'9' }.take(19))
                }, modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp)
                    .semantics {
                        contentDescription = "Сколько монет отложить в копилку"
                        if (invalidInput) error(limitText)
                    },
                    enabled = enabled && !interactionBlocked, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                    textStyle = TextStyle(fontFamily = Rubik, fontWeight = FontWeight.Bold,
                        fontSize = 22.sp, color = GameInk, textAlign = TextAlign.Center),
                    cursorBrush = SolidColor(GameInk),
                    decorationBox = { field ->
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            if (input.isEmpty()) Text("0", color = GameInk.copy(alpha = .5f),
                                fontFamily = Rubik, fontSize = 22.sp)
                            field()
                        }
                    })
            }
            TransferStep("+", "Увеличить сумму на одну монету", enabled && current < limit, interactionBlocked) {
                onInputChange((current + 1).toString())
            }
        }
        Text(limitText, color = if (invalidInput) Color(0xFF934113) else GameInk,
            fontFamily = Nunito, fontSize = 14.sp, lineHeight = 18.sp)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            TransferBalance("В копилке будет", savingsBalance + preview, balanceWidth, balanceStyle)
            TransferBalance("В запасе останется", reserveBalance - preview, balanceWidth, balanceStyle)
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cancel: @Composable (Modifier) -> Unit = { buttonModifier ->
                GameActionButton("Отмена", onClick = { focus.clearFocus(); onCancel() },
                    modifier = buttonModifier, enabled = enabled, style = GameActionStyle.SECONDARY,
                    interactionBlocked = interactionBlocked,
                    textStyle = GameActionButtonDefaults.QuietText)
            }
            val confirm: @Composable (Modifier) -> Unit = { buttonModifier ->
                GameActionButton("Отложить", onClick = { focus.clearFocus(); onConfirm() },
                    modifier = buttonModifier, enabled = enabled && valid,
                    interactionBlocked = interactionBlocked,
                    textStyle = GameActionButtonDefaults.QuietText)
            }
            if (maxWidth < 280.dp || LocalDensity.current.fontScale >= 1.4f) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    cancel(Modifier.fillMaxWidth())
                    confirm(Modifier.fillMaxWidth())
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    cancel(Modifier.weight(1f))
                    confirm(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun TransferBalance(label: String, value: Long, valueWidth: Dp, valueStyle: TextStyle) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (valueWidth <= maxWidth * .45f) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.weight(1f), color = GameInk, fontFamily = Nunito,
                    fontSize = 14.sp, lineHeight = 19.sp)
                Text(value.toString(), Modifier.width(valueWidth), style = valueStyle, textAlign = TextAlign.End,
                    softWrap = false)
            }
        } else {
            Column(Modifier.fillMaxWidth()) {
                Text(label, color = GameInk, fontFamily = Nunito, fontSize = 14.sp, lineHeight = 19.sp)
                Text(value.toString(), Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    style = valueStyle, softWrap = false)
            }
        }
    }
}

@Composable
private fun TransferStep(label: String, description: String, enabled: Boolean,
    interactionBlocked: Boolean, onClick: () -> Unit) {
    GamePlaque(Modifier.size(48.dp).alpha(if (enabled) 1f else .45f), light = true,
        fixedEdges = true, fillVisibleHeight = true, contentPadding = PaddingValues(0.dp)) {
        TextButton(onClick, Modifier.fillMaxSize().semantics { contentDescription = description },
            enabled = enabled && !interactionBlocked, contentPadding = PaddingValues(0.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = GameInk, disabledContentColor = GameInk)) {
            Text(label, fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
        }
    }
}
