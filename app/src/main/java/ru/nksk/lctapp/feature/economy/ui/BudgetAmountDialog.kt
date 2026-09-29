package ru.nksk.lctapp.feature.economy.ui

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import ru.nksk.lctapp.core.ui.components.GameInk
import ru.nksk.lctapp.core.ui.components.GamePaper
import ru.nksk.lctapp.core.ui.theme.Rubik

private val BudgetAccent = Color(0xFF3E31B8)
private val BudgetMuted = Color(0xFF625E80)
private val BudgetBorder = Color(0xFFD9DCF5)
private val BudgetDanger = Color(0xFFA52532)

@Composable
internal fun BudgetAmountDialog(
    article: BudgetArticle, amount: Long, maximum: Long, onDismiss: () -> Unit, onSave: (Long) -> Unit, minimum: Long = article.minimum,
) {
    var input by rememberSaveable(article.name) { mutableStateOf(amount.toString()) }
    val value = input.toLongOrNull()
    val valid = value != null && value in minimum..maximum
    AlertDialog(onDismissRequest = onDismiss, containerColor = GamePaper, titleContentColor = GameInk,
        textContentColor = GameInk, iconContentColor = GameInk,
        title = { Text(article.title, fontFamily = Rubik) },
        text = {
            OutlinedTextField(value = input, onValueChange = { if (it.all { char -> char in '0'..'9' } && it.length <= 19) input = it },
                label = { Text("Сколько монет выделим?") }, singleLine = true, isError = !valid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                supportingText = { Text(if (maximum < minimum)
                    "Здесь нужно хотя бы $minimum. Сначала уменьши другую часть бюджета на ${minimum - maximum}."
                    else "Можно распределить от ${minimum} до $maximum") },
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = GameInk, unfocusedTextColor = GameInk,
                    disabledTextColor = BudgetMuted, errorTextColor = BudgetDanger,
                    errorLabelColor = BudgetDanger, errorSupportingTextColor = BudgetDanger,
                    errorCursorColor = BudgetDanger, errorBorderColor = BudgetDanger,
                    focusedLabelColor = BudgetAccent, unfocusedLabelColor = BudgetMuted,
                    disabledLabelColor = BudgetMuted, disabledSupportingTextColor = BudgetMuted,
                    focusedSupportingTextColor = BudgetMuted, unfocusedSupportingTextColor = BudgetMuted,
                    focusedContainerColor = GamePaper, unfocusedContainerColor = GamePaper,
                    disabledContainerColor = GamePaper, errorContainerColor = GamePaper,
                    cursorColor = BudgetAccent, focusedBorderColor = BudgetAccent, unfocusedBorderColor = BudgetBorder))
        },
        confirmButton = { TextButton(onClick = { if (valid) onSave(checkNotNull(value)) }, enabled = valid) {
            Text("Готово", color = if (valid) BudgetAccent else BudgetMuted)
        } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", color = BudgetMuted) } })
}
