package ru.nksk.lctapp.ui.tasks.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Runtime class name retained for Navigation 3 Android saved-state compatibility. */
@Serializable
@SerialName("tasks")
data object Tasks : NavKey
