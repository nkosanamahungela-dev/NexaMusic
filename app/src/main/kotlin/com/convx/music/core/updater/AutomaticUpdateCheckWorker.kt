package com.convx.music.core.updater

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.convx.music.core.UpdateNotificationHelper
import com.convx.music.core.updater.downloadmanager.UpdateDownloadWorker
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Periodic GitHub Releases check. Stable releases are always eligible; GitHub
 * pre-releases and nightly builds are considered by checkForUpdate only when
 * the user's Beta Updates preference allows them.
 *
 * APK installation is deliberately left to Android's user-confirmed installer.
 */
class AutomaticUpdateCheckWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private data class DetectedUpdate(
        val tag: String,
        val available: Boolean,
        val size: String,
        val apkUrl: String?
    )

    override suspend fun doWork(): Result {
        val context = applicationContext
        if (!getAutoUpdateCheckSetting(context)) return Result.success()

        val update = withTimeoutOrNull(90_000L) {
            suspendCancellableCoroutine<DetectedUpdate?> { continuation ->
                checkForUpdate(
                    context = context,
                    onSuccess = { tag, available, _, size, _, _, _, apkUrl ->
                        if (continuation.isActive) {
                            continuation.resume(DetectedUpdate(tag, available, size, apkUrl))
                        }
                    },
                    onError = {
                        if (continuation.isActive) continuation.resume(null)
                    }
                )
            }
        } ?: return Result.retry()

        if (!getAutoUpdateCheckSetting(context)) return Result.success()

        saveLastCheckedTime(
            context,
            java.time.LocalDateTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy, h:mm a")
            )
        )

        if (!update.available) {
            saveUpdateAvailableState(context, false)
            UpdateNotificationHelper.cancelUpdateNotification(context)
            return Result.success()
        }

        saveUpdateAvailableState(context, true)

        if (getUpdateNotificationsSetting(context) &&
            getLastNotifiedUpdateTag() != update.tag
        ) {
            UpdateNotificationHelper.showUpdateNotification(context, update.tag)
            saveLastNotifiedUpdateTag(update.tag)
        }

        // Auto-check also downloads eligible updates in the background. Android
        // still displays its package installer and requires the user to approve.
        val apkUrl = update.apkUrl?.takeIf(String::isNotBlank) ?: return Result.success()
        val downloadRequest = OneTimeWorkRequestBuilder<UpdateDownloadWorker>()
            .setInputData(
                workDataOf(
                    "apk_url" to apkUrl,
                    "version" to update.tag,
                    "file_size" to update.size
                )
            )
            .addTag("update_download")
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "update_download",
            ExistingWorkPolicy.KEEP,
            downloadRequest
        )

        return Result.success()
    }
}
