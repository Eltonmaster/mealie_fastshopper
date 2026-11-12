package io.github.eltonmaster.fastshopper.data

import io.github.eltonmaster.fastshopper.data.api.ItemWrite
import io.github.eltonmaster.fastshopper.data.api.MealieApi
import io.github.eltonmaster.fastshopper.data.api.MealieClient
import io.github.eltonmaster.fastshopper.data.api.ShoppingList
import io.github.eltonmaster.fastshopper.data.api.ShoppingListItem
import io.github.eltonmaster.fastshopper.data.api.ShoppingListSummary
import io.github.eltonmaster.fastshopper.data.api.UserSelf
import io.github.eltonmaster.fastshopper.data.db.ItemEntity
import io.github.eltonmaster.fastshopper.data.db.LabelOrderEntity
import io.github.eltonmaster.fastshopper.data.db.ListEntity
import io.github.eltonmaster.fastshopper.data.db.OpType
import io.github.eltonmaster.fastshopper.data.db.PendingOp
import io.github.eltonmaster.fastshopper.data.db.ShoppingDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

class NotConfiguredException : IllegalStateException("App is not connected to a Mealie server")

class MealieRepository(
    private val settingsStore: SettingsStore,
    private val dao: ShoppingDao,
    private val scheduleSync: () -> Unit,
) {
    private val apiLock = Mutex()
    private val syncLock = Mutex()
    private var cachedKey: Pair<String, String>? = null
    private var cachedApi: MealieApi? = null

    private suspend fun api(): MealieApi = apiLock.withLock {
        val settings = settingsStore.settings.first()
        val url = settings.serverUrl
        val token = settings.token
        if (url.isNullOrBlank() || token.isNullOrBlank()) throw NotConfiguredException()
        val key = url to token
        cachedApi?.takeIf { cachedKey == key } ?: MealieClient.create(url, token).also {
            cachedApi = it
            cachedKey = key
        }
    }

    // ---- Setup -------------------------------------------------------------------------------

    /** Checks a server URL + token pair without saving it. */
    suspend fun verify(serverUrl: String, token: String): Result<UserSelf> =
        runCatching { MealieClient.create(serverUrl, token).getSelf() }

    suspend fun signOut() {
        dao.clearAll()
        settingsStore.clear()
    }

    // ---- Reading -----------------------------------------------------------------------------

    fun observeLists(): Flow<List<ListEntity>> = dao.observeLists()
    fun observeItems(listId: String): Flow<List<ItemEntity>> = dao.observeItems(listId)
    fun observeLabelOrder(listId: String): Flow<List<LabelOrderEntity>> = dao.observeLabelOrder(listId)
    fun observePendingCount(): Flow<Int> = dao.observePendingCount()

    suspend fun shoppingLists(): Result<List<ShoppingListSummary>> = runCatching {
        val lists = api().getShoppingLists().items
        dao.replaceLists(lists.map { ListEntity(it.id, it.name ?: "Unnamed list") })
        lists
    }

    /** Sends queued changes, then pulls the list from the server into the local cache. */
    suspend fun sync(listId: String): Result<Unit> = runCatching {
        syncLock.withLock {
            flushLocked()
            val list = api().getShoppingList(listId)
            dao.replaceListFromServer(listId, list.toItemEntities(), list.toLabelOrder())
        }
    }

    // ---- Local changes (instant, synced later) -----------------------------------------------

    suspend fun setChecked(itemId: String, checked: Boolean) {
        val item = dao.item(itemId) ?: return
        dao.upsertItem(item.copy(checked = checked))
        queue(itemId, OpType.UPDATE)
    }

    /** Hides the item right away; returns false if it doesn't exist locally. */
    suspend fun delete(itemId: String): Boolean {
        val item = dao.item(itemId) ?: return false
        val existing = dao.op(itemId)
        if (existing?.type == OpType.CREATE) {
            // Never reached the server: just forget it.
            dao.deleteOp(itemId)
            dao.deleteItem(itemId)
            return true
        }
        dao.upsertItem(item.copy(pendingDelete = true))
        dao.upsertOp(PendingOp(itemId, OpType.DELETE, System.currentTimeMillis()))
        scheduleSync()
        return true
    }

    /** Brings back a deleted item if the delete hasn't been sent yet. */
    suspend fun undoDelete(itemId: String): Boolean {
        val item = dao.item(itemId) ?: return false
        if (dao.op(itemId)?.type != OpType.DELETE) return false
        dao.upsertItem(item.copy(pendingDelete = false))
        dao.upsertOp(PendingOp(itemId, OpType.UPDATE, System.currentTimeMillis()))
        scheduleSync()
        return true
    }

    private suspend fun queue(itemId: String, type: String) {
        val existing = dao.op(itemId)
        // A not-yet-created item stays a CREATE: the create sends its latest state anyway.
        val newType = if (existing?.type == OpType.CREATE) OpType.CREATE else type
        dao.upsertOp(PendingOp(itemId, newType, System.currentTimeMillis()))
        scheduleSync()
    }

    // ---- Outbox ------------------------------------------------------------------------------

    /** Sends all queued changes. Returns true when nothing is left to send. */
    suspend fun flush(): Boolean = syncLock.withLock { flushLocked() }

    private suspend fun flushLocked(): Boolean {
        val ops = dao.pendingOps()
        if (ops.isEmpty()) return true
        val api = api()
        for (op in ops) {
            val item = dao.item(op.itemId)
            try {
                when (op.type) {
                    OpType.DELETE -> {
                        api.deleteItems(listOf(op.itemId)).close()
                        if (dao.deleteOpIfUnchanged(op.itemId, op.createdAt) > 0) dao.deleteItem(op.itemId)
                    }
                    OpType.CREATE -> {
                        if (item != null) api.createItem(item.toWrite()).close()
                        dao.deleteOpIfUnchanged(op.itemId, op.createdAt)
                    }
                    else -> {
                        if (item != null) api.updateItems(listOf(item.toWrite())).close()
                        dao.deleteOpIfUnchanged(op.itemId, op.createdAt)
                    }
                }
            } catch (e: IOException) {
                return false // offline: keep everything for later
            } catch (e: HttpException) {
                if (e.code() == 401 || e.code() == 403) throw e
                // A 4xx means the server will never accept this change (e.g. the item was
                // deleted elsewhere); give up on it. Server errors get a few retries.
                if (e.code() in 400..499 || op.attempts + 1 >= MAX_ATTEMPTS) {
                    dao.deleteOpIfUnchanged(op.itemId, op.createdAt)
                    if (op.type == OpType.DELETE) dao.deleteItem(op.itemId)
                } else {
                    dao.upsertOp(op.copy(attempts = op.attempts + 1))
                    return false
                }
            }
        }
        return dao.pendingOps().isEmpty()
    }

    private companion object {
        const val MAX_ATTEMPTS = 5
    }
}

