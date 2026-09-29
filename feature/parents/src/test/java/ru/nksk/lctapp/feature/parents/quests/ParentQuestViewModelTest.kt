package ru.nksk.lctapp.feature.parents.quests

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import ru.nksk.lctapp.domain.backend.*
import ru.nksk.lctapp.domain.pet.ParentRewardCaps

@OptIn(ExperimentalCoroutinesApi::class)
class ParentQuestViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun teardown() = Dispatchers.resetMain()

    @Test fun ownedCapCannotBeIssuedAndInventoryUpdateChangesAvailability() = runTest(dispatcher) {
        val repo = FakeRewards()
        val model = ParentQuestViewModel(repo)
        model.load(ParentQuest.SHOPPING); runCurrent()
        repeat(4) { model.setStepChecked(it, true) }
        repo.inventory.value = ParentRewardInventory("run", setOf(cap))
        runCurrent()
        assertTrue(model.uiState.value.selectedOwned)
        assertFalse(model.uiState.value.canComplete)
        model.complete(); runCurrent()
        assertEquals(0, repo.calls)
    }

    @Test fun uncertainIssueFreezesSelectionAndReopenUsesSameRewardWithoutAutoIssuing() = runTest(dispatcher) {
        val repo = FakeRewards().apply { fail = true }
        val model = ParentQuestViewModel(repo)
        model.load(ParentQuest.WEEKEND); runCurrent()
        repeat(4) { model.setStepChecked(it, true) }
        model.complete(); model.complete(); runCurrent()
        assertEquals(1, repo.calls)
        assertFalse(model.uiState.value.completed)
        model.selectItem(ParentRewardCaps.all[1].itemId)
        assertEquals(cap, model.uiState.value.selectedItemId)
        val reopened = ParentQuestViewModel(repo)
        reopened.load(ParentQuest.WEEKEND); runCurrent()
        assertEquals(1, repo.calls)
        assertEquals(4, reopened.uiState.value.completedSteps)
        assertTrue(reopened.uiState.value.canComplete)
        repo.fail = false
        reopened.complete(); runCurrent()
        assertTrue(reopened.uiState.value.completed)
        assertEquals(listOf(cap, cap), repo.items)
    }

    @Test fun newRunWhileQuestIsOpenCannotReceiveItsOldCompletion() = runTest(dispatcher) {
        val repo = FakeRewards()
        val model = ParentQuestViewModel(repo)
        model.load(ParentQuest.SECOND_LIFE); runCurrent()
        repeat(4) { model.setStepChecked(it, true) }
        repo.inventory.value = ParentRewardInventory("new-run", emptySet()); runCurrent()
        assertTrue(model.uiState.value.targetChanged)
        model.complete(); runCurrent()
        assertEquals(0, repo.calls)
    }

    private class FakeRewards : ParentQuestRewardsRepository {
        val inventory = MutableStateFlow<ParentRewardInventory?>(ParentRewardInventory("run", emptySet()))
        var frozen: PendingParentQuestReward? = null
        var calls = 0
        var fail = false
        val items = mutableListOf<String>()
        override fun inventory() = inventory
        override suspend fun pending(questId: String) = frozen
        override suspend fun issue(questId: String, itemId: String, expectedRunId: String) {
            calls++; items += itemId
            frozen = PendingParentQuestReward(expectedRunId, itemId)
            if (fail) throw IOException("Lost response")
            inventory.value = ParentRewardInventory(expectedRunId, setOf(itemId))
            frozen = null
        }
    }
    companion object { private val cap = ParentRewardCaps.all.first().itemId }
}
