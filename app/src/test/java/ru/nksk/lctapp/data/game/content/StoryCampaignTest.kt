package ru.nksk.lctapp.data.game.content

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.app.createInitialGameState
import ru.nksk.lctapp.domain.content.*
import ru.nksk.lctapp.domain.engine.*
import ru.nksk.lctapp.domain.game.*
import ru.nksk.lctapp.domain.minigame.*
import ru.nksk.lctapp.domain.story.StoryDecision
import ru.nksk.lctapp.domain.pet.PetAge
import ru.nksk.lctapp.domain.pet.PetColor

class StoryCampaignTest {
    @Test fun bundledRecapsCoverEveryChoiceAndDoNotReuseSuccessTextForSkips() {
        val catalog = bundledGameCatalog()
        for (choice in catalog.content.choices) {
            val recap = catalog.cards[choice.eventId]?.summaryByChoiceId?.get(choice.id)
            assertFalse("Missing outcome for ${choice.id}", recap.isNullOrBlank())
            if (choice.id.endsWith(":skip") && !choice.id.contains("G5.02"))
                assertTrue(recap!!.startsWith("Пропустили"))
        }
    }

    @Test fun olderSaveAdoptsStoryAgeWithoutChangingAnythingElseAndPreparationIsIdempotent() = runTest {
        val f = Fixture()
        f.finishCampaign(listOf(TOWER_GOAL, HOME_GOAL, MAP_GOAL, STARS_GOAL, EXPEDITION_GOAL), skipOptional = true)
        val old = f.state.copy(pet = f.state.pet.copy(age = PetAge.CUB))
        val repo = MemoryRepository(old)
        val session = GameSession(repo, f.content, f.catalog, f.initial)
        session.prepare()
        val oldDay = checkNotNull(old.engine)
        val expected = old.copy(pet = old.pet.copy(age = PetAge.SENIOR),
            engine = oldDay.copy(revision = oldDay.revision + 1))
        assertEquals(expected, repo.value)
        session.prepare()
        GameSession(repo, f.content, f.catalog, f.initial).prepare()
        assertEquals(expected, repo.value)
    }

    @Test fun allEighteenAllowedGoalOrdersReachTheSameCoreRevealsWithoutOptionalScenes() = runTest {
        val orders = permutations(listOf(STARS_GOAL, TOWER_GOAL, HOME_GOAL, MAP_GOAL)).filter { it.first() != MAP_GOAL }
        assertEquals(18, orders.size)
        for (order in orders) {
            val f = Fixture()
            f.finishCampaign(order + EXPEDITION_GOAL, skipOptional = true)
            val expected = sourceLoreCards.filterNot { it.optional }.map { storyEventId(it.id) }
            val actual = f.state.story.decisions.map { decision -> f.catalog.content.choices.single { it.id == decision.choiceId }.eventId }
                .filter { it in expected }
            assertEquals(order.toString(), expected, actual)
            assertEquals(order + EXPEDITION_GOAL, f.state.completedGoalProjects.map { it.goalId })
            assertNull(f.state.selectedGoalId)
            assertNull(f.catalog.goals.selectedGoal(f.state))
            assertTrue(f.catalog.storyProgress(f.state).campaignComplete)
            assertTrue("extra_archive_fragment" in f.catalog.storyProgress(f.state).facts)
            assertFalse("beacon_chain_restored" in f.catalog.storyProgress(f.state).facts)
            val finale = f.catalog.cards.getValue(storyEventId("G5.12"))
            assertTrue(finale.variants.any { f.catalog.storyProgress(f.state).meets(it.condition) })
            assertTrue(f.catalog.plan(f.state).none { f.catalog.content.events.single { event -> event.id == it }.type == EventType.STORY })
        }
    }

    @Test fun optionalRepairsProduceTheRestoredNetworkEndingAndDoNotNeedTheArchiveFallback() = runTest {
        val f = Fixture()
        f.finishCampaign(listOf(HOME_GOAL, MAP_GOAL, TOWER_GOAL, STARS_GOAL, EXPEDITION_GOAL), skipOptional = false)
        val progress = f.catalog.storyProgress(f.state)
        assertTrue("beacon_chain_restored" in progress.facts)
        assertFalse("extra_archive_fragment" in progress.facts)
        assertTrue(f.catalog.cards.getValue(storyEventId("G5.12")).variants.none { progress.meets(it.condition) })
    }

