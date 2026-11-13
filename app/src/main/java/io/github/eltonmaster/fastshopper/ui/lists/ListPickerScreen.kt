package io.github.eltonmaster.fastshopper.ui.lists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
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
                        "No shopping lists yet. Create one in Mealie.",
                        Modifier.align(Alignment.Center).padding(24.dp),
                    )

                else -> LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        Text(
                            "Tap the star to open a list automatically on launch.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    items(state.lists, key = { it.id }) { list ->
                        val isDefault = list.id == state.defaultListId
                        val newCount = state.newCounts[list.id] ?: 0
                        ListItem(
                            headlineContent = { Text(list.name ?: "Unnamed list") },
                            supportingContent = if (isDefault || newCount > 0) {
                                { ListSupportingText(isDefault, newCount) }
                            } else null,
                            trailingContent = {
                                IconButton(onClick = { viewModel.toggleDefault(list.id) }) {
                                    Icon(
                                        if (isDefault) Icons.Filled.Star else Icons.Filled.StarBorder,
                                        contentDescription = if (isDefault) "Unset default list" else "Set as default list",
                                    )
                                }
                            },
                            modifier = Modifier.clickable { onOpenList(list.id) },
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
