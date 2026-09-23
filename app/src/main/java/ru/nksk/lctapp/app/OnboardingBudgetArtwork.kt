package ru.nksk.lctapp.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.components.GameArtwork
import ru.nksk.lctapp.feature.onboarding.ui.OnboardingBudgetCategory

@Composable
internal fun OnboardingBudgetArtwork(category: OnboardingBudgetCategory, modifier: Modifier) {
    GameArtwork(
        resource = when (category) {
            OnboardingBudgetCategory.Needs -> R.drawable.budget_needs
            OnboardingBudgetCategory.Wants -> R.drawable.budget_wants
            OnboardingBudgetCategory.Savings -> R.drawable.budget_savings
            OnboardingBudgetCategory.Reserve -> R.drawable.budget_reserve
        },
        contentDescription = null,
        modifier = modifier,
    )
}
