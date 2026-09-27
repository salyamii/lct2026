package ru.nksk.lctapp.feature.gear.ui

import ru.nksk.lctapp.core.ui.components.GameArtwork
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.theme.Nunito
import ru.nksk.lctapp.core.ui.theme.Rubik

internal object GearColors {
    val Cream = Color(0xFFF7F1E4)
    val Card = Color(0xFFFFFCF5)
    val Ink = Color(0xFF33306E)
    val Secondary = Color(0xFF716B8B)
    val Border = Color(0xFFE5DCC6)
    val Gold = Color(0xFFF1E3B2)
}

@Composable
internal fun GearSectionHeading(title: String, count: Int) {
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            title, modifier = Modifier.weight(1f).semantics { heading() },
            color = GearColors.Ink, fontFamily = Rubik, fontWeight = FontWeight.Bold, fontSize = 19.sp,
        )
        Text(
            count.toString(), color = GearColors.Ink, fontFamily = Nunito, fontWeight = FontWeight.Bold,
            modifier = Modifier.background(GearColors.Gold, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

@Composable
internal fun GearEmptySection(title: String, description: String) {
    Surface(color = GearColors.Card, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, GearColors.Border)) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, color = GearColors.Ink, fontFamily = Rubik, fontWeight = FontWeight.Medium, fontSize = 16.sp)
            Text(description, color = GearColors.Secondary, fontFamily = Nunito, fontSize = 14.sp)
        }
    }
}

@Composable
internal fun GearItemCard(item: GearItemUiState, enabled: Boolean = true, onEquip: (String) -> Unit = {},
    onOpen: (String) -> Unit = {}) {
    Surface(color = GearColors.Card,
        shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, GearColors.Border)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.fillMaxWidth()
                    .background(GearColors.Cream, RoundedCornerShape(12.dp)).padding(4.dp),
                contentAlignment = Alignment.Center,
            ) {
                val artwork = item.artworkRes
                if (artwork != null) GameArtwork(artwork, item.name, Modifier.size(132.dp), contentScale = ContentScale.Fit)
                else Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        item.description.ifBlank { item.name }, color = GearColors.Ink,
                        fontFamily = Nunito, fontSize = 13.sp, textAlign = TextAlign.Center,
                    )
                }
            }
            Text(item.name, color = GearColors.Ink, fontFamily = Rubik, fontWeight = FontWeight.Medium, fontSize = 16.sp)
            OutlinedButton(onClick = { onOpen(item.occurrenceId) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, GearColors.Ink.copy(alpha = .35f)),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = GearColors.Ink)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)) {
                    Text("Рассмотреть", Modifier.weight(1f, fill = false), textAlign = TextAlign.Center, fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                    Icon(painterResource(R.drawable.menu_chevron), null, Modifier.size(14.dp))
                }
            }
            if (item.lookId != null) {
                Button(enabled = enabled, onClick = { onEquip(if (item.equipped) "PLAIN" else item.lookId) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (item.equipped) Color(0xFFE6E1F0) else Color(0xFFA5E91F),
                        contentColor = GearColors.Ink,
                        disabledContainerColor = Color(0xFFE6E1F0), disabledContentColor = GearColors.Secondary)) {
                    Text(stringResource(if (item.equipped) R.string.gear_unequip else R.string.gear_equip),
                        fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                }
            }
            if (item.priceCoins != null) {
                Row(
                    Modifier.fillMaxWidth().background(GearColors.Gold, RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GameArtwork(R.drawable.menu_coin, null, Modifier.size(20.dp), contentScale = ContentScale.Fit)
                    Text(
                        gearPriceText(item.priceCoins), color = GearColors.Ink,
                        fontFamily = Nunito, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    )
                }
            } else if (!item.isStarterAccessory) {
                Text(stringResource(R.string.gear_price_unknown), color = GearColors.Secondary, fontFamily = Nunito, fontSize = 12.sp)
            }
        }
    }
}

private fun gearPriceText(amount: Long): String {
    val unit = when {
        amount % 100 in 11L..14L -> "монет"
        amount % 10 == 1L -> "монета"
        amount % 10 in 2L..4L -> "монеты"
        else -> "монет"
    }
    return "$amount $unit"
}
