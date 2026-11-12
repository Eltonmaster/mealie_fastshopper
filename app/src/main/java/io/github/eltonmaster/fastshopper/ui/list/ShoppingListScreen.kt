package io.github.eltonmaster.fastshopper.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.eltonmaster.fastshopper.data.MealieRepository
import io.github.eltonmaster.fastshopper.data.api.ShoppingList
import io.github.eltonmaster.fastshopper.data.toUserMessage
import io.github.eltonmaster.fastshopper.ui.appViewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ShoppingListUiState(
    val loading: Boolean = true,
    val list: ShoppingList? = null,
    val error: String? = null,
)

/** Read-only preview of a list. The real list screen (grouping, checking, adding) comes next. */
class ShoppingListViewModel(
    private val listId: String,
    private val repository: MealieRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ShoppingListUiState())
    val state: StateFlow<ShoppingListUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            repository.shoppingList(listId)
                .onSuccess { list -> _state.update { it.copy(loading = false, list = list) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.toUserMessage()) } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListScreen(
    listId: String,
    onBack: () -> Unit,
    viewModel: ShoppingListViewModel = viewModel(
        key = "list-$listId",
        factory = appViewModelFactory { ShoppingListViewModel(listId, it.repository) },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.list?.name ?: "Shopping list") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "All lists")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val list = state.list
            when {
                state.loading && list == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.error != null && list == null -> Column(
                    Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = viewModel::refresh) { Text("Try again") }
                }
                list != null -> LazyColumn(Modifier.fillMaxSize()) {
                    val items = list.listItems.sortedWith(compareBy({ it.checked }, { it.position }))
                    if (items.isEmpty()) {
                        item { Text("This list is empty.", Modifier.padding(16.dp)) }
                    }
                    items(items, key = { it.id }) { item ->
                        ListItem(
                            headlineContent = {
                                Text(
                                    item.display ?: item.note ?: "",
                                    textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                                    color = if (item.checked) MaterialTheme.colorScheme.onSurfaceVariant
                                    else MaterialTheme.colorScheme.onSurface,
                                )
                            },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
