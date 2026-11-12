package io.github.eltonmaster.fastshopper.ui.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.eltonmaster.fastshopper.ui.appViewModelFactory

/** Opens the sheet to edit an existing item. [session] makes every opening start fresh. */
@Composable
fun EditItemSheet(
    listId: String,
    itemId: String,
    session: Int,
    onDismiss: () -> Unit,
    onMessage: (String) -> Unit,
) {
    AddItemSheet(
        listId = listId,
        listName = "",
        onDismiss = onDismiss,
        onMessage = onMessage,
        viewModel = viewModel(
            key = "edit-$itemId-$session",
            factory = appViewModelFactory { AddItemViewModel(listId, it.repository, editItemId = itemId) },
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddItemSheet(
    listId: String,
    listName: String,
    onDismiss: () -> Unit,
    onMessage: (String) -> Unit,
    viewModel: AddItemViewModel = viewModel(
        key = "add-$listId",
        factory = appViewModelFactory { AddItemViewModel(listId, it.repository) },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val nameFocus = remember { FocusRequester() }
    var labelMenuOpen by remember { mutableStateOf(false) }

    // Adding starts typing right away; editing usually changes something other than the name.
    LaunchedEffect(Unit) { if (!viewModel.isEdit) nameFocus.requestFocus() }

    if (state.missing) {
        LaunchedEffect(Unit) {
            onMessage("This item was deleted meanwhile.")
            onDismiss()
        }
    }

    val submit = {
        if (viewModel.isEdit) {
            viewModel.save(onMessage, onDismiss)
        } else {
            viewModel.add(onMessage)
            nameFocus.requestFocus()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                when {
                    viewModel.isEdit -> "Edit item"
                    listName.isBlank() -> "Add item"
                    else -> "Add to $listName"
                },
                style = MaterialTheme.typography.titleLarge,
            )

            // Name with autocomplete
            Column {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChange,
                    label = { Text("Item") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(nameFocus),
                )
                if (state.suggestions.isNotEmpty() || state.showCreate) {
                    Surface(
                        tonalElevation = 2.dp,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    ) {
                        Column {
                            state.suggestions.forEachIndexed { index, s ->
                                if (index > 0) HorizontalDivider()
                                SuggestionRow(s.name, s.usualUnit) { viewModel.pickFood(s) }
                            }
                            if (state.showCreate) {
                                if (state.suggestions.isNotEmpty()) HorizontalDivider()
                                SuggestionRow("+ Create “${state.query.trim()}”", null) { submit() }
                            }
                        }
                    }
                }
            }

            // Number and container
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { viewModel.stepQuantity(-1) }) {
                        Icon(Icons.Filled.Remove, contentDescription = "Less")
                    }
                    OutlinedTextField(
                        value = state.quantityText,
                        onValueChange = viewModel::onQuantityChange,
                        label = { Text("Number") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                        modifier = Modifier.width(80.dp),
                    )
                    IconButton(onClick = { viewModel.stepQuantity(1) }) {
                        Icon(Icons.Filled.Add, contentDescription = "More")
                    }
                }
                OutlinedTextField(
                    value = state.unitText,
                    onValueChange = viewModel::onUnitTextChange,
                    label = { Text("Container") },
                    placeholder = { Text(state.unitPlaceholder ?: "e.g. Packung") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.weight(1f),
                )
            }
            if (state.unitSuggestions.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    state.unitSuggestions.forEach { u ->
                        AssistChip(onClick = { viewModel.pickUnit(u) }, label = { Text(u.name) })
                    }
                }
            }

            if (viewModel.isEdit) {
                OutlinedTextField(
                    value = state.note,
                    onValueChange = viewModel::onNoteChange,
                    label = { Text("Note") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Label
            Box {
                AssistChip(
                    onClick = { labelMenuOpen = true },
                    label = { Text("Label: " + (state.labelName ?: "none")) },
                    trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
                )
                DropdownMenu(expanded = labelMenuOpen, onDismissRequest = { labelMenuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("No label") },
                        onClick = { labelMenuOpen = false; viewModel.pickLabel(null) },
                    )
                    state.labels.forEach { label ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    label.name,
                                    fontWeight = if (label.id == state.labelId) FontWeight.Bold else FontWeight.Normal,
                                )
                            },
                            onClick = { labelMenuOpen = false; viewModel.pickLabel(label.id) },
                        )
                    }
                }
            }

            Button(
                onClick = { submit() },
                enabled = state.canAdd,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(if (viewModel.isEdit) "Save" else "Add")
            }
        }
    }
}

@Composable
private fun SuggestionRow(title: String, trailing: String?, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (trailing != null) {
            Text(trailing, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
