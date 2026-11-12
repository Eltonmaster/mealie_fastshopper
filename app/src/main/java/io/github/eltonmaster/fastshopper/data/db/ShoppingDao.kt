package io.github.eltonmaster.fastshopper.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ShoppingDao {
    @Query("SELECT * FROM lists ORDER BY name COLLATE NOCASE")
    abstract fun observeLists(): Flow<List<ListEntity>>

    @Query("SELECT * FROM items WHERE listId = :listId AND pendingDelete = 0 ORDER BY position")
    abstract fun observeItems(listId: String): Flow<List<ItemEntity>>

    @Query("SELECT * FROM label_order WHERE listId = :listId ORDER BY position")
    abstract fun observeLabelOrder(listId: String): Flow<List<LabelOrderEntity>>

    @Query("SELECT COUNT(*) FROM pending_ops")
    abstract fun observePendingCount(): Flow<Int>

    @Query("SELECT * FROM items WHERE id = :id")
    abstract suspend fun item(id: String): ItemEntity?

    @Upsert
    abstract suspend fun upsertItem(item: ItemEntity)

    @Upsert
    abstract suspend fun upsertItems(items: List<ItemEntity>)

    @Query("DELETE FROM items WHERE id = :id")
    abstract suspend fun deleteItem(id: String)

    @Query("DELETE FROM items WHERE listId = :listId AND id NOT IN (SELECT itemId FROM pending_ops)")
    protected abstract suspend fun deleteSyncedItems(listId: String)

    @Query("DELETE FROM label_order WHERE listId = :listId")
    protected abstract suspend fun deleteLabelOrder(listId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertLabelOrder(labels: List<LabelOrderEntity>)

    @Query("DELETE FROM lists")
    protected abstract suspend fun deleteLists()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertLists(lists: List<ListEntity>)

    @Query("SELECT * FROM pending_ops ORDER BY createdAt")
    abstract suspend fun pendingOps(): List<PendingOp>

    @Query("SELECT itemId FROM pending_ops")
    abstract suspend fun pendingItemIds(): List<String>

    @Query("SELECT * FROM pending_ops WHERE itemId = :itemId")
    abstract suspend fun op(itemId: String): PendingOp?

    @Upsert
    abstract suspend fun upsertOp(op: PendingOp)

    @Query("DELETE FROM pending_ops WHERE itemId = :itemId")
    abstract suspend fun deleteOp(itemId: String)

    /** Removes the op only if nobody queued a newer change for the item meanwhile. */
    @Query("DELETE FROM pending_ops WHERE itemId = :itemId AND createdAt = :createdAt")
    abstract suspend fun deleteOpIfUnchanged(itemId: String, createdAt: Long): Int

    @Transaction
    open suspend fun replaceLists(lists: List<ListEntity>) {
        deleteLists()
        insertLists(lists)
    }

    /** Replaces a list with the server's copy, keeping items that still have local changes queued. */
    @Transaction
    open suspend fun replaceListFromServer(
        listId: String,
        items: List<ItemEntity>,
        labels: List<LabelOrderEntity>,
    ) {
        val pending = pendingItemIds().toSet()
        deleteSyncedItems(listId)
        upsertItems(items.filter { it.id !in pending })
        deleteLabelOrder(listId)
        insertLabelOrder(labels)
    }

    @Query("DELETE FROM items")
    protected abstract suspend fun deleteAllItems()

    @Query("DELETE FROM label_order")
    protected abstract suspend fun deleteAllLabelOrder()

    @Query("DELETE FROM pending_ops")
    protected abstract suspend fun deleteAllOps()

    @Transaction
    open suspend fun clearAll() {
        deleteAllOps()
        deleteAllItems()
        deleteAllLabelOrder()
        deleteLists()
    }
}