private fun ItemEntity.toWrite() = ItemWrite(
    id = id,
    shoppingListId = listId,
    checked = checked,
    position = position,
    quantity = quantity,
    note = note,
    foodId = foodId,
    unitId = unitId,
    labelId = labelId,
)

private fun ShoppingList.toLabelOrder(): List<LabelOrderEntity> = labelSettings.map {
    LabelOrderEntity(listId = id, labelId = it.labelId, name = it.label?.name.orEmpty(), position = it.position)
}

private fun ShoppingList.toItemEntities(): List<ItemEntity> = listItems.map { it.toEntity(id) }

private fun ShoppingListItem.toEntity(listId: String) = ItemEntity(
    id = id,
    listId = shoppingListId ?: listId,
    quantity = quantity,
    note = note.orEmpty(),
    display = display.orEmpty(),
    checked = checked,
    position = position,
    foodId = foodId ?: food?.id,
    foodName = food?.name,
    unitId = unitId ?: unit?.id,
    unitName = unit?.name,
    unitAbbreviation = unit?.abbreviation,
    unitUseAbbreviation = unit?.useAbbreviation ?: false,
    labelId = labelId ?: label?.id,
    labelName = label?.name,
    updatedAt = updatedAt,
)

/** Human-readable message for errors coming from the Mealie API. */
fun Throwable.toUserMessage(): String = when (this) {
    is NotConfiguredException -> "Not connected to a Mealie server."
    is HttpException -> when (code()) {
        401, 403 -> "The server rejected the API token."
        404 -> "Not found. Check the server URL (including any sub-path)."
        in 500..599 -> "The Mealie server had an error (HTTP ${code()})."
        else -> "Request failed (HTTP ${code()})."
    }
    is UnknownHostException -> "Offline or server not found."
    is SSLException -> "Secure connection failed. Check the server's HTTPS certificate."
    is SerializationException -> "Unexpected answer from the server. Is this a Mealie server?"
    is IOException -> "Couldn't reach the server."
    else -> message ?: "Something went wrong."
}
