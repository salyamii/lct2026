package ru.nksk.lctapp.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.nksk.lctapp.domain.content.StoryContentRepository
import ru.nksk.lctapp.domain.engine.GameEngineProvider
import ru.nksk.lctapp.domain.game.GameRepository
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.engine.GameSession
import ru.nksk.lctapp.domain.demo.DemoPreferencesRepository
import ru.nksk.lctapp.data.game.content.bundledGameCatalog
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import ru.nksk.lctapp.feature.menu.ui.MenuComputationDispatcher
import ru.nksk.lctapp.feature.day.ui.DayComputationDispatcher

@Module
@InstallIn(SingletonComponent::class)
internal object GameEngineModule {
    @Provides @MenuComputationDispatcher
    fun menuComputationDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides @DayComputationDispatcher
    fun dayComputationDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @Singleton
    fun session(games: GameRepository, content: StoryContentRepository, initial: GameState,
        demoPreferences: DemoPreferencesRepository): GameSession =
        GameSession(games, content, bundledGameCatalog(), initial, demoPreferences)

    @Provides
    fun engineProvider(games: GameRepository, content: StoryContentRepository): GameEngineProvider =
        GameEngineProvider(games, content)
}
