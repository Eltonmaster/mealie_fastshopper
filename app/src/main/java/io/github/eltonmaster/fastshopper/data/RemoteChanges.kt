package io.github.eltonmaster.fastshopper.data

import io.github.eltonmaster.fastshopper.data.db.ItemEntity

/**
 * Marks items that someone else added or changed since this phone last looked (unit-tested).
 *
 * Stored in [ItemEntity.remoteChange]; the list screen shows it as a small badge until the list
 * has been on screen and is left, or until you check or edit the item yourself.
 */
object RemoteChanges {
    const val NEW = "NEW"
    const val CHANGED = "CHANGED"

    /**
     * Returns [incoming] (the server's copy of a list) with [ItemEntity.remoteChange] and
     * [ItemEntity.remoteChangeNotified] filled in, based on what was [cached] before.
     *
     * - An id the cache didn't have is [NEW], except on the list's very first sync ([firstSync]),
     *   where everything is new to this phone and marking it all would be noise.
     * - A cached item is [CHANGED] when its `updatedAt` moved and its name, amount, unit, note or
     *   label differ. A check or uncheck alone doesn't count, or every shopping trip would light up
     *   the whole list.
     * - Your own changes are never marked: items in [ownItemIds] (queued or just sent from this
     *   phone) keep what they had, and so do items this phone created and never got back from the
     *   server yet (no `updatedAt`), because the app caches its own adds before syncing them.
     * - Checked items carry no marker: there's nothing left to look at.
     * - Otherwise an existing marker is kept, so a sync never clears what you haven't seen.
     *
     * A freshly set marker starts un-notified; a kept one keeps its notified flag.
     */
    fun mark(
        cached: List<ItemEntity>,
        incoming: List<ItemEntity>,
        ownItemIds: Set<String>,
        firstSync: Boolean,
    ): List<ItemEntity> {
        val before = cached.associateBy { it.id }
        return incoming.map { item ->
            val old = before[item.id]
            val fresh = when {
                item.checked -> null
                old == null -> if (firstSync) null else NEW
                item.id in ownItemIds || old.updatedAt == null || old.remoteChange == NEW -> null
                item.updatedAt != old.updatedAt && contentDiffers(old, item) -> CHANGED
                else -> null
            }
            when {
                item.checked -> item.copy(remoteChange = null, remoteChangeNotified = false)
                fresh != null -> item.copy(remoteChange = fresh, remoteChangeNotified = false)
                old != null -> item.copy(remoteChange = old.remoteChange, remoteChangeNotified = old.remoteChangeNotified)
                else -> item.copy(remoteChange = null, remoteChangeNotified = false)
            }
        }
    }

    private fun contentDiffers(a: ItemEntity, b: ItemEntity): Boolean =
        a.foodId != b.foodId ||
            a.note.trim() != b.note.trim() ||
            a.quantity != b.quantity ||
            a.unitId != b.unitId ||
            a.labelId != b.labelId
}
