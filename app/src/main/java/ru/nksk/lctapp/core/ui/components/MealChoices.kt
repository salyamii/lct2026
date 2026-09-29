package ru.nksk.lctapp.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.theme.Nunito

data class MealChoiceUiState(
    val id: String,
    val label: String,
    val enabled: Boolean,
    val consequence: String? = null,
    val priceLabel: String? = null,
)

/** Shared food choices; screens supply already projected effects and guarded callbacks. */
@Composable
internal fun MealChoices(choices: List<MealChoiceUiState>, busy: Boolean, onChoose: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        choices.forEach { meal ->
            Surface(onClick = { onChoose(meal.id) }, enabled = meal.enabled && !busy,
                modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}
                    .alpha(if (meal.enabled) 1f else .55f),
                color = Color.White, contentColor = GameInk, shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, GameInk.copy(alpha = .12f))) {
                Row(Modifier.fillMaxWidth().heightIn(min = 104.dp).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Price moves to its own line on narrow screens or with large system text.
                        FlowRow(Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(meal.label, color = GameInk, fontFamily = Nunito,
                                fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, lineHeight = 22.sp)
                            meal.priceLabel?.let { price ->
                                Text(price, color = GameInk.copy(alpha = .72f), fontFamily = Nunito,
                                    fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 22.sp)
                            }
                        }
                        meal.consequence?.let {
                            Text(it, color = GameInk.copy(alpha = .76f), fontFamily = Nunito,
                                fontSize = 15.sp, lineHeight = 21.sp)
                        }
                    }
                    Icon(painterResource(R.drawable.menu_chevron), contentDescription = null,
                        modifier = Modifier.size(18.dp), tint = GameInk.copy(alpha = .6f))
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun MealSelectionDialog(choices: List<MealChoiceUiState>, busy: Boolean,
    onChoose: (String) -> Unit, onDismiss: () -> Unit, message: String? = null,
    extraContent: @Composable () -> Unit = {}) {
    val currentBusy by rememberUpdatedState(busy)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || !currentBusy })
    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }, sheetState = sheetState,
        sheetMaxWidth = 560.dp, containerColor = GamePaper, contentColor = GameInk,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            AdventureHeading("Выбери обед")
            Column(Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                message?.let { AdventureBody(it) }
                MealChoices(choices, busy, onChoose)
                extraContent()
            }
            AdventureQuietButton("Вернуться", onDismiss, Modifier.fillMaxWidth(), enabled = !busy)
        }
    }
}
