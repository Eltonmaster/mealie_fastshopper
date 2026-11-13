package io.github.eltonmaster.fastshopper.ui.lists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.eltonmaster.fastshopper.data.ListEdit
import io.github.eltonmaster.fastshopper.data.api.ShoppingListSummary
import io.github.eltonmaster.fastshopper.ui.appViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListPickerScreen(
    onOpenList: (String) -> Unit,
    onSignedOut: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: ListPickerViewModel = viewModel(
        factory = appViewModelFactory { ListPickerViewModel(it.repository, it.settingsStore) }
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Shopping lists") },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    onOpenSettings()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Sign out") },
                                onClick = {
                                    menuOpen = false
                                    viewModel.signOut(onSignedOut)
                                },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (!(state.loading && state.lists.isEmpty())) {
                ExtendedFloatingActionButton(
                    onClick = viewModel::startCreate,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("New list") },
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading && state.lists.isEmpty() ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))

                state.error != null && state.lists.isEmpty() ->
                    Column(
                        Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = viewModel::refresh) { Text("Try again") }
                    }

                state.lists.isEmpty() ->
                    Text(
                        "No shopping lists yet. Tap New list to create one.",
                        Modifier.align(Alignment.Center).padding(24.dp),
                    )

                // Bottom padding keeps the last row and the sign-in note clear of the button.
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
                    item {
                        Text(
                            "Tap the star to open a list automatically on launch.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    items(state.lists, key = { it.id }) { list ->
                        ListRow(
                            list = list,
                            isDefault = list.id == state.defaultListId,
                            newCount = state.newCounts[list.id] ?: 0,
                            onOpen = { onOpenList(list.id) },
                            onToggleDefault = { viewModel.toggleDefault(list.id) },
                            onRename = { viewModel.startRename(list) },
                            onDelete = { viewModel.startDelete(list) },
                        )
                        HorizontalDivider()
                    }
                    state.userName?.let { name ->
                        item {
                            Text(
                                "Signed in as $name" + (state.serverUrl?.let { " on $it" } ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    when (val dialog = state.dialog) {
        null -> Unit
        ListDialog.Create -> ListNameDialog(
            title = "New list",
            confirmLabel = "Create",
            initial = "",
            working = state.working,
            error = state.dialogError,
            onConfirm = viewModel::confirmName,
            onDismiss = viewModel::dismissDialog,
        )
        is ListDialog.Rename -> ListNameDialog(
            title = "Rename list",
            confirmLabel = "Rename",
            initial = dialog.list.name.orEmpty(),
            working = state.working,
            error = state.dialogError,
            onConfirm = viewModel::confirmName,
            onDismiss = viewModel::dismissDialog,
        )
        is ListDialog.Delete -> DeleteListDialog(
            dialog = dialog,
            working = state.working,
            error = state.dialogError,
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::dismissDialog,
        )
    }
}

@Composable
private fun ListRow(
    list: ShoppingListSummary,
    isDefault: Boolean,
    newCount: Int,
    onOpen: () -> Unit,
    onToggleDefault: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(list.name ?: "Unnamed list") },
        supportingContent = if (isDefault || newCount > 0) {
            { ListSupportingText(isDefault, newCount) }
        } else null,
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onToggleDefault) {
                    Icon(
                        if (isDefault) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = if (isDefault) "Unset default list" else "Set as default list",
                    )
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "List options")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onRename()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onDelete()
                            },
                        )
                    }
                }
            }
        },
        modifier = Modifier.clickable(onClick = onOpen),
    )
}

@Composable
private fun ListNameDialog(
    title: String,
    confirmLabel: String,
    initial: String,
    working: Boolean,
    error: String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length)))
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val canConfirm = !working && ListEdit.cleanName(name.text) != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    enabled = !working,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { if (canConfirm) onConfirm(name.text) }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.text) }, enabled = canConfirm) {
                if (working) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !working) { Text("Cancel") }
        },
    )
}

@Composable
private fun DeleteListDialog(
    dialog: ListDialog.Delete,
    working: Boolean,
    error: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Delete, contentDescription = null) },
        title = { Text("Delete list?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (dialog.counting) "Counting items…"
                    else ListEdit.deletePrompt(dialog.list.name ?: "Unnamed list", dialog.itemCount)
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !working && !dialog.counting,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                if (working) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !working) { Text("Cancel") }
        },
    )
}

/** "Default list", "3 new", or both. */
@Composable
private fun ListSupportingText(isDefault: Boolean, newCount: Int) {
    Row {
        if (isDefault) Text("Default list")
        if (isDefault && newCount > 0) Text(" · ")
        if (newCount > 0) {
            Text(
                "$newCount new",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
