package ru.nksk.lctapp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import ru.nksk.lctapp.core.ui.game.FinancialAdviceUi
import ru.nksk.lctapp.core.ui.theme.LCTAppTheme
import ru.nksk.lctapp.domain.pet.PetState
import ru.nksk.lctapp.domain.pet.PetVisualState
import ru.nksk.lctapp.feature.economy.ui.BudgetArticle
import ru.nksk.lctapp.feature.economy.ui.BudgetPlanScreen
import ru.nksk.lctapp.feature.economy.ui.BudgetUiState

@OptIn(ExperimentalTestApi::class)
class BudgetPlanScreenLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun longAdviceAndLargeTextDoNotOverlapTheFooterOrArticleControls() {
        val shown = base.copy(needs = 35, savings = 65, unallocated = 0,
            advice = FinancialAdviceUi("long", "Реши, сколько оставить на приятные покупки, большую цель и запас. ".repeat(5)))
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(360.dp, 560.dp))
                then DeviceConfigurationOverride.FontScale(1.6f)) {
                LCTAppTheme {
                    BudgetPlanScreen(shown, { _, _ -> }, {}, {},
                        pet = PetState(selectedLookId = "PLAIN", visualState = PetVisualState.NORMAL))
                }
            }
        }
        for (article in BudgetArticle.entries) {
            compose.onNodeWithTag("budget.article.${article.name}").performScrollTo().assertIsDisplayed()
            val heading = compose.onNodeWithTag("budget.article.${article.name}.heading").getUnclippedBoundsInRoot()
            val controls = compose.onNodeWithTag("budget.article.${article.name}.controls").getUnclippedBoundsInRoot()
            assertTrue("${article.name} title overlaps its controls", heading.bottom <= controls.top + 1.dp)
        }
        compose.onNodeWithTag("budget.plan.confirm").performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(64.dp)
        val adviser = compose.onNodeWithTag("budget.plan.adviser").getUnclippedBoundsInRoot()
        val footer = compose.onNodeWithTag("budget.plan.footer").getUnclippedBoundsInRoot()
        assertTrue(adviser.bottom > adviser.top)
        assertTrue(footer.bottom > footer.top)
        assertTrue("Advice overlaps the confirmation panel", adviser.bottom <= footer.top + 1.dp)
    }

    @Test fun foodReminderHidesWhenCoveredButOnlyActualExposureCarriesAcrossDraftEdits() {
        var shown by mutableStateOf(base)
        val presented = mutableSetOf<String>()
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(390.dp, 900.dp))) {
                LCTAppTheme {
                    BudgetPlanScreen(shown, { _, _ -> }, {}, {},
                        contextId = "${shown.planningId}:${shown.needs}",
                        onContextPresented = { presented += it })
                }
            }
        }
        compose.onNodeWithTag("budget.plan.food").assertIsDisplayed()
        compose.runOnIdle { assertTrue("plan:0" in presented) }
        compose.runOnIdle { shown = shown.copy(needs = 35, unallocated = 65) }
        compose.onNodeWithTag("budget.plan.food").assertDoesNotExist()
        compose.runOnIdle { assertTrue("plan:35" in presented) }
        compose.runOnIdle { shown = shown.copy(planningId = "other-plan") }
        compose.runOnIdle { assertFalse("other-plan:35" in presented) }
    }

    @Test fun anInitiallyFundedPlanDoesNotInventExposureOfTheHiddenFoodReminder() {
        val presented = mutableListOf<String>()
        compose.setContent {
            LCTAppTheme {
                BudgetPlanScreen(base.copy(needs = 35, unallocated = 65), { _, _ -> }, {}, {},
                    contextId = "already-funded", onContextPresented = { presented += it })
            }
        }
        compose.onNodeWithTag("budget.plan.food").assertDoesNotExist()
        compose.runOnIdle { assertTrue(presented.isEmpty()) }
    }

    private val base = BudgetUiState(0, 0, 0, 0, 100, 100,
        knownNeeds = 35, planningId = "plan")
}
