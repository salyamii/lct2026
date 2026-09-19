package ru.nksk.lctapp.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.nksk.lctapp.core.ui.theme.Nunito

/** A transient confirmation; SnackbarHost owns timing, animation and accessibility. */
@Composable
internal fun GameCompletionSnackbar(data: SnackbarData) {
    Snackbar(
        modifier = Modifier.padding(horizontal = 20.dp).widthIn(max = 420.dp),
        shape = RoundedCornerShape(24.dp),
        containerColor = GamePaper,
        contentColor = GameInk,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("✓", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(data.visuals.message, fontFamily = Nunito, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}
