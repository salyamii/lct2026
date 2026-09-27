package ru.nksk.lctapp.feature.economy.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.components.*
import ru.nksk.lctapp.core.ui.game.goalItemArtwork
import ru.nksk.lctapp.core.ui.theme.AdventureLime
import ru.nksk.lctapp.core.ui.theme.Nunito

/** The target is a compact link inside the transfer page, not another hub. */
@Composable
internal fun SavingsGoalSummary(target: SavingsTargetUi?, savings: Long, onOpenGoal: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
        .clickable(role = Role.Button, onClickLabel = "Открыть цели", onClick = onOpenGoal)
        .padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        GameArtwork(target?.let { goalItemArtwork(it.id) } ?: R.drawable.budget_savings, null,
            Modifier.size(48.dp), contentScale = ContentScale.Fit)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(target?.name ?: "Выбрать цель", color = GameInk, fontFamily = Nunito,
                fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, lineHeight = 24.sp)
            if (target != null) {
                LinearProgressIndicator(
                    progress = { if (target.price == 0L) 1f else (savings.toFloat() / target.price).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(4.dp)),
                    color = AdventureLime, trackColor = Color(0xFFE5E0EC), gapSize = 0.dp, drawStopIndicator = {},
                )
                SavingsCaption(if (target.remaining(savings) == 0L) "На покупку хватает"
                    else "Ещё ${savingsCoins(target.remaining(savings))} до покупки")
            } else SavingsCaption("Копилка общая для всех целей")
        }
        Icon(painterResource(R.drawable.menu_chevron), null, Modifier.size(24.dp), tint = GameInk)
    }
}

@Composable
internal fun SavingsCaption(text: String) {
    Text(text, color = Color(0xFF514972), fontFamily = Nunito, fontSize = 14.sp, lineHeight = 20.sp)
}

internal fun savingsCoins(amount: Long, accusative: Boolean = false): String = "$amount " + when {
    amount % 100 in 11..14 -> "монет"
    amount % 10 == 1L -> if (accusative) "монету" else "монета"
    amount % 10 in 2..4 -> "монеты"
    else -> "монет"
}

