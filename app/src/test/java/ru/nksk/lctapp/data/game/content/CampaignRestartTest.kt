package ru.nksk.lctapp.data.game.content

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.app.createInitialGameState
import ru.nksk.lctapp.domain.content.StoryContent
import ru.nksk.lctapp.domain.content.StoryContentRepository
import ru.nksk.lctapp.domain.engine.GameSession
import ru.nksk.lctapp.domain.game.GameRepository
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.game.OwnedItem
import ru.nksk.lctapp.domain.history.CampaignRestartRequest
import ru.nksk.lctapp.domain.pet.PetAge
import ru.nksk.lctapp.domain.pet.PetColor
import ru.nksk.lctapp.domain.story.StoryDecision

class CampaignRestartTest {
    private val catalog = bundledGameCatalog()
    private val initial = createInitialGameState()
    private val request = CampaignRestartRequest("rewind", "old", null, 1)

    @Test fun onlyTheCompletedCampaignCanStartAgain() = runBlocking {
        val repository = Repository(initial, initial)
        val session = session(repository)
        assertFalse(session.canRestartCampaign(initial))
        try { session.restartCampaign(request); fail("An unfinished story cannot rewind") }
        catch (_: IllegalStateException) { }
        assertEquals(initial, repository.read())
    }

    @Test fun completedStoryRestartsTheSameHeroWithTheOriginalEconomyAndNoEarnedProgress() = runBlocking {
        val firstGoal = catalog.goals.first { it.goalId == catalog.storyCampaign!!.acts.first().goalId }
        val original = initial.copy(selectedGoalId = firstGoal.goalId, selectedSavingItemId = firstGoal.itemIds.first())
        val finals = catalog.storyCampaign!!.acts.mapIndexed { index, act ->
            StoryDecision("final-$index", catalog.content.choices.first { it.eventId == act.finaleId }.id)
        }
        val completed = original.copy(pet = original.pet.copy(name = "Наш лис", age = PetAge.SENIOR, color = PetColor.COPPER),
            story = original.story.copy(decisions = finals), ownedItems = listOf(OwnedItem("purchased", "old-item")),
            completedMiniGames = setOf("played"))
        val repository = Repository(completed, original)
        val session = session(repository)
        assertTrue(session.canRestartCampaign(completed))
        val fresh = session.restartCampaign(request)
        assertEquals("Наш лис", fresh.pet.name)
        assertEquals(PetAge.CUB, fresh.pet.age)
        assertEquals(completed.pet.color, fresh.pet.color)
        assertEquals(initial.economy, fresh.economy)
        assertEquals(firstGoal.goalId, fresh.selectedGoalId)
        assertEquals(original.selectedSavingItemId, fresh.selectedSavingItemId)
        assertTrue(fresh.story.decisions.isEmpty())
        assertTrue(fresh.ownedItems.isEmpty())
        assertTrue(fresh.completedMiniGames.isEmpty())
        assertFalse(session.canRestartCampaign(fresh))
    }

    private fun session(repository: GameRepository) = GameSession(repository, object : StoryContentRepository {
        override suspend fun read() = catalog.content
        override suspend fun install(content: StoryContent) { }
    }, catalog, initial)

    private class Repository(state: GameState, private val original: GameState) : GameRepository {
        private val flow = MutableStateFlow(state)
        override fun observe() = flow
        override suspend fun read() = flow.value
        override suspend fun initializeIfAbsent(initial: GameState) = flow.value
        override suspend fun update(transform: (GameState) -> GameState) = transform(flow.value).also { flow.value = it }
        override suspend fun restartCampaign(request: CampaignRestartRequest, transform: (GameState, GameState?) -> GameState) =
            transform(flow.value, original).also { flow.value = it }
    }
}
