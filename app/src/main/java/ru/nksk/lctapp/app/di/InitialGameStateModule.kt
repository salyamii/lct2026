package ru.nksk.lctapp.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.nksk.lctapp.app.createInitialGameState
import ru.nksk.lctapp.domain.game.GameState

/** New-save fixture, used by the repository only when no saved game exists (D-043). */
@Module
@InstallIn(SingletonComponent::class)
object InitialGameStateModule {
    @Provides
    fun provideInitialGameState(): GameState = createInitialGameState()
}
