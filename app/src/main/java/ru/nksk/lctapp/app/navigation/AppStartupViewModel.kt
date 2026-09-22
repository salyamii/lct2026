package ru.nksk.lctapp.app.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.nksk.lctapp.domain.engine.GameSession
import ru.nksk.lctapp.domain.onboarding.*
import ru.nksk.lctapp.domain.pet.toPetState
import ru.nksk.lctapp.domain.pet.PetCustomization
import ru.nksk.lctapp.domain.pet.PetFur
import ru.nksk.lctapp.domain.pet.PetState
import ru.nksk.lctapp.domain.pet.PetTemperament
import ru.nksk.lctapp.domain.pet.PetVisualState

internal sealed interface AppStartupState {
    data object Loading : AppStartupState
    data class Choose(val saving: Boolean = false, val failed: Boolean = false) : AppStartupState
    data class Customize(val draft: PetCustomization, val saving: Boolean = false, val failed: Boolean = false) : AppStartupState
    data class Accessories(val draft: OnboardingDraft, val saving: Boolean = false, val failed: Boolean = false) : AppStartupState
    data class GoalSelection(val draft: OnboardingDraft, val saving: Boolean = false, val failed: Boolean = false) : AppStartupState
    data class Introduction(val draft: OnboardingDraft, val saving: Boolean = false, val failed: Boolean = false) : AppStartupState
    data object Ready : AppStartupState
    data object Error : AppStartupState
}

