package ru.nksk.lctapp.feature.onboarding.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import ru.nksk.lctapp.app.accessoryArtwork
import ru.nksk.lctapp.app.customizationArtwork
import ru.nksk.lctapp.app.introductionIcons

@Composable
private fun AdventureIntroductionDesign(
    fur: CharacterFur = CharacterFur.Copper,
    accessory: OnboardingAccessory = OnboardingAccessory.Backpack,
) {
    val art = customizationArtwork()
    AdventureIntroductionScreen(art, accessoryArtwork(art).portraits.getValue(accessory).getValue(fur),
        introductionIcons(), onBack = {}, onStart = {})
}

@Preview(name = "01 · Знакомство · Телефон", widthDp = 402, heightDp = 874, showBackground = true)
@Composable
private fun IntroductionPhonePreview() = AdventureIntroductionDesign()

@Preview(name = "02 · Знакомство · Песочный / бандана", widthDp = 402, heightDp = 874, showBackground = true)
@Composable
private fun IntroductionBandanaPreview() = AdventureIntroductionDesign(CharacterFur.Sand, OnboardingAccessory.Bandana)

@Preview(name = "03 · Компактный · Крупный текст", widthDp = 360, heightDp = 740, fontScale = 1.3f, showBackground = true)
@Composable
private fun IntroductionCompactPreview() = AdventureIntroductionDesign(CharacterFur.Russet, OnboardingAccessory.None)

@Preview(name = "04 · Телефон · Альбомный", device = "spec:width=891dp,height=411dp,dpi=420,isRound=false,chinSize=0dp,orientation=landscape", showSystemUi = true)
@Composable
private fun IntroductionLandscapePreview() = AdventureIntroductionDesign()

@Preview(name = "05 · Планшет · Альбомный", widthDp = 1280, heightDp = 800, showBackground = true)
@Composable
private fun IntroductionTabletPreview() = AdventureIntroductionDesign(CharacterFur.Sand, OnboardingAccessory.Bandana)

@Preview(name = "06 · Планшет · Портретный", widthDp = 800, heightDp = 1280, showBackground = true)
@Composable
private fun IntroductionTabletPortraitPreview() = AdventureIntroductionDesign()
