package com.tcnunes.szokert.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.tcnunes.szokert.R
import com.tcnunes.szokert.SzokertApp
import kotlinx.coroutines.CancellationException

private const val PHASE = "phase"
private const val DONE = "done"
private const val TOTAL = "total"

fun Progress.toData(): Data = when (this) {
    is Progress.Downloading -> workDataOf(PHASE to "download", DONE to done, TOTAL to total)
    Progress.Verifying -> workDataOf(PHASE to "verify")
    is Progress.Unpacking -> workDataOf(PHASE to "unpack", DONE to done, TOTAL to total)
}

fun Data.toProgress(): Progress? = when (getString(PHASE)) {
    "download" -> Progress.Downloading(getLong(DONE, 0), getLong(TOTAL, 0))
    "verify" -> Progress.Verifying
    "unpack" -> Progress.Unpacking(getLong(DONE, 0), getLong(TOTAL, 0))
    else -> null
}

/** The download the person started: runs as a foreground job with a progress notification. */
class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val data get() = (applicationContext as SzokertApp).data

    override suspend fun doWork(): Result {
        // Allowed while the app is open; if it isn't, carry on without the notification.
        runCatching { setForeground(foregroundInfo(null)) }
        var lastPercent = -1
        return try {
            data.updater.update { p ->
                setProgress(p.toData())
                val percent = percentOf(p)
                if (percent != lastPercent) {
                    lastPercent = percent
                    runCatching { setForeground(foregroundInfo(p)) }
                }
            }
            data.reload()
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: UpdateException) {
            // Dropped connections resume by themselves; WorkManager also waits for the network to return.
            if (e.retryable && runAttemptCount < 5) Result.retry() else Result.failure(workDataOf(MESSAGE to e.message))
        } catch (e: Exception) {
            Result.failure(workDataOf(MESSAGE to "Something went wrong: ${e.message}"))
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(null)

    private fun foregroundInfo(progress: Progress?): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Dictionary download", NotificationManager.IMPORTANCE_LOW),
        )
        val percent = percentOf(progress)
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Downloading the Szókert dictionary")
            .setContentText(
                when (progress) {
                    is Progress.Unpacking, Progress.Verifying -> "Preparing…"
                    else -> if (percent >= 0) "$percent%" else "Starting…"
                },
            )
            .setProgress(100, percent.coerceAtLeast(0), percent < 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val MESSAGE = "message"
        private const val CHANNEL = "dictionary"
        private const val NOTIFICATION_ID = 1

        /** Overall percent: downloading is most of the work, unpacking the last tenth. */
        fun percentOf(p: Progress?): Int = when (p) {
            is Progress.Downloading -> if (p.total > 0) (p.done * 90 / p.total).toInt() else -1
            Progress.Verifying -> 90
            is Progress.Unpacking -> if (p.total > 0) 90 + (p.done * 10 / p.total).toInt() else 90
            null -> -1
        }
    }
}

/** Daily on unmetered networks: fetch a newer dictionary quietly, if there is one. */
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val data = (applicationContext as SzokertApp).data
        if (data.store.current() == null) return Result.success() // the first download is up to the person
        return try {
            data.updater.update()
            data.reload()
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: UpdateException) {
            if (e.retryable) Result.retry() else Result.failure()
        }
    }
}
