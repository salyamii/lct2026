package ru.nksk.lctapp.app

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
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
import ru.nksk.lctapp.feature.onboarding.ui.AdventureGoalBriefingScreen
import ru.nksk.lctapp.feature.onboarding.ui.AdventureGoalSelectionScreen
import ru.nksk.lctapp.feature.onboarding.ui.BudgetIntroductionScreen
import ru.nksk.lctapp.feature.onboarding.ui.CustomizationScreen
import ru.nksk.lctapp.feature.onboarding.ui.AccessoryCustomizationScreen
import ru.nksk.lctapp.feature.onboarding.ui.AccessoryCustomizationUiState
import ru.nksk.lctapp.feature.onboarding.ui.OnboardingAccessory
import ru.nksk.lctapp.feature.onboarding.ui.CustomizationUiState
import ru.nksk.lctapp.feature.onboarding.ui.CharacterFur
import ru.nksk.lctapp.feature.onboarding.ui.CharacterTemperament
import ru.nksk.lctapp.domain.pet.PetFur
import ru.nksk.lctapp.domain.pet.PetTemperament
import ru.nksk.lctapp.app.presentation.PetReactionViewModel
import ru.nksk.lctapp.app.presentation.MediaPlaybackViewModel
import ru.nksk.lctapp.core.ui.game.LocalLivePetReaction
import ru.nksk.lctapp.core.ui.media.MediaPlaybackHost
import ru.nksk.lctapp.core.ui.media.LocalMediaPlayback

/** Composition root for shared presentation and app-owned navigation. */
@Composable
fun LctApp() {
    AppDebugOverlay { debugSettingsButton ->
        LctAppContent(debugSettingsButton)
    }
}

@Composable
private fun LctAppContent(debugSettingsButton: (@Composable () -> Unit)?) {
    LCTAppTheme {
        val startup: AppStartupViewModel = hiltViewModel()
        val state by startup.uiState.collectAsStateWithLifecycle()
        val media: MediaPlaybackViewModel = hiltViewModel()
        val mediaState by media.uiState.collectAsStateWithLifecycle()
        MediaPlaybackHost(soundEnabled = mediaState.soundEnabled, factory = media.playerFactory,
            audio = media.audioController, musicCueKey = mediaState.musicCueKey) {
            val audio = LocalMediaPlayback.current
            LaunchedEffect(media, audio) {
                media.actionCues.collect { audio?.playAction(it.id, it.cues) }
            }
            when (val current = state) {
                AppStartupState.Ready -> {
                    val reactions: PetReactionViewModel = hiltViewModel()
                    val reaction by reactions.uiState.collectAsStateWithLifecycle()
                    CompositionLocalProvider(LocalLivePetReaction provides reaction) {
                        LctNavHost(debugSettingsButton = debugSettingsButton)
                    }
                }
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
                is AppStartupState.IntroVideo -> IntroVideoEntry(
                    state = current,
                    media = mediaState,
                    onPositionChanged = startup::updateIntroVideoPosition,
                    onFinished = startup::finishIntroVideo,
                    onPlaybackError = startup::introVideoFailed,
                    onSoundChanged = media::setSoundEnabled,
                    onSoundRetry = media::retry,
                )
                is AppStartupState.Customize -> {
                    BackHandler { startup.backToCharacters() }
                    CustomizationScreen(
                        state = CustomizationUiState(current.draft.name,
                            CharacterTemperament.valueOf(current.draft.temperament.name),
                            CharacterFur.valueOf(current.draft.fur.name)),
                        artwork = customizationArtwork(),
                        onNameChange = startup::editName,
                        onTemperamentChange = { startup.editTemperament(PetTemperament.valueOf(it.name)) },
                        onFurChange = { startup.editFur(PetFur.valueOf(it.name)) },
                        onBack = startup::backToCharacters,
                        onContinue = startup::finishCustomization,
                        saving = current.saving,
                        saveFailed = current.failed,
                    )
                }
                is AppStartupState.Accessories -> {
                    BackHandler { startup.backToCustomization() }
                    val art = customizationArtwork()
                    AccessoryCustomizationScreen(
                        state = AccessoryCustomizationUiState(
                            name = current.draft.profile.name,
                            fur = CharacterFur.valueOf(current.draft.profile.fur.name),
                            accessory = OnboardingAccessory.entries.first { it.id == current.draft.accessoryId },
                        ),
                        artwork = art,
                        accessories = accessoryArtwork(art),
                        onSelect = { startup.selectAccessory(it.id) },
                        onBack = startup::backToCustomization,
                        onApply = startup::confirmAccessory,
                        saving = current.saving,
                        saveFailed = current.failed,
                    )
                }
                is AppStartupState.GoalBriefing -> {
                    BackHandler { startup.backToAccessories() }
                    AdventureGoalBriefingScreen(
                        artwork = customizationArtwork().copy(background = R.drawable.location_hill_day),
                        chapterPreviewRes = R.drawable.goal_preview_stargazing,
                        onBack = startup::backToAccessories,
                        onContinue = startup::continueToGoals,
                        saving = current.saving,
                        saveFailed = current.failed,
                    )
                }
                is AppStartupState.GoalSelection -> {
                    BackHandler { startup.backToGoalBriefing() }
                    AdventureGoalSelectionScreen(
                        artwork = customizationArtwork().copy(background = R.drawable.location_hill_day),
                        goals = onboardingGoalOptions().filter { it.id in startup.savingItemIds },
                        selectedGoalId = current.draft.savingItemId,
                        onSelect = startup::selectGoal,
                        onBack = startup::backToGoalBriefing,
                        onConfirm = startup::confirmGoal,
                        saving = current.saving,
                        saveFailed = current.failed,
                    )
                }
                is AppStartupState.Introduction -> {
                    BackHandler { startup.backToGoals() }
                    BudgetIntroductionScreen(
                        artwork = customizationArtwork().copy(background = R.drawable.location_hill_day),
                        categoryArtwork = { category, modifier -> OnboardingBudgetArtwork(category, modifier) },
                        onBack = startup::backToGoals,
                        onContinue = startup::finishOnboarding,
                        saving = current.saving,
                        saveFailed = current.failed,
                    )
                }
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
}