    @Test fun offerAndPauseDoNotGrantKnowledgeOnlyCompletedMiniGameDoes() = runTest {
        val f = Fixture()
        val id = "figma-2163-43-v1"
        f.send(EngineCommand.BeginDay(f.catalog.storyDayId, listOf(id) + f.catalog.deedPool.take(3), openFirst = true))
        assertFalse("telescope_tuned" in f.catalog.storyProgress(f.state).facts)
        f.send(EngineCommand.AcceptDeedProposal(f.state.engine!!.currentEvent!!.id))
        val occurrence = f.state.engine!!.currentEvent!!
        f.send(EngineCommand.PauseEvent(occurrence.id))
        assertFalse("telescope_tuned" in f.catalog.storyProgress(f.state).facts)
        f.send(EngineCommand.StartDeed(occurrence.deedOfferId!!))
        f.send(EngineCommand.CompleteDeed(occurrence.id, perfectScore(DeedGameKind.PRECISION)))
        assertTrue("telescope_tuned" in f.catalog.storyProgress(f.state).facts)
        val reread = GameSession(f.repo, f.content, f.catalog, createInitialGameState())
        reread.prepare()
        assertEquals(f.state, reread.read())
        assertEquals(f.catalog.storyProgress(f.state).facts, reread.catalog.storyProgress(reread.read()!!).facts)
    }

    @Test fun oldUnopenedGoalInvitationDoesNotBlockOrdinaryLifeWithoutASelectedGoal() = runTest {
        val f = Fixture()
        val oldInvitation = f.catalog.goals.first().introductionEventId
        f.send(EngineCommand.BeginDay(f.catalog.storyDayId, listOf(oldInvitation) + f.catalog.deedPool.take(3)))
        val ordinaryIds = f.state.engine!!.events.drop(1).map { it.id }
        f.send(EngineCommand.OpenNextEvent)
        assertNull(f.state.selectedGoalId)
        assertEquals(ordinaryIds, f.state.engine!!.events.map { it.id })
        assertEquals(EventType.EARNING, f.catalog.content.events.single { it.id == f.state.engine!!.currentEvent!!.eventId }.type)
    }

    @Test fun owningDifferentKitsDoesNotRevealAnythingOrUnlockTheLastProject() = runTest {
        val f = Fixture()
        val state = f.state.copy(ownedItems = f.catalog.goals.flatMap { it.itemIds }.mapIndexed { n, id -> OwnedItem("item-$n", id) })
        assertTrue(f.catalog.storyProgress(state).facts.isEmpty())
        assertEquals(listOf(STARS_GOAL, TOWER_GOAL, HOME_GOAL), f.catalog.goals.filter { it.isAvailable(state) }.map { it.goalId })
        assertFalse(f.catalog.storyProgress(state).eligible(storyEventId("G1.12")))
        assertFalse(f.catalog.goals.last().isAvailable(state))
        assertTrue(f.catalog.plan(state).none { f.catalog.policies.getValue(it).storyActId != null })
        val selected = state.copy(selectedGoalId = TOWER_GOAL)
        assertFalse("A kit alone cannot open the finale", f.catalog.storyProgress(selected).eligible(storyEventId("G1.12")))
        val revealed = selected.copy(story = selected.story.copy(decisions = listOf(
            StoryDecision("signal", "${storyEventId("G1.11")}:continue"))))
        assertTrue(f.catalog.storyProgress(revealed).eligible(storyEventId("G1.12")))
        assertFalse("Reveals alone cannot open the finale", f.catalog.storyProgress(revealed.copy(ownedItems = emptyList())).eligible(storyEventId("G1.12")))
    }

    @Test fun factoryRejectsMissingFactProducersAndConditionsUseCommittedDecisions() {
        val f = Fixture()
        val id = storyEventId("G1.02")
        assertThrows(IllegalArgumentException::class.java) {
            EventFactory(f.catalog.content, f.catalog.policies + (id to f.catalog.policies.getValue(id).copy(condition = fact("missing"))),
                f.catalog.meals, f.catalog.goals, f.catalog.storyCampaign)
        }
        val decision = StoryDecision("done", "${storyEventId("G1.01")}:continue")
        val saved = f.state.copy(story = f.state.story.copy(decisions = listOf(decision)), selectedGoalId = HOME_GOAL)
        val progress = f.catalog.storyProgress(saved)
        assertTrue(progress.meets(all(fact("observatory_known"), any(fact("helped_dock"), StoryCondition.SelectedGoal(HOME_GOAL)),
            StoryCondition.Not(fact("chronoscope_unlocked")))))
        assertFalse(progress.eligible(storyEventId("G2.01")))
        val hint = f.catalog.storyCampaign!!.deedHints.first { progress.meets(it.condition) }
        assertTrue(f.catalog.policies.getValue(hint.eventId).factsByChoiceId.values.any { "helped_dock" in it })
        val legacy = saved.copy(story = saved.story.copy(decisions = listOf(StoryDecision("old", f.catalog.goals.first().legacyAcceptanceChoiceIds.single()))), selectedGoalId = null)
        assertTrue(f.catalog.storyProgress(legacy).completed(storyEventId("G1.01")))
        assertEquals(STARS_GOAL, f.catalog.goals.selectedGoal(legacy)?.goalId)
    }

