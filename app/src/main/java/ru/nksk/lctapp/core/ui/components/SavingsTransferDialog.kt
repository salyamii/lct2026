package ru.nksk.lctapp.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/** Confirmation contains amounts, not a mutable copy of the saved economy. */
@Composable
fun SavingsTransferDialog(
    withdrawing: Boolean,
    available: Long,
    savings: Long,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
    knownNeeds: Long = 0,
) {
    var input by rememberSaveable(withdrawing) { mutableStateOf("") }
    val amount = input.toLongOrNull()
    val maximum = if (withdrawing) savings else available
    val valid = amount != null && amount > 0 && amount <= maximum
    val muted = Color(0xFF625E80)
    val danger = Color(0xFFA52532)
    val buttonColors = ButtonDefaults.textButtonColors(contentColor = GameInk, disabledContentColor = muted)
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = GamePaper,
        titleContentColor = GameInk,
        textContentColor = GameInk,
        iconContentColor = GameInk,
        title = { Text(if (withdrawing) "Взять из копилки" else "Отложить монеты") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Доступно: $available монет. В копилке: $savings.")
                Text("На еду до следующей недели нужно $knownNeeds монет.")
                OutlinedTextField(value = input, onValueChange = {
                    if (it.length <= 19 && it.all { character -> character in '0'..'9' }) input = it
                }, enabled = !busy, singleLine = true, label = { Text("Сколько монет") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = input.isNotEmpty() && !valid,
                    supportingText = { Text("От 1 до $maximum монет") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = GameInk, unfocusedTextColor = GameInk, disabledTextColor = muted,
                        errorTextColor = danger, cursorColor = GameInk, errorCursorColor = danger,
                        focusedContainerColor = GamePaper, unfocusedContainerColor = GamePaper,
                        disabledContainerColor = GamePaper, errorContainerColor = GamePaper,
                        focusedLabelColor = GameInk, unfocusedLabelColor = muted, disabledLabelColor = muted,
                        errorLabelColor = danger, focusedSupportingTextColor = muted, unfocusedSupportingTextColor = muted,
                        disabledSupportingTextColor = muted, errorSupportingTextColor = danger,
                        focusedBorderColor = GameInk, unfocusedBorderColor = muted,
                        disabledBorderColor = muted, errorBorderColor = danger))
                if (valid) {
                    val value = checkNotNull(amount)
                    Text(if (withdrawing)
                        "В копилке останется ${savings - value}. Доступно для покупок станет ${available + value}."
                    else "В копилке станет ${savings + value}. Для обычных покупок останется ${available - value}.")
                }
                if (withdrawing) Text("На будущие цели останется меньше монет. Всё купленное останется у тебя.")
                else Text("Проверь, что оставшихся монет хватит на ближайшие нужды.")
            }
        },
        confirmButton = {
            TextButton(onClick = { if (valid) onConfirm(checkNotNull(amount)) }, enabled = valid && !busy,
                colors = buttonColors) {
                Text(if (withdrawing) "Взять монеты" else "Отложить монеты")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy, colors = buttonColors) { Text("Отмена") } },
    )
}
