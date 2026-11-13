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
import io.github.eltonmaster.fastshopper.notify.ChangeNotifier
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * While change notifications are on: every 15 minutes (Android's minimum; Doze can stretch it)
 * sync the lists opened on this phone and post a notification for what someone else changed.
 * The sync also runs the auto-clear, like [AutoClearWorker] does.
 */
class ChangeWatchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as FastShopperApp).container
        if (!container.settingsStore.notifyChanges.first()) return Result.success()
        // A failed list (offline, server down) still lets the others notify; the next run retries.
        runCatching { container.repository.syncWatchedLists() }
        runCatching { ChangeNotifier.notifyPending(applicationContext, container.repository) }
        return Result.success()
    }

    companion object {
        private const val NAME = "change-watch"

        fun schedule(context: Context, enabled: Boolean) {
            val workManager = WorkManager.getInstance(context)
            if (!enabled) {
                workManager.cancelUniqueWork(NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<ChangeWatchWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            workManager.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