    /** Drives public commands, including real deed result validation, feeding, sleep and purchases. */
    private class Fixture {
        val catalog = bundledGameCatalog()
        val initial = createInitialGameState().let { it.copy(economy = it.economy.copy(balance = 10_000),
            pet = it.pet.copy(name = "Тоша", color = PetColor.SAND, selectedLookId = "backend:scarf")) }
        val repo = MemoryRepository(initial)
        val content = object : StoryContentRepository {
            override suspend fun read() = catalog.content
            override suspend fun install(content: StoryContent) = Unit
        }
        val session = GameSession(repo, content, catalog, initial)
        val state get() = repo.value
        var sequence = 0
        suspend fun send(command: EngineCommand) {
            val before = state
            val result = session.dispatch(EngineRequest("campaign-${++sequence}", state.engine?.revision, command))
            assertTrue("${state.engine?.day}: $command -> $result", result is EngineResult.Applied)
            val expected = listOf(PetAge.CUB, PetAge.TEEN, PetAge.TEEN, PetAge.ADULT, PetAge.SENIOR, PetAge.SENIOR)
            assertEquals("Age must follow story completion for every goal order", expected[state.completedGoalProjects.size], state.pet.age)
            assertEquals(before.pet.copy(age = state.pet.age, visualState = state.pet.visualState), state.pet)
            if (before.completedGoalProjects == state.completedGoalProjects) assertEquals(before.pet.age, state.pet.age)
        }
        suspend fun finishCampaign(order: List<String>, skipOptional: Boolean) {
            repeat(4_000) {
                if (catalog.storyProgress(state).campaignComplete) return
                val day = state.engine
                when {
                    day != null && day.phase != DayPhase.FINISHED && !day.ateToday -> send(EngineCommand.Feed("basic-v1"))
                    catalog.goals.selectedGoal(state) == null -> {
                        val next = order[state.completedGoalProjects.size]
                        assertTrue(catalog.goals.single { it.goalId == next }.isAvailable(state))
                        send(session.selectGoalCommand(state, next))
                    }
                    day?.phase == DayPhase.FINISHED -> {
                        send(checkNotNull(session.advanceCommand(state)))
                        assertNull("Waking must not open an event", state.engine!!.currentEvent)
                    }
                    else -> {
                        val goal = catalog.goals.selectedGoal(state)!!
                        val missing = goal.progress(state, catalog.content).items.firstOrNull { item -> state.ownedItems.none { it.itemId == item.id } }
                        if (missing != null) {
                            val before = state.story
                            send(EngineCommand.BuyGoalItem(goal.goalId, missing.id, acceptFoodRisk = true))
                            assertEquals(before, state.story)
                        } else {
                            val active = state.engine!!.currentEvent
                            if (active == null) send(checkNotNull(session.advanceCommand(state)))
                            else if (active.origin == EventOrigin.DEED) {
                                send(EngineCommand.CompleteDeed(active.id, perfectScore(catalog.policies.getValue(active.eventId).deedGameKind!!)))
                            } else if (catalog.content.events.single { it.id == active.eventId }.type == EventType.EARNING) {
                                val accept = EngineCommand.AcceptDeedProposal(active.id)
                                if (session.engine.blockReason(state, accept) == null) send(accept)
                                else send(EngineCommand.DismissDeedProposal(active.id))
                            } else {
                                val choices = catalog.content.choices.filter { it.eventId == active.eventId }
                                val selected = if (skipOptional) choices.firstOrNull { it.id.endsWith(":skip") } ?: choices.first() else choices.first()
                                val command = EngineCommand.CompleteEvent(active.id, selected.id)
                                when (session.engine.blockReason(state, command)) {
                                    null -> send(command)
                                    BlockReason.MustSleep -> send(EngineCommand.PauseEvent(active.id))
                                    else -> error("Blocked story: $command ${session.engine.blockReason(state, command)}")
                                }
                            }
                        }
                    }
                }
            }
            fail("Campaign stuck: ${catalog.storyProgress(state).currentAct} ${catalog.storyProgress(state).facts} ${state.engine}")
        }
    }

    private class MemoryRepository(initial: GameState) : GameRepository {
        private val flow = MutableStateFlow(initial)
        val value get() = flow.value
        override fun observe() = flow
        override suspend fun read() = value
        override suspend fun initializeIfAbsent(initial: GameState) = value
        override suspend fun update(transform: (GameState) -> GameState) = transform(value).also { flow.value = it }
    }

    private companion object {
        fun permutations(values: List<String>): List<List<String>> = if (values.isEmpty()) listOf(emptyList())
            else values.flatMap { first -> permutations(values - first).map { listOf(first) + it } }
        fun perfectScore(kind: DeedGameKind): DeedGameScore = checkNotNull(when (kind) {
            DeedGameKind.PRECISION -> DeedGameScore.fromPrecision(TargetStopState(10, round = 5, hits = 5, lastHit = true))
            DeedGameKind.COMPARISON -> DeedGameScore.fromComparison(PriceQuizState.create().copy(current = 5, correctAnswers = 5))
            DeedGameKind.MEMORY -> DeedGameScore.fromMemory(MemoryState((0 until MemoryState.PAIRS).flatMap { listOf(it, it) })
                .let { it.copy(matched = it.faces.indices.toSet(), moves = MemoryState.PAIRS) })
        })
    }
}
