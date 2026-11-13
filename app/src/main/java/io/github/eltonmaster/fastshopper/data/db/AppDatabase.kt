package io.github.eltonmaster.fastshopper.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ListEntity::class, ItemEntity::class, LabelOrderEntity::class, PendingOp::class,
        FoodEntity::class, UnitEntity::class, LabelEntity::class, ContainerHistory::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun shoppingDao(): ShoppingDao

    companion object {
        /** v2 adds the food/unit/label catalog and container history; keeps queued changes. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `foods` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                        "`pluralName` TEXT, `labelId` TEXT, PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `units` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                        "`abbreviation` TEXT, `useAbbreviation` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `labels` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                        "`color` TEXT, PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `container_history` (`foodId` TEXT NOT NULL, " +
                        "`lastUnitId` TEXT, `timesAdded` INTEGER NOT NULL, `lastAddedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`foodId`))"
                )
            }
        }

        /** v3 adds "new"/"changed" markers on items and when each list was last synced. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `items` ADD COLUMN `remoteChange` TEXT")
                db.execSQL("ALTER TABLE `items` ADD COLUMN `remoteChangeNotified` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `lists` ADD COLUMN `lastSyncedAt` INTEGER")
            }
        }

        // No destructive fallback when upgrading: a missing migration must not throw away
        // changes that are still queued offline. Every version bump needs a real migration.
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "fastshopper.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                .build()
    }
}
