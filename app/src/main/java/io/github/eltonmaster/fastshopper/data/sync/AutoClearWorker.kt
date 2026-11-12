package io.github.eltonmaster.fastshopper.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.eltonmaster.fastshopper.FastShopperApp
import java.util.concurrent.TimeUnit

/** Every few hours: sync cached lists, which also deletes items checked more than 12 hours ago. */
class AutoClearWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as FastShopperApp).container
        return runCatching { container.repository.syncAllCachedLists() }
            .fold(onSuccess = { if (it) Result.success() else Result.retry() }, onFailure = { Result.retry() })
    }

    companion object {
        private const val NAME = "auto-clear"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<AutoClearWorker>(3, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
