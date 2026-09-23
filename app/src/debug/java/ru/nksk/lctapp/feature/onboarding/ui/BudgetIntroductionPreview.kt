package ru.nksk.lctapp.feature.onboarding.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import ru.nksk.lctapp.R
import ru.nksk.lctapp.app.OnboardingBudgetArtwork
import ru.nksk.lctapp.app.customizationArtwork

@Preview(name = "Бюджет · Онбординг · Телефон", widthDp = 402, heightDp = 874, showBackground = true)
@Preview(name = "Бюджет · Онбординг · Крупный текст", widthDp = 360, heightDp = 640, fontScale = 1.5f, showBackground = true)
@Preview(name = "Бюджет · Онбординг · Альбомный", widthDp = 891, heightDp = 411, showBackground = true)
@Preview(name = "Бюджет · Онбординг · Планшет", widthDp = 1280, heightDp = 800, showBackground = true)
@Composable
private fun BudgetIntroductionPreview() {
    MaterialTheme {
        BudgetIntroductionScreen(
            artwork = customizationArtwork().copy(background = R.drawable.location_hill_day),
            categoryArtwork = { category, modifier -> OnboardingBudgetArtwork(category, modifier) },
            onBack = {}, onContinue = {},
        )
    }
}
