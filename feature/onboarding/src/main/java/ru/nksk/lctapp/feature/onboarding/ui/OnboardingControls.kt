package ru.nksk.lctapp.feature.onboarding.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private class OnboardingControls(
    val primary: @Composable (String, Boolean, () -> Unit, Modifier) -> Unit,
    val back: @Composable (Boolean, () -> Unit, Modifier, String) -> Unit,
    val title: @Composable (String, Modifier) -> Unit,
)

private val LocalOnboardingControls = staticCompositionLocalOf<OnboardingControls?> { null }

/** The application supplies game artwork; the feature keeps native controls in standalone previews. */
@Composable
fun ProvideOnboardingControls(
    primary: @Composable (text: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) -> Unit,
    back: @Composable (enabled: Boolean, onClick: () -> Unit, modifier: Modifier, contentDescription: String) -> Unit,
    title: @Composable (text: String, modifier: Modifier) -> Unit,
    content: @Composable () -> Unit,
) {
    val controls = remember(primary, back, title) { OnboardingControls(primary, back, title) }
    CompositionLocalProvider(LocalOnboardingControls provides controls, content = content)
}

@Composable
internal fun OnboardingPrimaryButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val renderer = LocalOnboardingControls.current
    if (renderer != null) renderer.primary(text, enabled, onClick, modifier)
    else Button(onClick, enabled = enabled, modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = RoundedCornerShape(18.dp), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)) {
        Text(text, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, lineHeight = 22.sp,
            textAlign = TextAlign.Center)
    }
}

@Composable
internal fun OnboardingBackButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = "Назад",
) {
    val renderer = LocalOnboardingControls.current
    if (renderer != null) renderer.back(enabled, onClick, modifier, contentDescription)
    else IconButton(onClick, modifier.size(52.dp).semantics { this.contentDescription = contentDescription },
        enabled = enabled) {
        Text("‹", Modifier.clearAndSetSemantics {}, fontSize = 32.sp)
    }
}

@Composable
internal fun OnboardingSceneTitle(text: String, modifier: Modifier = Modifier) {
    val renderer = LocalOnboardingControls.current
    if (renderer != null) renderer.title(text, modifier)
    else Text(text, modifier, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, lineHeight = 22.sp)
}
