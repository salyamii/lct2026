package ru.nksk.lctapp.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.nksk.lctapp.R
import ru.nksk.lctapp.app.navigation.AppStartupState
import ru.nksk.lctapp.app.navigation.AppStartupViewModel
import ru.nksk.lctapp.app.navigation.LctNavHost
import ru.nksk.lctapp.core.ui.theme.AdventureNight
import ru.nksk.lctapp.core.ui.theme.LCTAppTheme
import ru.nksk.lctapp.core.ui.theme.Nunito
import ru.nksk.lctapp.core.ui.theme.Rubik
import ru.nksk.lctapp.feature.onboarding.navigation.OnboardingEntry
import ru.nksk.lctapp.feature.onboarding.ui.OnboardingArtwork

/** Composition root for shared presentation and app-owned navigation. */
@Composable
fun LctApp() {
    LCTAppTheme {
        val startup: AppStartupViewModel = hiltViewModel()
        val state by startup.uiState.collectAsStateWithLifecycle()
        when (val current = state) {
            AppStartupState.Ready -> LctNavHost()
            is AppStartupState.Choose -> OnboardingEntry(
                artwork = OnboardingArtwork(
                    background = R.drawable.onboarding_castle,
                    fox = R.drawable.onboarding_ryzhik,
                    owl = R.drawable.npc_luna_body,
                    axolotl = R.drawable.onboarding_tiko_unavailable,
                    groundShadow = R.drawable.menu_ground_shadow,
                    titleFont = Rubik,
                    bodyFont = Nunito,
                ),
                saving = current.saving,
                saveFailed = current.failed,
                onStartAdventure = startup::startAdventure,
            )
            else -> Box(Modifier.fillMaxSize().background(AdventureNight).safeDrawingPadding(),
                contentAlignment = Alignment.Center) {
                if (current == AppStartupState.Loading) CircularProgressIndicator()
                else Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Не удалось прочитать сохранение", color = androidx.compose.ui.graphics.Color.White)
                    Button(onClick = startup::retry) { Text("Повторить") }
                }
            }
        }
    }
}
