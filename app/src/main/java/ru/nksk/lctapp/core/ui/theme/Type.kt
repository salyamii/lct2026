package ru.nksk.lctapp.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.sp
import ru.nksk.lctapp.R

val Nunito = FontFamily(Font(R.font.nunito_extrabold, FontWeight.ExtraBold))
val Rubik = FontFamily(Font(R.font.rubik_extrabold, FontWeight.ExtraBold))

private val MenuText = TextStyle(
    fontFamily = Nunito,
    fontWeight = FontWeight.ExtraBold,
    letterSpacing = 0.sp,
)

// Game copy is currently Russian even when the device uses another language.
// Keep source strings intact; Android chooses hyphenation points during layout.
internal val GameTextWrapping = TextStyle(
    localeList = LocaleList("ru"),
    hyphens = Hyphens.Auto,
    lineBreak = LineBreak.Paragraph,
)

private val BaseTypography = Typography(
    headlineSmall = MenuText.copy(fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = MenuText.copy(fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = MenuText.copy(fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = MenuText.copy(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = MenuText.copy(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = MenuText.copy(fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = MenuText.copy(fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = MenuText.copy(fontSize = 11.sp, lineHeight = 15.sp),
)

val Typography = BaseTypography.run {
    copy(
        displayLarge = displayLarge.merge(GameTextWrapping),
        displayMedium = displayMedium.merge(GameTextWrapping),
        displaySmall = displaySmall.merge(GameTextWrapping),
        headlineLarge = headlineLarge.merge(GameTextWrapping),
        headlineMedium = headlineMedium.merge(GameTextWrapping),
        headlineSmall = headlineSmall.merge(GameTextWrapping),
        titleLarge = titleLarge.merge(GameTextWrapping),
        titleMedium = titleMedium.merge(GameTextWrapping),
        titleSmall = titleSmall.merge(GameTextWrapping),
        bodyLarge = bodyLarge.merge(GameTextWrapping),
        bodyMedium = bodyMedium.merge(GameTextWrapping),
        bodySmall = bodySmall.merge(GameTextWrapping),
        labelLarge = labelLarge.merge(GameTextWrapping),
        labelMedium = labelMedium.merge(GameTextWrapping),
        labelSmall = labelSmall.merge(GameTextWrapping),
    )
}
