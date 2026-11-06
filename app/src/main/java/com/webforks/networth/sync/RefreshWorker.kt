package com.webforks.networth.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.webforks.networth.container
import com.webforks.networth.widget.NetWorthWidget
import java.util.concurrent.TimeUnit

/** Pulls fresh balances from Plaid in the background, then redraws the widget. */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repo = applicationContext.container.repository
        val result = runCatching { repo.refreshAll() }
        NetWorthWidget.updateAll(applicationContext)
        return when {
            result.isFailure -> Result.retry()
            // Partial failures (e.g. one bank needs re-login) are shown in the app; don't retry.
            else -> Result.success()
        }
    }
}

object RefreshScheduler {
    private const val PERIODIC = "networth-periodic-refresh"
    private const val ONE_SHOT = "networth-refresh-now"

    private val networkConstraint = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun schedule(context: Context, hours: Int, replace: Boolean = false) {
        val request = PeriodicWorkRequestBuilder<RefreshWorker>(hours.toLong(), TimeUnit.HOURS)
            .setConstraints(networkConstraint)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC,
            if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun refreshNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<RefreshWorker>()
            .setConstraints(networkConstraint)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(ONE_SHOT, ExistingWorkPolicy.REPLACE, request)
    }
}
