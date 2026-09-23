package ru.nksk.lctapp.core.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.theme.*

internal val GamePaper = Color(0xFFFBF9EE)
internal val GameInk = Color(0xFF171440)

/** Figma narrative panel, adapted to native insets, scrolling, landscape and large text. */
@Composable
internal fun GameCardLayout(
    category: String,
    @DrawableRes scene: Int?,
    @DrawableRes character: Int?,
    onBack: () -> Unit,
    sceneDim: Float = 0f,
    characterDescription: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().background(AdventureNight)) {
        val landscape = maxWidth > maxHeight
        @Composable fun Stage(modifier: Modifier) {
            Box(modifier) {
                scene?.let { GameArtwork(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                if (sceneDim > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = sceneDim)))
                character?.let {
                    GameArtwork(it, characterDescription, Modifier.fillMaxHeight(0.78f).fillMaxWidth(0.7f)
                        .align(Alignment.BottomCenter).padding(bottom = 12.dp), contentScale = ContentScale.Fit)
                }
                Row(Modifier.statusBarsPadding().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalButton(onClick = onBack) { Text("Назад") }
                    Spacer(Modifier.width(8.dp))
                    Surface(color = AdventurePanel.copy(alpha = 0.85f), shape = RoundedCornerShape(20.dp)) {
                        Text(category, Modifier.padding(horizontal = 14.dp, vertical = 10.dp), color = Color.White,
                            fontFamily = Rubik, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        @Composable fun Panel(modifier: Modifier) {
            Surface(modifier, color = GamePaper, shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)) {
                key(category) {
                    Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
                }
            }
        }
        if (landscape) Row(Modifier.fillMaxSize().safeDrawingPadding()) {
            Stage(Modifier.weight(1f).fillMaxHeight())
            Panel(Modifier.weight(1f).fillMaxHeight())
        } else Column(Modifier.fillMaxSize()) {
            Stage(Modifier.fillMaxWidth().weight(0.48f))
            Panel(Modifier.fillMaxWidth().weight(0.52f))
        }
    }
}

@Composable
internal fun GameTitle(text: String) = Text(text, fontFamily = Rubik, fontWeight = FontWeight.ExtraBold,
    fontSize = 22.sp, color = GameInk)

@Composable
internal fun GameBody(text: String) = Text(text, fontFamily = Nunito, fontSize = 15.sp,
    lineHeight = 21.sp, color = Color(0xFF383363))

@Composable
internal fun GameButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick, Modifier.fillMaxWidth().heightIn(min = 56.dp), enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = AdventureLime, contentColor = AdventureNight),
        shape = RoundedCornerShape(28.dp)) {
        Text(text, fontFamily = Rubik, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}

@DrawableRes internal fun gameScene(key: String?): Int? = when (key) {
    "observatory" -> R.drawable.location_observatory
    "pier" -> R.drawable.location_pier_day
    "fair" -> R.drawable.location_fair_day
    "trail" -> R.drawable.location_trail_day
    "workshop" -> R.drawable.location_workshop_day
    "village", null -> R.drawable.menu_village
    else -> null
}

@DrawableRes internal fun gameCharacter(key: String?): Int? = when (key) {
    "caretaker" -> R.drawable.npc_caretaker_explaining
    "caretaker_lens" -> R.drawable.npc_caretaker_cleaning_lens
    else -> null
}
