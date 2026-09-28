package ru.nksk.lctapp.feature.onboarding.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Small windows, the keyboard and large text must grow content instead of compressing actions. */
@Composable
internal fun needsOnboardingScroll(availableHeight: Dp): Boolean =
    availableHeight < 480.dp || LocalDensity.current.fontScale >= 1.5f
