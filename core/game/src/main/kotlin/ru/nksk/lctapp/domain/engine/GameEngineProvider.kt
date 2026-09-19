package ru.nksk.lctapp.domain.engine

import ru.nksk.lctapp.domain.content.StoryContentRepository
import ru.nksk.lctapp.domain.game.GameRepository

/** Composition boundary for an authored content/rules release; no placeholder rules are installed. */
class GameEngineProvider(
    private val games: GameRepository,
    private val content: StoryContentRepository,
) {
    suspend fun create(rules: EngineRules, policies: Map<String, EventPolicy>, meals: List<MealDefinition>): GameEngine =
        GameEngine(games, EventFactory(content.read(), policies, meals), rules)
}
