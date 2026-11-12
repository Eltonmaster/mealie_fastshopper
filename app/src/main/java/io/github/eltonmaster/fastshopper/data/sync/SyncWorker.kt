package io.github.eltonmaster.fastshopper.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.eltonmaster.fastshopper.FastShopperApp
import java.util.concurrent.TimeUnit

/** Sends queued list changes as soon as the phone is online, even if the app is closed. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repository = (applicationContext as FastShopperApp).container.repository
        return runCatching { repository.flush() }
            .fold(
                onSuccess = { done -> if (done) Result.success() else Result.retry() },
                onFailure = { Result.retry() },
            )
    }

    companion object {
        private const val NAME = "outbox-sync"

        fun schedule(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
