package ru.nksk.lctapp.feature.parents.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Source palette/type: lct26-parentsapp, 89d644f. Theme is scoped to the parent Activity.
private val ParentColors = lightColorScheme(
    primary = Color(0xFFA8E830), onPrimary = Color(0xFF20242E),
    primaryContainer = Color(0xFFE4F7C0), onPrimaryContainer = Color(0xFF20242E),
    secondary = Color(0xFFD9CFF8), onSecondary = Color(0xFF20242E),
    background = Color(0xFFEFEDE5), onBackground = Color(0xFF20242E),
    surface = Color.White, onSurface = Color(0xFF20242E),
    surfaceVariant = Color(0xFFECEAE0), onSurfaceVariant = Color(0xFF6E6A5E),
)

private val ParentTypography = Typography(
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp,
        lineHeight = 34.sp, letterSpacing = (-0.5).sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp,
        lineHeight = 26.sp, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp,
        lineHeight = 14.sp, letterSpacing = 0.2.sp),
)

@Composable
fun ParentsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ParentColors, typography = ParentTypography, content = content)
}
