package io.github.eltonmaster.fastshopper.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.eltonmaster.fastshopper.data.AutoClearDelay
import io.github.eltonmaster.fastshopper.data.ItemFormat
import io.github.eltonmaster.fastshopper.data.MealieRepository
import io.github.eltonmaster.fastshopper.data.db.ItemEntity
import io.github.eltonmaster.fastshopper.data.db.LabelOrderEntity
import io.github.eltonmaster.fastshopper.data.db.ListEntity
import io.github.eltonmaster.fastshopper.data.toUserMessage
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RowItem(
    val id: String,
    val title: String,
    val subtitle: String?,
    val amount: String,
    /** Raw quantity for the +/− stepper (0 means no amount). */
    val quantity: Double,
    val checked: Boolean,
    /** [io.github.eltonmaster.fastshopper.data.RemoteChanges.NEW] or `CHANGED` while unseen, else null. */
    val remoteChange: String? = null,
)

data class Section(
    val key: String,
    val title: String,
    val items: List<RowItem>,
)

data class ShoppingListUiState(
    val listName: String = "",
    val lists: List<ListEntity> = emptyList(),
    val sections: List<Section> = emptyList(),
    val checked: List<RowItem> = emptyList(),
    val checkedExpanded: Boolean = false,
    val autoClearDelay: AutoClearDelay = AutoClearDelay.DEFAULT,
    val pendingCount: Int = 0,
    /** Unseen "new"/"changed" items per list id, for the list switcher. */
    val newCounts: Map<String, Int> = emptyMap(),
    val loadedOnce: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
) {
    val isEmpty: Boolean get() = sections.isEmpty() && checked.isEmpty()
}

private data class Content(
    val items: List<ItemEntity>,
    val labels: List<LabelOrderEntity>,
    val lists: List<ListEntity>,
    val newCounts: Map<String, Int>,
)

private data class SyncStatus(
    val loadedOnce: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
)

class ShoppingListViewModel(
    private val listId: String,
    private val repository: MealieRepository,
) : ViewModel() {
    private val status = MutableStateFlow(SyncStatus())
    private val checkedExpanded = MutableStateFlow(false)

    private val content = combine(
        repository.observeItems(listId),
        repository.observeLabelOrder(listId),
        repository.observeLists(),
        repository.observeRemoteChangeCounts(),
    ) { items, labels, lists, newCounts -> Content(items, labels, lists, newCounts) }

    val state: StateFlow<ShoppingListUiState> = combine(
        content,
        repository.observePendingCount(),
        status,
        checkedExpanded,
        repository.observeAutoClearDelay(),
    ) { (items, labels, lists, newCounts), pending, s, expanded, autoClearDelay ->
        val (open, done) = items.partition { !it.checked }
        ShoppingListUiState(
            listName = lists.firstOrNull { it.id == listId }?.name.orEmpty(),
            lists = lists,
            sections = groupByLabel(open, labels),
            checked = done.map { it.toRow() },
            checkedExpanded = expanded,
            autoClearDelay = autoClearDelay,
            pendingCount = pending,
            newCounts = newCounts,
            loadedOnce = s.loadedOnce || items.isNotEmpty(),
            refreshing = s.refreshing,
            error = s.error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShoppingListUiState())

    init {
        viewModelScope.launch { if (state.value.lists.isEmpty()) repository.shoppingLists() }
    }

    /** Called by the screen every few seconds while visible, and by pull-to-refresh. */
    suspend fun sync(userInitiated: Boolean = false) {
        if (userInitiated) status.update { it.copy(refreshing = true) }
        val result = repository.sync(listId)
        status.update {
            it.copy(
                loadedOnce = it.loadedOnce || result.isSuccess,
                refreshing = false,
                error = result.exceptionOrNull()?.toUserMessage(),
            )
        }
    }

    fun refresh() {
        viewModelScope.launch {
            repository.shoppingLists()
            sync(userInitiated = true)
        }
    }

    fun toggle(item: RowItem) {
        viewModelScope.launch { repository.setChecked(item.id, !item.checked) }
    }

    fun changeQuantity(item: RowItem, delta: Int) {
        viewModelScope.launch { repository.changeQuantity(item.id, delta) }
    }

    /** Saves the order the open items were dragged into (all sections, top to bottom). */
    fun reorder(sections: List<Section>) {
        val ids = sections.flatMap { section -> section.items.map { it.id } }
        viewModelScope.launch { repository.reorder(listId, ids) }
    }

    fun delete(itemId: String, onDeleted: () -> Unit) {
        viewModelScope.launch { if (repository.delete(itemId)) onDeleted() }
    }

    fun undoDelete(itemId: String, onFailed: () -> Unit) {
        viewModelScope.launch { if (!repository.undoDelete(itemId)) onFailed() }
    }

    fun toggleCheckedExpanded() = checkedExpanded.update { !it }

    /**
     * Called when you leave the list after it was on screen: everything marked "new" or "changed"
     * that was shown counts as seen. Runs to the end even if the screen is being closed.
     */
    fun markShownChangesSeen() {
        val shown = state.value.let { it.sections.flatMap(Section::items) + it.checked }
            .filter { it.remoteChange != null }
            .map { it.id }
        if (shown.isEmpty()) return
        viewModelScope.launch(NonCancellable) { repository.clearRemoteChanges(shown) }
    }

    fun uncheckAll() {
        viewModelScope.launch {
            state.value.checked.forEach { repository.setChecked(it.id, false) }
        }
    }

    fun deleteAllChecked() {
        viewModelScope.launch {
            state.value.checked.forEach { repository.delete(it.id) }
        }
    }
}

private const val OTHER_KEY = "__other__"

private fun groupByLabel(items: List<ItemEntity>, labels: List<LabelOrderEntity>): List<Section> {
    val order = labels.associate { it.labelId to it.position }
    val names = labels.associate { it.labelId to it.name }
    return items
        .groupBy { it.labelId ?: OTHER_KEY }
        .map { (key, group) ->
            val title = if (key == OTHER_KEY) "Other" else names[key]?.takeIf { it.isNotBlank() }
                ?: group.first().labelName ?: "Other"
            Section(key, title, group.sortedBy { it.position }.map { it.toRow() })
        }
        .sortedWith(
            compareBy<Section> { if (it.key == OTHER_KEY) 1 else 0 }
                .thenBy { order[it.key] ?: Int.MAX_VALUE }
                .thenBy { it.title.lowercase() }
        )
}

private fun ItemEntity.toRow() = RowItem(
    id = id,
    title = ItemFormat.title(foodName, note, display),
    subtitle = ItemFormat.subtitle(foodName, note),
    amount = ItemFormat.amount(quantity, unitName, unitAbbreviation, unitUseAbbreviation),
    quantity = quantity,
    checked = checked,
    remoteChange = remoteChange,
)
