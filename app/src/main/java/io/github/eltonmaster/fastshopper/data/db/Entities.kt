package io.github.eltonmaster.fastshopper.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "lists")
data class ListEntity(
    @PrimaryKey val id: String,
    val name: String,
)

@Entity(tableName = "items", indices = [Index("listId")])
data class ItemEntity(
    @PrimaryKey val id: String,
    val listId: String,
    val quantity: Double,
    val note: String,
    val display: String,
    val checked: Boolean,
    val position: Int,
    val foodId: String?,
    val foodName: String?,
    val unitId: String?,
    val unitName: String?,
    val unitAbbreviation: String?,
    val unitUseAbbreviation: Boolean,
    val labelId: String?,
    val labelName: String?,
    val updatedAt: String?,
    /** Hidden locally while a delete waits to be sent (or undone). */
    val pendingDelete: Boolean = false,
)

@Entity(tableName = "label_order", primaryKeys = ["listId", "labelId"])
data class LabelOrderEntity(
    val listId: String,
    val labelId: String,
    val name: String,
    val position: Int,
)

object OpType {
    const val CREATE = "CREATE"
    const val UPDATE = "UPDATE"
    const val DELETE = "DELETE"
}

/** One pending change per item, collapsed: the item's current local state is what gets sent. */
@Entity(tableName = "pending_ops")
data class PendingOp(
    @PrimaryKey val itemId: String,
    val type: String,
    val createdAt: Long,
    val attempts: Int = 0,
)

@Entity(tableName = "foods")
data class FoodEntity(
    @PrimaryKey val id: String,
    val name: String,
    val pluralName: String?,
    val labelId: String?,
)

@Entity(tableName = "units")
data class UnitEntity(
    @PrimaryKey val id: String,
    val name: String,
    val abbreviation: String?,
    val useAbbreviation: Boolean,
)

@Entity(tableName = "labels")
data class LabelEntity(
    @PrimaryKey val id: String,
    val name: String,
    val color: String?,
)

/** Remembers which container (unit) each food was last bought in, and how often it's added. */
@Entity(tableName = "container_history")
data class ContainerHistory(
    @PrimaryKey val foodId: String,
    val lastUnitId: String?,
    val timesAdded: Int,
    val lastAddedAt: Long,
)
