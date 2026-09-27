package ru.nksk.lctapp.feature.economy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.nksk.lctapp.core.ui.components.*
import ru.nksk.lctapp.core.ui.game.availableSourcesDescription
import ru.nksk.lctapp.core.ui.game.toAdventurePetPresentation
import ru.nksk.lctapp.core.ui.theme.AdventureLime
import ru.nksk.lctapp.core.ui.theme.Nunito
import ru.nksk.lctapp.core.ui.theme.Rubik
import ru.nksk.lctapp.domain.economy.EconomyOperations

/** One destination: choose a direction, preview, confirm and see the receipt in place. */
@Composable
internal fun SavingsScreen(state: EconomyUiState, onAction: (EconomyAction) -> Unit,
    onBack: () -> Unit, onOpenGoal: () -> Unit, onOpenBudget: () -> Unit) {
    val economy = state.economy
    val withdrawing = state.savingsStep == SavingsStep.WITHDRAW
    val amount = state.transferInput.toLongOrNull()
    val maximum = if (withdrawing) economy?.savingsBalance ?: 0 else economy?.availableBalance ?: 0
    val planningRequired = economy?.let { it.planning != null || it.unallocated != 0L } == true
    val valid = economy != null && !planningRequired && amount != null && amount > 0 && amount <= maximum
    val confirming = state.withdrawal != null || state.depositWarning != null
    val pet = state.pet?.toAdventurePetPresentation()
    val focus = LocalFocusManager.current
    LaunchedEffect(state.transferReceipt, confirming) {
        if (state.transferReceipt != null || confirming) focus.clearFocus()
    }
    AdventureScreen(
        title = "Наша копилка", onBack = onBack,
        available = economy?.availableBalance, savings = economy?.savingsBalance,
        characterRes = pet?.artworkRes, characterMotionIntensity = pet?.motionIntensity ?: 1f,
        characterDescription = pet?.name,
        speech = if (withdrawing) "Можно взять часть монет. На цель останется меньше."
            else "Отложим монеты для следующих приключений!",
        pinFooter = true, scrollWholePage = true, contentSpacing = 10.dp,
        footer = {
            if (economy != null && !planningRequired) {
                AdventurePrimaryButton(
                    text = when {
                        state.depositWarning != null -> "Всё равно отложить"
                        state.withdrawal != null -> "Подтвердить снятие"
                        withdrawing -> if (valid) "Взять ${savingsCoins(checkNotNull(amount), true)}" else "Взять из копилки"
                        else -> if (valid) "Отложить ${savingsCoins(checkNotNull(amount), true)}" else "Пополнить копилку"
                    },
                    enabled = valid && state.error == null,
                    onClick = {
                        if (!state.saving && valid) {
                            focus.clearFocus()
                            onAction(when {
                                state.depositWarning != null -> EconomyAction.ConfirmDepositRisk
                                state.withdrawal != null -> EconomyAction.ConfirmWithdrawal
                                withdrawing -> EconomyAction.Withdraw(checkNotNull(amount))
                                else -> EconomyAction.Deposit(checkNotNull(amount))
                            })
                        }
                    },
                )
            }
        },
    ) {
        if (economy == null) {
            if (state.loading) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            AdventureBody(if (state.loading) "Открываем копилку…" else "Не удалось открыть копилку.")
            if (!state.loading) AdventurePrimaryButton("Повторить", { onAction(EconomyAction.Retry) })
        } else {
            SavingsGoalSummary(state.savingsTarget, economy.savingsBalance,
                onOpenGoal = { if (!state.saving) onOpenGoal() })
            SavingsDirections(withdrawing, enabled = !planningRequired, onSelect = {
                if (!state.saving) { focus.clearFocus(); onAction(EconomyAction.OpenTransfer(it)) }
            })
            if (planningRequired) {
                AdventureBody("Сначала распределим монеты в бюджете. Копилка останется на месте.")
                AdventurePrimaryButton("К бюджету", { if (!state.saving) onOpenBudget() })
            } else {
                SavingsCaption(if (withdrawing) "Добавим эти монеты к запасу на неожиданности"
                    else "Собираемся сберечь ${savingsCoins(economy.plan.savings, true)}")
                SavingsAmountInput(state.transferInput, maximum, readOnly = confirming,
                    onChange = { if (!state.saving) onAction(EconomyAction.UpdateTransferInput(it)) })
                // Confirmation and receipt stay in this form; neither opens another page.
                val receipt = state.transferReceipt
                if (receipt != null) {
                    Surface(color = Color(0xFFEDF4D9), shape = RoundedCornerShape(16.dp)) {
                        Text(if (receipt.withdrawing) "Вернули ${savingsCoins(receipt.amount, true)} в запас"
                            else "Отложили ${savingsCoins(receipt.amount, true)} в копилку",
                            Modifier.fillMaxWidth().padding(14.dp).semantics { liveRegion = LiveRegionMode.Polite },
                            color = GameInk, fontFamily = Nunito, fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp, lineHeight = 22.sp)
                    }
                } else if (valid) {
                    val value = checkNotNull(amount)
                    val availableAfter = if (withdrawing) economy.availableBalance + value else economy.availableBalance - value
                    val savingsAfter = if (withdrawing) economy.savingsBalance - value else economy.savingsBalance + value
                    SavingsPreviewContext(state, onAction) {
                        SavingsBalanceChange(economy.availableBalance, availableAfter, economy.savingsBalance, savingsAfter)
                        if (withdrawing) {
                            state.savingsTarget?.let { target ->
                                val remaining = target.remaining(savingsAfter)
                                SavingsCaption(if (remaining > 0) "После снятия на «${target.name}» останется накопить ${savingsCoins(remaining, true)}."
                                    else "На «${target.name}» по-прежнему хватает.")
                            }
                        } else EconomyOperations.depositQuote(economy, value).availableSourcesDescription()?.let {
                            SavingsCaption(it)
                        }
                        val warning = state.depositWarning
                        if (warning != null) Text(
                            "На еду до следующей недели может не хватить: останется ${warning.remainingBalance}, нужно ${warning.neededForFood} монет.",
                            color = Color(0xFF934113), fontFamily = Nunito, fontWeight = FontWeight.Bold,
                            fontSize = 14.sp, lineHeight = 20.sp)
                        else SavingsCaption("На еду до следующей недели нужно ${savingsCoins(state.knownNeeds, true)}.")
                    }
                } else {
                    SavingsCaption(when {
                        maximum == 0L -> if (withdrawing) "В копилке пока пусто. Сначала отложим монеты."
                            else "Пока нет доступных монет. Их можно заработать в делах."
                        state.transferInput.isNotEmpty() && amount == null -> "Введи сумму от 1 до $maximum."
                        amount != null && amount > maximum -> "Доступно для перевода: ${savingsCoins(maximum)}."
                        else -> "Выбери сумму — ниже увидишь, сколько монет останется."
                    })
                }
                if (confirming) AdventureQuietButton("Изменить сумму", {
                    if (!state.saving) onAction(if (state.withdrawal != null) EconomyAction.CancelWithdrawal
                        else EconomyAction.CancelDepositRisk)
                })
            }
        }
    }
    EconomyFeedback(state, onAction, showDepositWarning = false)
}

