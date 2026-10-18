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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.theme.GameLabelTextWrapping
import ru.nksk.lctapp.core.ui.theme.Nunito

/** Scene controls share the wooden artwork while keeping native input and accessible labels. */
@Composable
internal fun GameBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String = "Назад",
) {
    GameDirectionButton(onClick, modifier, enabled, contentDescription, rotation = 180f)
}

/** A compact labelled forward action with one frame and one accessible touch target. */
@Composable
internal fun GameForwardAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    GamePlaque(modifier.heightIn(min = 52.dp).alpha(if (enabled) 1f else .5f),
        light = true, fixedEdges = true, fillVisibleHeight = true,
        contentPadding = PaddingValues(0.dp)) {
        Button(onClick, Modifier.heightIn(min = 52.dp).semantics { contentDescription = label },
            enabled = enabled, shape = RoundedCornerShape(24.dp), elevation = null,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent, contentColor = GameInk,
                disabledContentColor = GameInk)) {
            Text(label, Modifier.weight(1f, fill = false).clearAndSetSemantics {},
                style = GameLabelTextWrapping.merge(TextStyle(fontFamily = Nunito,
                    fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, lineHeight = 18.sp)),
                textAlign = TextAlign.Center)
            Spacer(Modifier.width(8.dp))
            Icon(painterResource(R.drawable.menu_chevron), contentDescription = null,
                Modifier.size(20.dp), tint = GameInk)
        }
    }
}

@Composable
private fun GameDirectionButton(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    contentDescription: String,
    rotation: Float,
) {
    GamePlaque(modifier.size(52.dp).alpha(if (enabled) 1f else .5f),
        light = true, fixedEdges = true, contentPadding = PaddingValues(0.dp)) {
        IconButton(onClick, Modifier.fillMaxSize(), enabled = enabled) {
            Icon(painterResource(R.drawable.menu_chevron), contentDescription,
                Modifier.size(24.dp).rotate(rotation), tint = GameInk)
        }
    }
}

@Composable
internal fun GameSceneTitle(title: String, modifier: Modifier = Modifier) {
    GamePlaque(modifier.heightIn(min = 52.dp), artworkRes = R.drawable.menu_name_plaque_v3,
        fixedEdges = true,
        contentPadding = PaddingValues(start = 32.dp, end = 20.dp, top = 18.dp, bottom = 18.dp)) {
        Text(title, color = GameInk, fontFamily = Nunito, fontWeight = FontWeight.ExtraBold,
            fontSize = 16.sp, lineHeight = 22.sp)
    }
}
