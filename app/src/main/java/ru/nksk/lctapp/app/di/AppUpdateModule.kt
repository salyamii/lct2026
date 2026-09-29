package ru.nksk.lctapp.app.di

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import ru.nksk.lctapp.app.updates.AppUpdateClient
import ru.nksk.lctapp.app.updates.RuStoreUpdateClient
import ru.rustore.sdk.appupdate.manager.RuStoreAppUpdateManager
import ru.rustore.sdk.appupdate.manager.factory.RuStoreAppUpdateManagerFactory

@Module
@InstallIn(SingletonComponent::class)
internal abstract class AppUpdateModule {
    @Binds abstract fun updateClient(client: RuStoreUpdateClient): AppUpdateClient

    companion object {
        @Provides @Singleton
        fun updateManager(@ApplicationContext context: Context): RuStoreAppUpdateManager =
            RuStoreAppUpdateManagerFactory.create(context)
    }
}
