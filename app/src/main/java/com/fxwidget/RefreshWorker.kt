package com.fxwidget

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Background refresh via WorkManager: the system runs it when the network is
 * actually available, which is more reliable than fetching inside a widget broadcast.
 */
class RefreshWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {

    override fun doWork(): Result {
        val ok = RateRepository(applicationContext).refresh()
        Widgets.updateAll(applicationContext)
        return if (ok) Result.success() else Result.retry()
    }

    companion object {
        private val NET = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun now(ctx: Context) {
            WorkManager.getInstance(ctx).enqueueUniqueWork(
                "fx_refresh_now", ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<RefreshWorker>().setConstraints(NET).build(),
            )
        }

        fun schedulePeriodic(ctx: Context) {
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                "fx_refresh_periodic", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<RefreshWorker>(3, TimeUnit.HOURS).setConstraints(NET).build(),
            )
        }
    }
}
