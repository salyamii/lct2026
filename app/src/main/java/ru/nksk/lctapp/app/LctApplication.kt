package ru.nksk.lctapp.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import coil.ImageLoader
import coil.ImageLoaderFactory
import javax.inject.Inject

@HiltAndroidApp
class LctApplication : Application(), ImageLoaderFactory, Configuration.Provider {
    @Inject lateinit var artworkImageLoader: ImageLoader
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override fun newImageLoader(): ImageLoader = artworkImageLoader

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
}
