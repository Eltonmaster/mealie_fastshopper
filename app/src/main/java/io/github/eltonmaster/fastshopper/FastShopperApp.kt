package io.github.eltonmaster.fastshopper

import android.app.Application
import io.github.eltonmaster.fastshopper.data.MealieRepository
import io.github.eltonmaster.fastshopper.data.SettingsStore
import io.github.eltonmaster.fastshopper.data.db.AppDatabase
import io.github.eltonmaster.fastshopper.data.sync.SyncWorker

class FastShopperApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
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
