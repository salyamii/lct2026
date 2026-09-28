package ru.nksk.lctapp.app.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.nksk.lctapp.BuildConfig
import ru.nksk.lctapp.data.backend.BackendConnection
import ru.nksk.lctapp.data.backend.DeviceParentIdentityStore
import ru.nksk.lctapp.data.backend.ParentIdentityStore
import ru.nksk.lctapp.data.backend.RemoteParentLinkRepository
import ru.nksk.lctapp.data.backend.BackendSyncStore
import ru.nksk.lctapp.data.backend.RoomBackendSyncStore
import ru.nksk.lctapp.data.backend.RemoteCloudSyncRepository
import ru.nksk.lctapp.data.backend.BackendRateLimit
import ru.nksk.lctapp.data.backend.RateLimitedBackendApi
import ru.nksk.lctapp.data.backend.createBackendApi
import ru.nksk.lctapp.domain.backend.CloudSyncRepository
import ru.nksk.lctapp.domain.parentlink.ParentLinkRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ParentLinkModule {
    @Binds @Singleton abstract fun identities(value: DeviceParentIdentityStore): ParentIdentityStore
    @Binds @Singleton abstract fun parentLink(value: RemoteParentLinkRepository): ParentLinkRepository
    @Binds @Singleton abstract fun cloud(value: RemoteCloudSyncRepository): CloudSyncRepository
    @Binds @Singleton abstract fun syncStore(value: RoomBackendSyncStore): BackendSyncStore

    companion object {
        @Provides @Singleton fun backend(rateLimit: BackendRateLimit): BackendConnection =
            BackendConnection(BuildConfig.BACKEND_BASE_URL) { url ->
                RateLimitedBackendApi(createBackendApi(url), rateLimit, url)
            }
    }
}
