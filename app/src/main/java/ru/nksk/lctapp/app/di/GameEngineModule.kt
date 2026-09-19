package ru.nksk.lctapp.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.nksk.lctapp.domain.content.StoryContentRepository
import ru.nksk.lctapp.domain.engine.GameEngineProvider
import ru.nksk.lctapp.domain.game.GameRepository

@Module
@InstallIn(SingletonComponent::class)
internal object GameEngineModule {
    @Provides
    fun engineProvider(games: GameRepository, content: StoryContentRepository): GameEngineProvider =
        GameEngineProvider(games, content)
}
