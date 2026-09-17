package ru.nksk.lctapp.ui.coins.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Runtime class name retained for Navigation 3 Android saved-state compatibility. */
@Serializable
@SerialName("coins")
data object Coins : NavKey
