package ru.nksk.lctapp.feature.gear.ui

import ru.nksk.lctapp.core.ui.components.GameArtwork
import ru.nksk.lctapp.core.ui.components.GameActionButton
import ru.nksk.lctapp.core.ui.components.GameActionButtonDefaults
import ru.nksk.lctapp.core.ui.components.GameActionStyle
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.style.TextOverflow
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
    onOpen: (String) -> Unit = {}, reserveEquipSlot: Boolean = item.lookId != null) {
    Surface(color = GearColors.Card,
        shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, GearColors.Border)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.fillMaxWidth().height(140.dp)
                    .background(GearColors.Cream, RoundedCornerShape(12.dp)).padding(4.dp),
                contentAlignment = Alignment.Center,
            ) {
                val artwork = item.artworkRes
                if (artwork != null) GameArtwork(artwork, item.name, Modifier.size(132.dp), contentScale = ContentScale.Fit)
                else Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        item.description.ifBlank { item.name }, color = GearColors.Ink,
                        fontFamily = Nunito, fontSize = 13.sp, textAlign = TextAlign.Center,
                        maxLines = 4, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(item.name, color = GearColors.Ink, fontFamily = Rubik, fontWeight = FontWeight.Medium,
                fontSize = 16.sp, lineHeight = 22.sp, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Column(verticalArrangement = Arrangement.spacedBy(GameActionButtonDefaults.Spacing)) {
                GameActionButton("Рассмотреть", { onOpen(item.occurrenceId) }, style = GameActionStyle.SECONDARY,
                    minHeight = 48.dp, contentColor = GearColors.Ink,
                    textStyle = GameActionButtonDefaults.QuietText.copy(fontSize = 14.sp, lineHeight = 20.sp))
                if (item.lookId != null) {
                    GameActionButton(stringResource(if (item.equipped) R.string.gear_unequip else R.string.gear_equip),
                        { onEquip(if (item.equipped) "PLAIN" else item.lookId) }, interactionBlocked = !enabled,
                        style = if (item.equipped) GameActionStyle.SECONDARY else GameActionStyle.PRIMARY,
                        minHeight = 48.dp, contentColor = GearColors.Ink,
                        textStyle = GameActionButtonDefaults.QuietText.copy(fontSize = 14.sp, lineHeight = 20.sp))
                } else if (reserveEquipSlot) {
                    // Keep neighbouring accessory actions aligned without inventing an equip action.
                    Box(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center) {
                        Text("", fontFamily = Nunito, fontSize = 14.sp, lineHeight = 20.sp, minLines = 1, maxLines = 1)
                    }
                }
            }
        }
    }
}
