package ru.nksk.lctapp.app.updates

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import ru.rustore.sdk.appupdate.listener.InstallStateUpdateListener
import ru.rustore.sdk.appupdate.manager.RuStoreAppUpdateManager
import ru.rustore.sdk.appupdate.model.AppUpdateInfo
import ru.rustore.sdk.appupdate.model.AppUpdateOptions
import ru.rustore.sdk.appupdate.model.AppUpdateType
import ru.rustore.sdk.appupdate.model.InstallStatus
import ru.rustore.sdk.appupdate.model.UpdateAvailability
import ru.rustore.sdk.core.tasks.Task
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class RuStoreUpdateClient @Inject constructor(
    private val manager: RuStoreAppUpdateManager,
) : AppUpdateClient {
    override val events = callbackFlow {
        val listener = InstallStateUpdateListener { state ->
            when (state.installStatus) {
                InstallStatus.DOWNLOADED -> trySend(UpdateStatus.DOWNLOADED)
                InstallStatus.DOWNLOADING, InstallStatus.PENDING -> trySend(UpdateStatus.IN_PROGRESS)
                InstallStatus.FAILED -> {
                    Log.w(TAG, "Download failed: ${state.installErrorCode}")
                    trySend(UpdateStatus.NONE)
                }
                InstallStatus.DOWNLOAD_INTERRUPTED -> trySend(UpdateStatus.NONE)
            }
        }
        manager.registerListener(listener)
        awaitClose { manager.unregisterListener(listener) }
    }

    override suspend fun check(): UpdateStatus = logged("Check") {
        manager.getAppUpdateInfo().awaitResult().status()
    }

    override suspend fun download(onPromptStarted: () -> Unit): Boolean = logged("Download") {
        // AppUpdateInfo is single-use; never keep it across attempts or activity recreation.
        val info = manager.getAppUpdateInfo().awaitResult()
        when (info.status()) {
            UpdateStatus.DOWNLOADED -> true
            UpdateStatus.AVAILABLE -> {
                val task = manager.startUpdateFlow(info, flexibleOptions())
                onPromptStarted()
                task.awaitResult()
                false // Acceptance/cancellation never completes installation automatically.
            }
            else -> false
        }
    }

    override suspend fun install(): Unit = logged("Install") {
        manager.completeUpdate(flexibleOptions()).awaitResult()
    }

    private fun AppUpdateInfo.status(): UpdateStatus = when {
        installStatus == InstallStatus.DOWNLOADED -> UpdateStatus.DOWNLOADED
        installStatus == InstallStatus.DOWNLOADING || installStatus == InstallStatus.PENDING ||
            updateAvailability == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS -> UpdateStatus.IN_PROGRESS
        updateAvailability == UpdateAvailability.UPDATE_AVAILABLE -> UpdateStatus.AVAILABLE
        else -> UpdateStatus.NONE
    }

    private fun flexibleOptions() = AppUpdateOptions.Builder().appUpdateType(AppUpdateType.FLEXIBLE).build()

    private suspend fun <T> logged(operation: String, block: suspend () -> T): T = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        // A missing/outdated store or offline device must not prevent playing.
        Log.w(TAG, "$operation unavailable", failure)
        throw failure
    }

    private companion object { const val TAG = "RuStoreUpdates" }
}

private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { value -> if (continuation.isActive) continuation.resume(value) }
    addOnFailureListener { error -> if (continuation.isActive) continuation.resumeWithException(error) }
}
