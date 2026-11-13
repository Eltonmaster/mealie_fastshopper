package io.github.eltonmaster.fastshopper.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import io.github.eltonmaster.fastshopper.data.RemoteChanges
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

    /** Replaces the lists with the server's, keeping when each one was last synced here. */
    @Transaction
    open suspend fun replaceLists(lists: List<ListEntity>) {
        val syncedAt = allLists().associate { it.id to it.lastSyncedAt }
        deleteLists()
        insertLists(lists.map { it.copy(lastSyncedAt = it.lastSyncedAt ?: syncedAt[it.id]) })
    }

    /**
     * Replaces a list with the server's copy, keeping items that still have local changes queued.
     * Marks what someone else added or changed (see [RemoteChanges.mark]) and keeps existing
     * markers. Runs as one transaction so a marker cleared meanwhile can't come back.
     */
    @Transaction
    open suspend fun replaceListFromServer(
        listId: String,
        items: List<ItemEntity>,
        labels: List<LabelOrderEntity>,
        ownItemIds: Set<String>,
        syncedAt: Long,
    ) {
        val pending = pendingItemIds().toSet()
        val marked = RemoteChanges.mark(
            cached = itemsOfList(listId),
            incoming = items,
            ownItemIds = ownItemIds + pending,
            firstSync = listSyncedAt(listId) == null,
        )
        deleteSyncedItems(listId)
        upsertItems(marked.filter { it.id !in pending })
        deleteLabelOrder(listId)
        insertLabelOrder(labels)
        setListSyncedAt(listId, syncedAt)
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
        deleteFoods()
        deleteUnits()
        deleteLabels()
        deleteAllHistory()
    }

    @Query("DELETE FROM container_history")
    protected abstract suspend fun deleteAllHistory()

    // ---- Catalog: foods, units, labels -------------------------------------------------------

    @Query("SELECT * FROM foods ORDER BY name COLLATE NOCASE")
    abstract fun observeFoods(): Flow<List<FoodEntity>>

    @Query("SELECT * FROM units ORDER BY name COLLATE NOCASE")
    abstract fun observeUnits(): Flow<List<UnitEntity>>

    @Query("SELECT * FROM labels ORDER BY name COLLATE NOCASE")
    abstract fun observeLabels(): Flow<List<LabelEntity>>

    @Query("SELECT COUNT(*) FROM foods")
    abstract suspend fun foodCount(): Int

    @Query("DELETE FROM foods")
    protected abstract suspend fun deleteFoods()

    @Query("DELETE FROM units")
    protected abstract suspend fun deleteUnits()

    @Query("DELETE FROM labels")
    protected abstract suspend fun deleteLabels()

    @Upsert
    abstract suspend fun upsertFood(food: FoodEntity)

    @Upsert
    abstract suspend fun upsertUnit(unit: UnitEntity)

    @Upsert
    abstract suspend fun upsertLabel(label: LabelEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertFoods(foods: List<FoodEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertUnits(units: List<UnitEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertLabels(labels: List<LabelEntity>)

    @Transaction
    open suspend fun replaceCatalog(foods: List<FoodEntity>, units: List<UnitEntity>, labels: List<LabelEntity>) {
        deleteFoods()
        insertFoods(foods)
        deleteUnits()
        insertUnits(units)
        deleteLabels()
        insertLabels(labels)
    }

    // ---- Container history -------------------------------------------------------------------

    @Query("SELECT * FROM container_history")
    abstract fun observeHistory(): Flow<List<ContainerHistory>>

    @Query("SELECT * FROM container_history WHERE foodId = :foodId")
    abstract suspend fun history(foodId: String): ContainerHistory?

    @Upsert
    abstract suspend fun upsertHistory(entry: ContainerHistory)

    @Query("SELECT id FROM items WHERE listId = :listId")
    abstract suspend fun itemIds(listId: String): List<String>

    @Query("SELECT MAX(position) FROM items WHERE listId = :listId")
    abstract suspend fun maxPosition(listId: String): Int?

    @Query("SELECT * FROM foods WHERE id = :id")
    abstract suspend fun food(id: String): FoodEntity?

    @Query("SELECT * FROM units WHERE id = :id")
    abstract suspend fun unit(id: String): UnitEntity?

    @Query("SELECT * FROM labels WHERE id = :id")
    abstract suspend fun label(id: String): LabelEntity?

    @Query("SELECT * FROM items WHERE listId = :listId")
    abstract suspend fun itemsOfList(listId: String): List<ItemEntity>

    @Query("SELECT DISTINCT listId FROM items")
    abstract suspend fun cachedListIds(): List<String>

    // ---- Reordering --------------------------------------------------------------------------

    @Query("UPDATE items SET position = :position WHERE id = :id")
    protected abstract suspend fun setPosition(id: String, position: Int)

    /**
     * Writes new positions and queues an UPDATE for each item in one transaction, so a sync
     * can't replace the items between the two. A not-yet-created item stays a CREATE.
     */
    @Transaction
    open suspend fun applyPositions(positions: Map<String, Int>, queuedAt: Long) {
        positions.forEach { (id, position) ->
            setPosition(id, position)
            val type = if (op(id)?.type == OpType.CREATE) OpType.CREATE else OpType.UPDATE
            upsertOp(PendingOp(id, type, queuedAt))
        }
    }

    // ---- "New" markers -----------------------------------------------------------------------

    @Query("SELECT * FROM lists")
    abstract suspend fun allLists(): List<ListEntity>

    @Query("SELECT lastSyncedAt FROM lists WHERE id = :listId")
    abstract suspend fun listSyncedAt(listId: String): Long?

    @Query("UPDATE lists SET lastSyncedAt = :at WHERE id = :listId")
    abstract suspend fun setListSyncedAt(listId: String, at: Long)

    /** Open items per list that someone else added or changed and you haven't seen yet. */
    @Query(
        "SELECT listId, COUNT(*) AS count FROM items " +
            "WHERE remoteChange IS NOT NULL AND checked = 0 AND pendingDelete = 0 GROUP BY listId"
    )
    abstract fun observeRemoteChangeCounts(): Flow<List<ListChangeCount>>

    @Query("UPDATE items SET remoteChange = NULL, remoteChangeNotified = 0 WHERE id IN (:itemIds)")
    abstract suspend fun clearRemoteChanges(itemIds: List<String>)

    // ---- Change notifications ----------------------------------------------------------------

    /** Lists opened on this phone before (synced at least once, or with items cached). */
    @Query("SELECT id FROM lists WHERE lastSyncedAt IS NOT NULL UNION SELECT DISTINCT listId FROM items")
    abstract suspend fun watchedListIds(): List<String>

    /**
     * All unseen "new"/"changed" open items of the lists that have at least one a notification
     * hasn't mentioned yet.
     */
    @Query(
        "SELECT * FROM items WHERE remoteChange IS NOT NULL AND checked = 0 AND pendingDelete = 0 " +
            "AND listId IN (SELECT listId FROM items WHERE remoteChange IS NOT NULL " +
            "AND remoteChangeNotified = 0 AND checked = 0 AND pendingDelete = 0) " +
            "ORDER BY listId, position"
    )
    abstract suspend fun unseenChangesToNotify(): List<ItemEntity>

    @Query("UPDATE items SET remoteChangeNotified = 1 WHERE id IN (:itemIds)")
    abstract suspend fun markRemoteChangesNotified(itemIds: List<String>)

    // ---- List management ---------------------------------------------------------------------

    @Upsert
    abstract suspend fun upsertList(list: ListEntity)

    /** Changes only the name, so any other cached fields of the list stay as they are. */
    @Query("UPDATE lists SET name = :name WHERE id = :listId")
    abstract suspend fun renameList(listId: String, name: String)

    @Query("DELETE FROM lists WHERE id = :listId")
    protected abstract suspend fun deleteListRow(listId: String)

    @Query("DELETE FROM items WHERE listId = :listId")
    protected abstract suspend fun deleteItemsOfList(listId: String)

    @Query("DELETE FROM pending_ops WHERE itemId IN (SELECT id FROM items WHERE listId = :listId)")
    protected abstract suspend fun deleteOpsOfList(listId: String)

    /** Removes a deleted list from the cache: its items, label order and queued changes. */
    @Transaction
    open suspend fun forgetList(listId: String) {
        deleteOpsOfList(listId)
        deleteItemsOfList(listId)
        deleteLabelOrder(listId)
        deleteListRow(listId)
    }
}

data class ListChangeCount(val listId: String, val count: Int)
