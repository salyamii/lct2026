package ru.nksk.lctapp.feature.menu.ui

import ru.nksk.lctapp.domain.pet.PetState
import ru.nksk.lctapp.domain.pet.PetVisualState
import ru.nksk.lctapp.core.ui.components.MealChoiceUiState
import ru.nksk.lctapp.core.ui.components.PetIndicatorsUiState
import ru.nksk.lctapp.core.ui.components.PetMoodUiState
import ru.nksk.lctapp.core.ui.components.PetSatietyUiState

/** Values rendered by the menu; this is presentation state, not a saved game. */
data class MainMenuUiState(
    val coins: Long,
    val backgroundRes: Int = ru.nksk.lctapp.R.drawable.menu_village,
    val completedGoals: Int,
    val totalGoals: Int,
    val pet: MainMenuPetUiState,
    val dayStatus: String? = null,
    val indicators: PetIndicatorsUiState? = null,
    val continueLabel: String? = null,
    val canFeed: Boolean = false,
    val busy: Boolean = false,
    val notice: String? = null,
    val mealPrice: Long? = null,
    val showFreeMeal: Boolean = false,
    val showMeals: Boolean = false,
    val meals: List<MealChoiceUiState> = emptyList(),
    val goalTitle: String = "Выбрать большую цель",
    val budget: MenuBudgetUiState? = null,
    val savings: MainMenuSavingsUiState? = null,
    val spendingPreview: String? = null,
    val canRestartCampaign: Boolean = false,
    val nameEditor: PetNameEditorUiState? = null,
)

/** A selected, unowned item and money already in the piggy bank, never a draft allocation. */
data class MainMenuSavingsUiState(
    val targetId: String,
    val title: String,
    val saved: Long,
    val total: Long,
)

data class PetNameEditorUiState(
    val input: String,
    val error: String? = null,
    val retryPending: Boolean = false,
)

/** Last confirmed plan and the two real balances, projected from the same saved economy. */
data class MenuBudgetUiState(
    val needs: Long,
    val wants: Long,
    val savings: Long,
    val reserve: Long,
    val available: Long = needs + wants + savings + reserve,
    val actualSavings: Long = 0,
    val unallocated: Long = 0,
)

/** Preview-only presentation fixture. Runtime state comes from MainMenuViewModel. */
internal val MainMenuPreviewState = MainMenuUiState(
    coins = 140L,
    budget = MenuBudgetUiState(needs = 40, wants = 30, savings = 50, reserve = 20, actualSavings = 12),
    savings = MainMenuSavingsUiState("preview-tripod", "Штатив", saved = 12, total = 36),
    completedGoals = 0,
    totalGoals = 4,
    pet = PetState("PLAIN", PetVisualState.NORMAL).toMainMenuPetUiState(),
    dayStatus = "День 2",
    indicators = PetIndicatorsUiState(3, 5, "Устал", "Дела расходуют энергию. Отдых помогает её восстановить.",
        PetSatietyUiState.NOT_EATEN, PetMoodUiState.CALM),
    canFeed = true,
)

/** UI intents are handled by the app host; the menu stays independent of navigation. */
enum class MainMenuAction {
    Gear, Tasks, Goal, Coins, Village, ContinueDay, Feed, Finance, Savings, CampaignArchive,
}
