package ru.nksk.lctapp.app

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import coil.ImageLoader
import coil.ImageLoaderFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.nksk.lctapp.BuildConfig
import ru.nksk.lctapp.data.backend.ParentIdentityStore
import ru.nksk.lctapp.data.telemetry.Telemetry
import javax.inject.Inject

@HiltAndroidApp
class LctApplication : Application(), ImageLoaderFactory, Configuration.Provider {
    @Inject lateinit var artworkImageLoader: ImageLoader
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject internal lateinit var identities: ParentIdentityStore

    override fun onCreate() {
        super.onCreate()
        Telemetry.init(this, BuildConfig.OTEL_EXPORTER_ENDPOINT)
        // Link telemetry to the persisted device identity as soon as DataStore provides it.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { Telemetry.attachDeviceId(identities.getOrCreate().deviceId) }
                .onFailure { Log.w("Telemetry", "device.id attach failed", it) }
                .onSuccess { Log.i("Telemetry", "device.id attached") }
        }
    }

    override fun newImageLoader(): ImageLoader = artworkImageLoader

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
}
