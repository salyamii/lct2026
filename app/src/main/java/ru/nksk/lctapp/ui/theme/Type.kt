package ru.nksk.lctapp.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import ru.nksk.lctapp.R

val Nunito = FontFamily(Font(R.font.nunito_extrabold, FontWeight.ExtraBold))
val Rubik = FontFamily(Font(R.font.rubik_extrabold, FontWeight.ExtraBold))

private val MenuText = TextStyle(
    fontFamily = Nunito,
    fontWeight = FontWeight.ExtraBold,
    letterSpacing = 0.sp,
)

val Typography = Typography(
    headlineSmall = MenuText.copy(fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = MenuText.copy(fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = MenuText.copy(fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = MenuText.copy(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = MenuText.copy(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = MenuText.copy(fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = MenuText.copy(fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = MenuText.copy(fontSize = 11.sp, lineHeight = 15.sp),
)