@Composable
private fun SavingsDirections(withdrawing: Boolean, enabled: Boolean, onSelect: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xFFECE7F3))
        .padding(4.dp).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(false to "Пополнить", true to "Взять").forEach { (direction, label) ->
            val selected = withdrawing == direction
            Box(Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                .background(if (selected) GameInk else Color.Transparent)
                .selectable(selected, enabled = enabled, role = Role.Tab, onClick = { onSelect(direction) })
                .heightIn(min = 44.dp).padding(horizontal = 8.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                Text(label, color = if (selected) GamePaper else GameInk, fontFamily = Nunito,
                    fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, lineHeight = 22.sp)
            }
        }
    }
}

@Composable
private fun SavingsPreviewContext(state: EconomyUiState, onAction: (EconomyAction) -> Unit,
    content: @Composable ColumnScope.() -> Unit) {
    val contextId = state.transferContextId
    key(contextId) {
        val requester = remember { BringIntoViewRequester() }
        LaunchedEffect(state.withdrawal, state.depositWarning) {
            if (state.withdrawal != null || state.depositWarning != null) {
                withFrameNanos { }
                requester.bringIntoView()
            }
        }
        Column(Modifier.fillMaxWidth().bringIntoViewRequester(requester).onGloballyPositioned { coordinates ->
            val visible = coordinates.boundsInWindow()
            if (visible.width >= coordinates.size.width - 1 && visible.height >= coordinates.size.height - 1 &&
                visible.width > 0 && visible.height > 0) contextId?.let {
                onAction(EconomyAction.TransferContextPresented(it))
            }
        }, verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}

@Composable
private fun SavingsAmountInput(input: String, maximum: Long, readOnly: Boolean, onChange: (String) -> Unit) {
    val amount = input.toLongOrNull()
    val muted = Color(0xFF625E80)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        @Composable fun StepButton(label: String, enabled: Boolean, onClick: () -> Unit) {
            FilledTonalButton(onClick, enabled = enabled, modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(16.dp), contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = AdventureLime, contentColor = GameInk,
                    disabledContainerColor = Color(0xFFECE7F3), disabledContentColor = muted)) {
                Text(label, fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 24.sp)
            }
        }
        StepButton("−", !readOnly && (amount ?: 0) > 0) { onChange(((amount ?: 0) - 5).coerceAtLeast(0).toString()) }
        OutlinedTextField(value = input, onValueChange = onChange,
            modifier = Modifier.weight(1f), readOnly = readOnly, singleLine = true,
            label = { Text("Монеты", fontFamily = Nunito) }, placeholder = { Text("0", Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = LocalTextStyle.current.copy(fontFamily = Rubik, fontWeight = FontWeight.Bold,
                fontSize = 24.sp, textAlign = TextAlign.Center),
            isError = input.isNotEmpty() && (amount == null || amount < 0 || amount > maximum),
            shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = GameInk, unfocusedTextColor = GameInk,
                cursorColor = GameInk, errorTextColor = Color(0xFFA52532),
                focusedContainerColor = GamePaper, unfocusedContainerColor = GamePaper, errorContainerColor = GamePaper,
                focusedLabelColor = GameInk, unfocusedLabelColor = muted, errorLabelColor = Color(0xFFA52532),
                focusedBorderColor = GameInk, unfocusedBorderColor = Color(0xFFD3CCDF)))
        StepButton("+", !readOnly && (amount ?: 0) < maximum) {
            val value = (amount ?: 0).coerceIn(0, maximum)
            onChange((value + minOf(5, maximum - value)).toString())
        }
    }
}

@Composable
private fun SavingsBalanceChange(availableBefore: Long, availableAfter: Long, savingsBefore: Long, savingsAfter: Long) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFFEFECF5)).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf("Доступно" to "$availableBefore → $availableAfter", "В копилке" to "$savingsBefore → $savingsAfter").forEach { (label, value) ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SavingsCaption(label)
                Text(value, color = GameInk, fontFamily = Nunito, fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp, lineHeight = 24.sp)
            }
        }
    }
}
