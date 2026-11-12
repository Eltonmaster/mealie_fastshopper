package io.github.eltonmaster.fastshopper.ui.lists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

data class ListPickerUiState(
    val loading: Boolean = true,
    val lists: List<ShoppingListSummary> = emptyList(),
    val error: String? = null,
    val defaultListId: String? = null,
    val userName: String? = null,
    val serverUrl: String? = null,
)

class ListPickerViewModel(
    private val repository: MealieRepository,
    private val settingsStore: SettingsStore,
) : ViewModel() {
    private val load = MutableStateFlow(ListPickerUiState())

    val state: StateFlow<ListPickerUiState> =
        combine(load, settingsStore.settings) { s, settings ->
            s.copy(
                defaultListId = settings.defaultListId,
                userName = settings.userName,
                serverUrl = settings.serverUrl,
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
            settingsStore.clear()
            onDone()
        }
    }
}
