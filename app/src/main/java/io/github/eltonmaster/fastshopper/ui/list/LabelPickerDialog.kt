package io.github.eltonmaster.fastshopper.ui.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.eltonmaster.fastshopper.data.LabelPick
import io.github.eltonmaster.fastshopper.data.db.LabelEntity

/**
 * Searchable, scrollable label list with "No label" and an option to create a new label.
 * Replaces a dropdown that ran off the bottom of the screen with many labels.
 */
@Composable
fun LabelPickerDialog(
    labels: List<LabelEntity>,
    selectedId: String?,
    creating: Boolean,
    error: String?,
    onPick: (String?) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val matches = remember(labels, query) { LabelPick.filter(labels, query) }
    val canCreate = LabelPick.canCreate(labels, query)
    val typed = query.trim()

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
            Column(
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Label",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search or create a label") },
                    singleLine = true,
                    enabled = !creating,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = {
                        when {
                            canCreate -> onCreate(typed)
                            matches.size == 1 -> onPick(matches.first().id)
                        }
                    }),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                )
                if (error != null) {
                    Text(
                        error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp).weight(1f, fill = false)) {
                    if (canCreate) {
                        item(key = "create") {
                            PickerRow(
                                text = "Create “$typed”",
                                selected = false,
                                enabled = !creating,
                                leading = {
                                    if (creating) {
                                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Filled.Add, contentDescription = null)
                                    }
                                },
                                onClick = { onCreate(typed) },
                            )
                            HorizontalDivider()
                        }
                    }
                    if (typed.isEmpty()) {
                        item(key = "none") {
                            PickerRow(
                                text = "No label",
                                selected = selectedId == null,
                                enabled = !creating,
                                leading = { Box(Modifier.size(20.dp)) },
                                onClick = { onPick(null) },
                            )
                        }
                    }
                    items(matches, key = { it.id }) { label ->
                        PickerRow(
                            text = label.name,
                            selected = label.id == selectedId,
                            enabled = !creating,
                            leading = { ColorDot(label.color) },
                            onClick = { onPick(label.id) },
                        )
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End).padding(horizontal = 16.dp),
                ) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun PickerRow(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    leading: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp),
    ) {
        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) { leading() }
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        if (selected) Icon(Icons.Filled.Check, contentDescription = "Selected")
    }
}

@Composable
private fun ColorDot(hex: String?) {
    val color = remember(hex) { parseColor(hex) } ?: MaterialTheme.colorScheme.outlineVariant
    Box(Modifier.size(12.dp).background(color, CircleShape))
}

/** Mealie stores label colors as "#RRGGBB" (sometimes "#RRGGBBAA"). */
private fun parseColor(hex: String?): Color? {
    val digits = hex?.trim()?.removePrefix("#") ?: return null
    val value = digits.toLongOrNull(16) ?: return null
    return when (digits.length) {
        6 -> Color(0xFF000000 or value)
        8 -> Color(((value and 0xFF) shl 24) or (value ushr 8))
        else -> null
    }
}
