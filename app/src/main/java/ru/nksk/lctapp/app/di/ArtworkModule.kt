package ru.nksk.lctapp.app.di

import android.content.Context
import coil.ImageLoader
import coil.memory.MemoryCache
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object ArtworkModule {
    @Provides
    @Singleton
    fun imageLoader(@ApplicationContext context: Context): ImageLoader = ImageLoader.Builder(context)
        .memoryCache {
            MemoryCache.Builder(context)
                .maxSizeBytes(minOf(Runtime.getRuntime().maxMemory() / 8, 32L * 1024 * 1024).toInt())
                .build()
        }
        // Artwork is bundled in the APK; no disk copy or network observer is needed.
        .diskCache(null)
        .networkObserverEnabled(false)
        .bitmapFactoryMaxParallelism(2)
        .crossfade(false)
        .build()
}
