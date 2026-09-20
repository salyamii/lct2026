package ru.nksk.lctapp.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import ru.nksk.lctapp.domain.game.GameRepository
import ru.nksk.lctapp.domain.location.*

@Module
@InstallIn(SingletonComponent::class)
internal object LocationModule {
    @Provides fun accessPolicy(): LocationAccessPolicy = AllLocationsAvailable

    @Provides @Singleton
    fun locationController(games: GameRepository, access: LocationAccessPolicy): GameLocationController =
        DefaultGameLocationController(games, access)
}
