package ru.nksk.lctapp.data.backend

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Schedules durable attempts; only the repository may decide what data is sent or applied. */
@Singleton
internal class BackendSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backend: BackendConnection,
) {
    private val workManager by lazy { WorkManager.getInstance(context) }
    private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun ensurePeriodicSync() {
        if (!backend.configured) return
        val request = PeriodicWorkRequestBuilder<BackendSyncWorker>(15, TimeUnit.MINUTES)
            .setInitialDelay(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun requestSync() {
        if (!backend.configured) return
        val request = OneTimeWorkRequestBuilder<BackendSyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        // KEEP coalesces offline and foreground requests without cancelling an in-flight upload.
        workManager.enqueueUniqueWork(REQUESTED_WORK, ExistingWorkPolicy.KEEP, request)
    }

    /** Subscribe only while the UI is resumed; WorkManager owns background network constraints. */
    fun networkReturns(): Flow<Unit> = callbackFlow {
        if (!backend.configured) {
            close()
            return@callbackFlow
        }
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val available = mutableSetOf<Network>()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                val connected = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                if (connected && available.add(network)) trySend(Unit)
                else if (!connected) available.remove(network)
            }

            override fun onLost(network: Network) { available.remove(network) }
        }
        connectivity.registerDefaultNetworkCallback(callback)
        awaitClose { connectivity.unregisterNetworkCallback(callback) }
    }.conflate()

    private companion object {
        const val PERIODIC_WORK = "backend-periodic-sync-v1"
        const val REQUESTED_WORK = "backend-requested-sync-v1"
    }
}
