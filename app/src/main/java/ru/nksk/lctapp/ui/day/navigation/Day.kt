package ru.nksk.lctapp.ui.day.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Runtime class name retained for Navigation 3 Android saved-state compatibility. */
@Serializable
@SerialName("day")
data object Day : NavKey
