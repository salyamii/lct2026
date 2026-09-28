package ru.nksk.lctapp.app.di

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import ru.nksk.lctapp.data.game.RoomGameRepository
import ru.nksk.lctapp.data.game.RoomOnboardingDraftRepository
import ru.nksk.lctapp.data.game.RoomStoryContentRepository
import ru.nksk.lctapp.data.game.local.GameDatabase
import ru.nksk.lctapp.domain.content.StoryContentRepository
import ru.nksk.lctapp.domain.game.GameRepository
import ru.nksk.lctapp.domain.onboarding.OnboardingDraftRepository
import ru.nksk.lctapp.domain.backend.ParentRewardPolicy

@Module
@InstallIn(SingletonComponent::class)
internal object GameDatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): GameDatabase = GameDatabase.open(context)

    /** Coin allocation and duplicate handling remain deferred until their product rules are confirmed. */
    @Provides
    @Singleton
    fun parentRewardPolicy(): ParentRewardPolicy = ParentRewardPolicy()
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class GameRepositoryModule {
    @Binds
    @Singleton
    abstract fun games(implementation: RoomGameRepository): GameRepository

    @Binds
    @Singleton
    abstract fun drafts(implementation: RoomOnboardingDraftRepository):
        OnboardingDraftRepository

    @Binds
    @Singleton
    abstract fun content(implementation: RoomStoryContentRepository): StoryContentRepository
}
