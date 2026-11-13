package io.github.eltonmaster.fastshopper.ui.list

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.eltonmaster.fastshopper.data.ItemOrder
import io.github.eltonmaster.fastshopper.ui.appViewModelFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private const val POLL_INTERVAL_MS = 5_000L

/** How long a dropped order waits for the database before the list shows the stored order again. */
private const val DROP_SETTLE_MS = 2_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListScreen(
    listId: String,
    onAllLists: () -> Unit,
    onSwitchList: (String) -> Unit,
    viewModel: ShoppingListViewModel = viewModel(
        key = "list-$listId",
        factory = appViewModelFactory { ShoppingListViewModel(listId, it.repository) },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var switcherOpen by remember { mutableStateOf(false) }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var editItemId by rememberSaveable { mutableStateOf<String?>(null) }
    var editSession by rememberSaveable { mutableIntStateOf(0) }
    val stepper = rememberStepperState()
    val haptics = LocalHapticFeedback.current

    // Reorder mode: drag handles instead of the amount, until Done (or back).
    var reordering by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    BackHandler(enabled = reordering) { reordering = false }
    LaunchedEffect(state.sections.isEmpty()) { if (state.sections.isEmpty()) reordering = false }
    // While dragging (and until the saved order comes back from the database) the list shows
    // this local copy, so syncs in the background don't make items jump under the finger.
    var dragOrder by remember { mutableStateOf<List<Section>?>(null) }
    var draggingSection by remember { mutableStateOf<String?>(null) }
    val sections = dragOrder ?: state.sections
    LaunchedEffect(state.sections, draggingSection) {
        if (draggingSection != null) return@LaunchedEffect
        val pending = dragOrder ?: return@LaunchedEffect
        if (pending.itemIds() != state.sections.itemIds()) {
            // Not saved yet; give the database a moment, then show what it has either way.
            delay(DROP_SETTLE_MS)
        }
        dragOrder = null
    }
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        dragOrder?.let { current -> dragOrder = moveWithinSection(current, from.key, to.key) }
    }
    val onEdit: (RowItem) -> Unit = { item ->
        editSession++
        editItemId = item.id
    }

    // Poll the server while the list is on screen.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner, listId) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                viewModel.sync()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    val onDelete: (RowItem) -> Unit = { item ->
        viewModel.delete(item.id) {
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                val result = snackbar.showSnackbar(
                    message = "Deleted ${item.title}",
                    actionLabel = "Undo",
                    duration = SnackbarDuration.Short,
                )
                if (result == SnackbarResult.ActionPerformed) {
                    viewModel.undoDelete(item.id) {
                        scope.launch { snackbar.showSnackbar("Already deleted on the server") }
                    }
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (!reordering) {
                FloatingActionButton(onClick = { showAdd = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "Add item")
                }
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Box {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable { switcherOpen = true }
                                .padding(vertical = 8.dp),
                        ) {
                            Text(
                                state.listName.ifBlank { "Shopping list" },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = "Switch list")
                        }
                        DropdownMenu(expanded = switcherOpen, onDismissRequest = { switcherOpen = false }) {
                            state.lists.forEach { list ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            list.name,
                                            fontWeight = if (list.id == listId) FontWeight.Bold else FontWeight.Normal,
                                        )
                                    },
                                    onClick = {
                                        switcherOpen = false
                                        if (list.id != listId) onSwitchList(list.id)
                                    },
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("All lists & settings") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                                onClick = {
                                    switcherOpen = false
                                    onAllLists()
                                },
                            )
                        }
                    }
                },
                actions = {
                    if (reordering) {
                        TextButton(onClick = { reordering = false }) { Text("Done") }
                    } else {
                        IconButton(onClick = viewModel::refresh) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                        }
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Reorder items") },
                                    leadingIcon = { Icon(Icons.Filled.SwapVert, contentDescription = null) },
                                    enabled = state.sections.isNotEmpty(),
                                    onClick = {
                                        menuOpen = false
                                        stepper.hide()
                                        reordering = true
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (showAdd) {
            AddItemSheet(
                listId = listId,
                listName = state.listName,
                onDismiss = { showAdd = false },
                onMessage = { msg -> scope.launch { snackbar.showSnackbar(msg) } },
            )
        }
        editItemId?.let { id ->
            EditItemSheet(
                listId = listId,
                itemId = id,
                session = editSession,
                onDismiss = { editItemId = null },
                onMessage = { msg -> scope.launch { snackbar.showSnackbar(msg) } },
            )
        }
        Column(Modifier.fillMaxSize().padding(padding)) {
            SyncBanner(pendingCount = state.pendingCount, error = state.error)
            if (reordering) ReorderBanner()
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    !state.loadedOnce && state.isEmpty && state.error == null ->
                        CircularProgressIndicator(Modifier.align(Alignment.Center))

                    state.isEmpty ->
                        LazyColumn(Modifier.fillMaxSize()) {
                            item {
                                Text(
                                    if (state.loadedOnce) "Nothing on this list." else "No cached copy of this list yet.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(24.dp),
                                )
                            }
                        }

                    else -> LazyColumn(Modifier.fillMaxSize(), state = listState) {
                        sections.forEach { section ->
                            item(key = "header-${section.key}") { SectionHeader(section.title) }
                            items(section.items, key = { it.id }) { item ->
                                // Only items of the section being dragged in can be dropped on.
                                ReorderableItem(
                                    reorderState,
                                    key = item.id,
                                    enabled = reordering && (draggingSection == null || draggingSection == section.key),
                                ) { isDragging ->
                                    val handle = if (reordering) {
                                        Modifier.draggableHandle(
                                            onDragStarted = {
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                draggingSection = section.key
                                                if (dragOrder == null) dragOrder = state.sections
                                            },
                                            onDragStopped = {
                                                dragOrder?.let { dropped ->
                                                    if (dropped.itemIds() != state.sections.itemIds()) {
                                                        viewModel.reorder(dropped)
                                                    }
                                                }
                                                draggingSection = null
                                            },
                                        )
                                    } else {
                                        null
                                    }
                                    Surface(shadowElevation = if (isDragging) 6.dp else 0.dp) {
                                        SwipeableRow(
                                            item,
                                            onToggle = { viewModel.toggle(item) },
                                            onEdit = { onEdit(item) },
                                            onDelete = { onDelete(item) },
                                            stepper = stepper,
                                            onStep = { delta -> viewModel.changeQuantity(item, delta) },
                                            dragHandle = handle,
                                        )
                                    }
                                }
                            }
                        }
                        checkedSection(
                            items = state.checked,
                            expanded = state.checkedExpanded,
                            clearHint = state.autoClearDelay.hint,
                            onToggleExpanded = viewModel::toggleCheckedExpanded,
                            onToggle = viewModel::toggle,
                            onEdit = onEdit,
                            onDelete = onDelete,
                            onUncheckAll = viewModel::uncheckAll,
                            onDeleteAll = viewModel::deleteAllChecked,
                        )
                        item(key = "bottom-space") { Spacer(Modifier.heightIn(min = 88.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncBanner(pendingCount: Int, error: String?) {
    val text = when {
        error != null && pendingCount > 0 -> "$error ${changes(pendingCount)} waiting to sync."
        error != null -> error
        pendingCount > 0 -> "Syncing ${changes(pendingCount)}…"
        else -> return
    }
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
}

private fun changes(n: Int) = if (n == 1) "1 change" else "$n changes"

@Composable
private fun ReorderBanner() {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Text(
            "Drag the handles to reorder items within a section. Tap Done when finished.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
}

private fun List<Section>.itemIds(): List<String> = flatMap { section -> section.items.map { it.id } }

/** Moves an item onto another item's place, only when both are in the same section. */
private fun moveWithinSection(sections: List<Section>, fromId: Any, toId: Any): List<Section> {
    val index = sections.indexOfFirst { section -> section.items.any { it.id == fromId } }
    if (index < 0) return sections
    val section = sections[index]
    val from = section.items.indexOfFirst { it.id == fromId }
    val to = section.items.indexOfFirst { it.id == toId }
    if (to < 0) return sections
    return sections.toMutableList().also { it[index] = section.copy(items = ItemOrder.move(section.items, from, to)) }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

private fun LazyListScope.checkedSection(
    items: List<RowItem>,
    expanded: Boolean,
    clearHint: String,
    onToggleExpanded: () -> Unit,
    onToggle: (RowItem) -> Unit,
    onEdit: (RowItem) -> Unit,
    onDelete: (RowItem) -> Unit,
    onUncheckAll: () -> Unit,
    onDeleteAll: () -> Unit,
) {
    if (items.isEmpty()) return
    item(key = "checked-header") {
        Column {
            HorizontalDivider(Modifier.padding(top = 12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(onClick = onToggleExpanded)
                    .padding(horizontal = 16.dp),
            ) {
                Text(
                    "Checked (${items.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Hide checked items" else "Show checked items",
                )
            }
            if (expanded) {
                Text(
                    clearHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Row(Modifier.padding(horizontal = 8.dp)) {
                    TextButton(onClick = onUncheckAll) { Text("Uncheck all") }
                    TextButton(onClick = onDeleteAll) { Text("Delete all checked") }
                }
            }
        }
    }
    if (expanded) {
        items(items, key = { it.id }) { item ->
            SwipeableRow(
                item,
                onToggle = { onToggle(item) },
                onEdit = { onEdit(item) },
                onDelete = { onDelete(item) },
            )
        }
    }
}
