package ru.nksk.lctapp.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** Selecting an answer and submitting it are separate, explicit actions. */
@Composable
internal fun GameQuizOption(text: String, selected: Boolean, enabled: Boolean, onSelect: () -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        color = if (selected) Color(0xFFF0F5DF) else Color.White,
        contentColor = GameInk,
        border = BorderStroke(if (selected) 2.dp else 1.dp,
            if (selected) GameInk else Color(0xFFD9D3E7))) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RadioButton(selected, onClick = null, enabled = enabled,
                colors = RadioButtonDefaults.colors(selectedColor = GameInk, unselectedColor = Color(0xFF837B9B)))
            Text(text, modifier = Modifier.weight(1f), color = GameInk, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
