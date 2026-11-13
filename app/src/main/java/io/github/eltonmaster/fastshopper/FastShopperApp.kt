package io.github.eltonmaster.fastshopper

import android.app.Application
import io.github.eltonmaster.fastshopper.data.MealieRepository
import io.github.eltonmaster.fastshopper.data.SettingsStore
import io.github.eltonmaster.fastshopper.data.db.AppDatabase
import io.github.eltonmaster.fastshopper.data.sync.AutoClearWorker
import io.github.eltonmaster.fastshopper.data.sync.ChangeWatchWorker
import io.github.eltonmaster.fastshopper.data.sync.SyncWorker
import io.github.eltonmaster.fastshopper.notify.ChangeNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

class FastShopperApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        AutoClearWorker.schedule(this)
        watchChangesWhileEnabled()
    }

    /** Runs the change watcher only while notifications are on; turning them off clears them. */
    private fun watchChangesWhileEnabled() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val enabled = container.settingsStore.notifyChanges
        scope.launch {
            enabled.collect { on -> ChangeWatchWorker.schedule(this@FastShopperApp, on) }
        }
        scope.launch {
            enabled.drop(1).collect { on -> if (!on) ChangeNotifier.cancelAll(this@FastShopperApp) }
        }
    }
}

/** Simple manual dependency container; swapped for Hilt once the app grows. */
class AppContainer(app: Application) {
    val settingsStore = SettingsStore(app)
    val database = AppDatabase.create(app)
    val repository = MealieRepository(
        settingsStore = settingsStore,
        dao = database.shoppingDao(),
        scheduleSync = { SyncWorker.schedule(app) },
    )
}
