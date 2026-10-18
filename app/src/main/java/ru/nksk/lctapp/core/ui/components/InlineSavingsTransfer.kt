package ru.nksk.lctapp.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
) {
    val limit = minOf(maximum.coerceAtLeast(0), reserveBalance.coerceAtLeast(0),
        Long.MAX_VALUE - savingsBalance.coerceAtLeast(0))
    val amount = input.toLongOrNull()
    val valid = amount != null && amount in 1..limit
    val preview = amount?.takeIf { it in 0..limit } ?: 0L
    val current = (amount ?: 0L).coerceIn(0, limit)
    val focus = LocalFocusManager.current
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            TransferStep("−", "Уменьшить сумму на одну монету", enabled && current > 0) {
                onInputChange((current - 1).toString())
            }
            GamePlaque(Modifier.weight(1f).heightIn(min = 56.dp), light = true, fixedEdges = true,
                fillVisibleHeight = true, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                BasicTextField(value = input, onValueChange = { value ->
                    onInputChange(value.filter { it in '0'..'9' }.take(19))
                }, modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp)
                    .semantics { contentDescription = "Сколько монет отложить в копилку" },
                    enabled = enabled, singleLine = true,
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
            TransferStep("+", "Увеличить сумму на одну монету", enabled && current < limit) {
                onInputChange((current + 1).toString())
            }
        }
        if (input.isNotEmpty() && (amount == null || amount < 0 || amount > limit)) {
            Text("Можно отложить до $limit", color = Color(0xFF934113), fontFamily = Nunito,
                fontSize = 14.sp, lineHeight = 18.sp)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            TransferBalance("В копилке будет", savingsBalance + preview)
            TransferBalance("В запасе останется", reserveBalance - preview)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GameActionButton("Отмена", onClick = { focus.clearFocus(); onCancel() },
                modifier = Modifier.weight(1f), enabled = enabled, style = GameActionStyle.SECONDARY,
                textStyle = GameActionButtonDefaults.QuietText)
            GameActionButton("Отложить", onClick = { focus.clearFocus(); onConfirm() },
                modifier = Modifier.weight(1f), enabled = enabled && valid,
                textStyle = GameActionButtonDefaults.QuietText)
        }
    }
}

@Composable
private fun TransferBalance(label: String, value: Long) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = GameInk, fontFamily = Nunito,
            fontSize = 14.sp, lineHeight = 19.sp)
        Text(value.toString(), color = GameInk, fontFamily = Nunito, fontWeight = FontWeight.ExtraBold,
            fontSize = 16.sp, lineHeight = 20.sp)
    }
}

@Composable
private fun TransferStep(label: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    GamePlaque(Modifier.size(48.dp).alpha(if (enabled) 1f else .45f), light = true,
        fixedEdges = true, fillVisibleHeight = true, contentPadding = PaddingValues(0.dp)) {
        TextButton(onClick, Modifier.fillMaxSize().semantics { contentDescription = description },
            enabled = enabled, contentPadding = PaddingValues(0.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = GameInk, disabledContentColor = GameInk)) {
            Text(label, fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
        }
    }
}
