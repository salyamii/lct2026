package ru.nksk.lctapp.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import coil.ImageLoader
import coil.ImageLoaderFactory
import javax.inject.Inject

@HiltAndroidApp
class LctApplication : Application(), ImageLoaderFactory {
    @Inject lateinit var artworkImageLoader: ImageLoader

    override fun newImageLoader(): ImageLoader = artworkImageLoader
}
