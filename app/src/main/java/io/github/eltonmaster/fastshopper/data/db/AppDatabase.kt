package io.github.eltonmaster.fastshopper.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ListEntity::class, ItemEntity::class, LabelOrderEntity::class, PendingOp::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun shoppingDao(): ShoppingDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "fastshopper.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