/** The app owns onboarding steps; a committed game is the completion marker. */
@HiltViewModel
internal class AppStartupViewModel @Inject constructor(
    private val session: GameSession,
    private val drafts: OnboardingDraftRepository,
) : ViewModel() {
    private val state = MutableStateFlow<AppStartupState>(AppStartupState.Loading)
    val uiState = state.asStateFlow()
    private var work: Job? = null
    private val writes = Mutex()
    private var selectedAccessory = "BACKPACK"
    private var selectedGoalId: String? = null
    val goalIds: List<String> = session.onboardingGoals.map { it.goalId }

    init { retry() }

    fun retry() {
        if (work?.isActive == true || state.value == AppStartupState.Ready) return
        state.value = AppStartupState.Loading
        work = viewModelScope.launch {
            try {
                state.value = if (session.read() != null) AppStartupState.Ready
                else drafts.read()?.let {
                    selectedAccessory = it.accessoryId
                    selectedGoalId = it.goalId
                    when (it.step) {
                        OnboardingStep.Profile -> AppStartupState.Customize(it.profile)
                        OnboardingStep.Accessories -> AppStartupState.Accessories(it)
                        OnboardingStep.GoalSelection -> AppStartupState.GoalSelection(it)
                        OnboardingStep.Introduction -> if (it.goalId in goalIds) AppStartupState.Introduction(it)
                            else AppStartupState.GoalSelection(it.copy(step = OnboardingStep.GoalSelection))
                    }
                } ?: AppStartupState.Choose()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                state.value = AppStartupState.Error
            }
        }
    }

    fun startAdventure() {
        val choice = state.value as? AppStartupState.Choose ?: return
        if (choice.saving || work?.isActive == true) return
        state.value = choice.copy(saving = true, failed = false)
        work = viewModelScope.launch {
            try {
                selectedAccessory = "BACKPACK"
                selectedGoalId = null
                val draft = PetCustomization(name = "")
                writes.withLock { drafts.save(OnboardingDraft(draft, accessoryId = selectedAccessory, goalId = selectedGoalId)) }
                state.value = AppStartupState.Customize(draft)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                state.value = AppStartupState.Choose(failed = true)
            }
        }
    }

    fun editName(name: String) = editCurrent { it.copy(name = name) }
    fun editFur(fur: PetFur) = editCurrent { it.copy(fur = fur) }
    fun editTemperament(temperament: PetTemperament) = editCurrent { it.copy(temperament = temperament) }

    private fun editCurrent(transform: (PetCustomization) -> PetCustomization) {
        val current = state.value as? AppStartupState.Customize ?: return
        editCustomization(transform(current.draft))
    }

    fun editCustomization(draft: PetCustomization) {
        val current = state.value as? AppStartupState.Customize ?: return
        if (current.saving) return
        state.value = current.copy(draft = draft, failed = false)
        viewModelScope.launch {
            try {
                writes.withLock { drafts.save(OnboardingDraft(draft, accessoryId = selectedAccessory, goalId = selectedGoalId)) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                val latest = state.value as? AppStartupState.Customize
                if (latest != null) state.value = latest.copy(failed = true)
            }
        }
    }

    fun backToCharacters() {
        val current = state.value as? AppStartupState.Customize ?: return
        if (current.saving) return
        state.value = current.copy(saving = true, failed = false)
        work = viewModelScope.launch {
            try {
                writes.withLock { drafts.clear() }
                state.value = AppStartupState.Choose()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                state.value = current.copy(failed = true)
            }
        }
    }

    fun finishCustomization() {
        val current = state.value as? AppStartupState.Customize ?: return
        if (current.saving || current.draft.name.isBlank() || work?.isActive == true) return
        state.value = current.copy(saving = true, failed = false)
        work = viewModelScope.launch {
            try {
                val draft = OnboardingDraft(current.draft, OnboardingStep.Accessories, selectedAccessory, selectedGoalId)
                writes.withLock { drafts.save(draft) }
                state.value = AppStartupState.Accessories(draft)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { state.value = current.copy(failed = true) }
        }
    }

    fun selectAccessory(id: String) {
        val current = state.value as? AppStartupState.Accessories ?: return
        if (current.saving || current.draft.accessoryId == id) return
        selectedAccessory = id
        val draft = current.draft.copy(accessoryId = id)
        state.value = current.copy(draft = draft, failed = false)
        viewModelScope.launch {
            try { writes.withLock { drafts.save(draft) } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                val latest = state.value as? AppStartupState.Accessories
                if (latest != null) state.value = latest.copy(failed = true)
            }
        }
    }

    fun backToCustomization() {
        val current = state.value as? AppStartupState.Accessories ?: return
        if (current.saving || work?.isActive == true) return
        state.value = current.copy(saving = true, failed = false)
        work = viewModelScope.launch {
            try {
                writes.withLock { drafts.save(current.draft.copy(step = OnboardingStep.Profile)) }
                state.value = AppStartupState.Customize(current.draft.profile)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { state.value = current.copy(failed = true) }
        }
    }

    fun confirmAccessory() {
        val current = state.value as? AppStartupState.Accessories ?: return
        if (current.saving || work?.isActive == true || !current.draft.hasValidChoices) return
        state.value = current.copy(saving = true, failed = false)
        work = viewModelScope.launch {
            try {
                val draft = current.draft.copy(step = OnboardingStep.GoalSelection)
                writes.withLock { drafts.save(draft) }
                state.value = AppStartupState.GoalSelection(draft)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { state.value = current.copy(failed = true) }
        }
    }

    fun backToAccessories() {
        val current = state.value as? AppStartupState.GoalSelection ?: return
        if (current.saving || work?.isActive == true) return
        state.value = current.copy(saving = true, failed = false)
        work = viewModelScope.launch {
            try {
                val draft = current.draft.copy(step = OnboardingStep.Accessories)
                writes.withLock { drafts.save(draft) }
                state.value = AppStartupState.Accessories(draft)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { state.value = current.copy(failed = true) }
        }
    }

    fun selectGoal(id: String) {
        val current = state.value as? AppStartupState.GoalSelection ?: return
        if (current.saving || work?.isActive == true || id !in goalIds) return
        state.value = current.copy(saving = true, failed = false)
        work = viewModelScope.launch {
            try {
                val draft = current.draft.copy(goalId = id)
                writes.withLock { drafts.save(draft) }
                selectedGoalId = id
                state.value = AppStartupState.GoalSelection(draft)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { state.value = current.copy(failed = true) }
        }
    }

    fun confirmGoal() {
        val current = state.value as? AppStartupState.GoalSelection ?: return
        if (current.saving || work?.isActive == true || current.draft.goalId !in goalIds) return
        state.value = current.copy(saving = true, failed = false)
        work = viewModelScope.launch {
            try {
                val draft = current.draft.copy(step = OnboardingStep.Introduction)
                writes.withLock { drafts.save(draft) }
                state.value = AppStartupState.Introduction(draft)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { state.value = current.copy(failed = true) }
        }
    }

    fun backToGoals() {
        val current = state.value as? AppStartupState.Introduction ?: return
        if (current.saving || work?.isActive == true) return
        state.value = current.copy(saving = true, failed = false)
        work = viewModelScope.launch {
            try {
                val draft = current.draft.copy(step = OnboardingStep.GoalSelection)
                writes.withLock { drafts.save(draft) }
                state.value = AppStartupState.GoalSelection(draft)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { state.value = current.copy(failed = true) }
        }
    }

    fun finishOnboarding() {
        val current = state.value as? AppStartupState.Introduction ?: return
        if (current.saving || work?.isActive == true || !current.draft.canFinish || current.draft.goalId !in goalIds) return
        state.value = current.copy(saving = true, failed = false)
        work = viewModelScope.launch {
            try {
                writes.withLock {
                    // Profile and accessory commit with the game; startup never replaces an existing save.
                    session.prepare(current.draft.profile.toPetState(current.draft.accessoryId), current.draft.goalId)
                }
                state.value = AppStartupState.Ready
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { state.value = current.copy(failed = true) }
        }
    }
}
