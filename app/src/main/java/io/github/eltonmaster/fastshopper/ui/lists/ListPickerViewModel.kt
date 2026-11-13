package io.github.eltonmaster.fastshopper.ui.lists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.eltonmaster.fastshopper.data.ListEdit
import io.github.eltonmaster.fastshopper.data.MealieRepository
import io.github.eltonmaster.fastshopper.data.SettingsStore
import io.github.eltonmaster.fastshopper.data.api.ShoppingListSummary
import io.github.eltonmaster.fastshopper.data.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The create, rename or delete dialog that's open on the picker. */
sealed interface ListDialog {
    data object Create : ListDialog
    data class Rename(val list: ShoppingListSummary) : ListDialog
    data class Delete(
        val list: ShoppingListSummary,
        /** Items on the server, or null while counting or when the count couldn't be loaded. */
        val itemCount: Int? = null,
        val counting: Boolean = true,
    ) : ListDialog
}

data class ListPickerUiState(
    val loading: Boolean = true,
    val lists: List<ShoppingListSummary> = emptyList(),
    val error: String? = null,
    val defaultListId: String? = null,
    val userName: String? = null,
    val serverUrl: String? = null,
    /** Open items per list id that someone else added or changed since you last looked. */
    val newCounts: Map<String, Int> = emptyMap(),
    val dialog: ListDialog? = null,
    /** A create, rename or delete is waiting for the server. */
    val working: Boolean = false,
    val dialogError: String? = null,
)

class ListPickerViewModel(
    private val repository: MealieRepository,
    private val settingsStore: SettingsStore,
) : ViewModel() {
    private val load = MutableStateFlow(ListPickerUiState())

    val state: StateFlow<ListPickerUiState> =
        combine(load, settingsStore.settings, repository.observeRemoteChangeCounts()) { s, settings, newCounts ->
            s.copy(
                defaultListId = settings.defaultListId,
                userName = settings.userName,
                serverUrl = settings.serverUrl,
                newCounts = newCounts,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListPickerUiState())

    init {
        refresh()
    }

    fun refresh() {
        load.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            repository.shoppingLists()
                .onSuccess { lists -> load.update { it.copy(loading = false, lists = lists) } }
                .onFailure { e -> load.update { it.copy(loading = false, error = e.toUserMessage()) } }
        }
    }

    fun toggleDefault(listId: String) {
        viewModelScope.launch {
            val newDefault = if (state.value.defaultListId == listId) null else listId
            settingsStore.setDefaultList(newDefault)
        }
    }

    fun signOut(onDone: () -> Unit) {
        viewModelScope.launch {
            repository.signOut()
            onDone()
        }
    }

    // ---- Create, rename, delete --------------------------------------------------------------

    fun startCreate() = open(ListDialog.Create)

    fun startRename(list: ShoppingListSummary) = open(ListDialog.Rename(list))

    fun startDelete(list: ShoppingListSummary) {
        open(ListDialog.Delete(list))
        viewModelScope.launch {
            val count = repository.listItemCount(list.id).getOrNull()
            load.update { s ->
                val d = s.dialog
                if (d is ListDialog.Delete && d.list.id == list.id) {
                    s.copy(dialog = d.copy(itemCount = count, counting = false))
                } else s
            }
        }
    }

    fun dismissDialog() {
        if (load.value.working) return
        load.update { it.copy(dialog = null, dialogError = null) }
    }

    /** Confirms the create or rename dialog with the typed name. */
    fun confirmName(input: String) {
        val name = ListEdit.cleanName(input) ?: return
        val dialog = load.value.dialog
        if (load.value.working) return
        when (dialog) {
            ListDialog.Create -> submit {
                repository.createList(name).map { created ->
                    load.update { s -> s.copy(lists = (s.lists + created).sortedBy { it.name.orEmpty().lowercase() }) }
                }
            }
            is ListDialog.Rename -> submit {
                if (name == dialog.list.name) return@submit Result.success(Unit)
                repository.renameList(dialog.list.id, name).map {
                    load.update { s ->
                        s.copy(lists = s.lists.map { if (it.id == dialog.list.id) it.copy(name = name) else it })
                    }
                }
            }
            else -> Unit
        }
    }

    fun confirmDelete() {
        val dialog = load.value.dialog as? ListDialog.Delete ?: return
        if (load.value.working) return
        submit {
            repository.deleteList(dialog.list.id).map {
                load.update { s -> s.copy(lists = s.lists.filterNot { it.id == dialog.list.id }) }
            }
        }
    }

    private fun open(dialog: ListDialog) {
        if (load.value.working) return
        load.update { it.copy(dialog = dialog, dialogError = null) }
    }

    /** Runs a server change for the open dialog: closes it on success, shows the error otherwise. */
    private fun submit(block: suspend () -> Result<Unit>) {
        load.update { it.copy(working = true, dialogError = null) }
        viewModelScope.launch {
            block()
                .onSuccess {
                    load.update { it.copy(working = false, dialog = null) }
                    refresh()
                }
                .onFailure { e -> load.update { it.copy(working = false, dialogError = e.toUserMessage()) } }
        }
    }
}
