package ru.nksk.lctapp.app.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.nksk.lctapp.BuildConfig
import ru.nksk.lctapp.data.backend.BackendConnection
import ru.nksk.lctapp.data.backend.EncryptedParentIdentityStore
import ru.nksk.lctapp.data.backend.ParentIdentityStore
import ru.nksk.lctapp.data.backend.RemoteParentLinkRepository
import ru.nksk.lctapp.domain.parentlink.ParentLinkRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ParentLinkModule {
    @Binds @Singleton abstract fun identities(value: EncryptedParentIdentityStore): ParentIdentityStore
    @Binds @Singleton abstract fun parentLink(value: RemoteParentLinkRepository): ParentLinkRepository

    companion object {
        @Provides @Singleton fun backend(): BackendConnection = BackendConnection(BuildConfig.BACKEND_BASE_URL)
    }
}
