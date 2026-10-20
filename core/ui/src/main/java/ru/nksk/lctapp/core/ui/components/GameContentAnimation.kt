package ru.nksk.lctapp.core.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.ui.Modifier

/** Natural-height content pushes adjacent sections gradually when its copy or controls change. */
fun Modifier.animateGameContentSize(): Modifier = animateContentSize(animationSpec = tween(220))
