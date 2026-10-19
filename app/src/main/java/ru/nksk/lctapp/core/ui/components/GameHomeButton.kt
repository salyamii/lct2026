package ru.nksk.lctapp.core.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.theme.GameLabelTextWrapping
import ru.nksk.lctapp.core.ui.theme.Nunito

/** One labelled touch target; the owner decides how to leave its current operation safely. */
@Composable
internal fun GameHomeButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showLabel: Boolean = true,
) {
    if (!showLabel) {
        GamePlaque(modifier.size(52.dp).alpha(if (enabled) 1f else .5f),
            light = true, fixedEdges = true, contentPadding = PaddingValues(0.dp)) {
            IconButton(onClick, Modifier.fillMaxSize(), enabled = enabled) {
                Icon(painterResource(R.drawable.game_home), contentDescription = "Домой",
                    modifier = Modifier.size(22.dp), tint = GameInk)
            }
        }
        return
    }
    GamePlaque(modifier.heightIn(min = 52.dp).alpha(if (enabled) 1f else .5f),
        light = true, fixedEdges = true,
        contentPadding = PaddingValues(0.dp)) {
        Button(onClick, Modifier.heightIn(min = 52.dp).semantics { contentDescription = "Домой" },
            enabled = enabled, shape = RoundedCornerShape(24.dp), elevation = null,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent, contentColor = GameInk,
                disabledContentColor = GameInk)) {
            Icon(painterResource(R.drawable.game_home), contentDescription = null,
                modifier = Modifier.size(22.dp), tint = GameInk)
            Spacer(Modifier.width(6.dp))
            Text("Домой", Modifier.clearAndSetSemantics {},
                style = GameLabelTextWrapping.merge(TextStyle(fontFamily = Nunito,
                    fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, lineHeight = 18.sp)))
        }
    }
}
