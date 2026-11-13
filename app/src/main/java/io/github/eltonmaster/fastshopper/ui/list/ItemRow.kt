package io.github.eltonmaster.fastshopper.ui.list

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.eltonmaster.fastshopper.data.QuantityStep
import io.github.eltonmaster.fastshopper.data.RemoteChanges
import kotlinx.coroutines.delay

/** How long the +/− buttons stay after the last tap. */
private const val STEPPER_HIDE_MS = 4_000L

/**
 * Which row currently shows its +/− buttons. Tapping an item's amount opens them for that item
 * only; they hide again a few seconds after the last tap, so a stray tap in the shop can't change
 * an amount by accident.
 */
@Stable
class StepperState {
    var itemId by mutableStateOf<String?>(null)
        private set
    var tick by mutableIntStateOf(0)
        private set

    fun show(id: String) {
        itemId = id
        tick++
    }

    /** Restarts the hide timer. */
    fun touch() {
        tick++
    }

    fun hide() {
        itemId = null
    }
}

@Composable
fun rememberStepperState(): StepperState {
    val state = remember { StepperState() }
    LaunchedEffect(state.itemId, state.tick) {
        if (state.itemId != null) {
            delay(STEPPER_HIDE_MS)
            state.hide()
        }
    }
    return state
}

/**
 * Swipe left deletes. [onStep] is null for rows that get no stepper (checked items).
 * [dragHandle] is set while reordering: the row then shows a drag handle and ignores taps and swipes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableRow(
    item: RowItem,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    stepper: StepperState? = null,
    onStep: ((Int) -> Unit)? = null,
    dragHandle: Modifier? = null,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = dragHandle == null,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) {
        ItemRow(item, onToggle, onEdit, stepper, onStep, dragHandle)
    }
}

/** Tap checks the item off, long press opens it for editing, tapping the amount shows +/−. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ItemRow(
    item: RowItem,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    stepper: StepperState? = null,
    onStep: ((Int) -> Unit)? = null,
    dragHandle: Modifier? = null,
) {
    val haptics = LocalHapticFeedback.current
    val reordering = dragHandle != null
    val canStep = !item.checked && !reordering && stepper != null && onStep != null
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .combinedClickable(
                    enabled = !reordering,
                    onClick = onToggle,
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onEdit()
                    },
                    onLongClickLabel = "Edit",
                )
                .padding(start = 4.dp, end = if (canStep || reordering) 4.dp else 16.dp),
        ) {
            Checkbox(
                checked = item.checked,
                onCheckedChange = if (reordering) null else { _ -> onToggle() },
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.title,
                        style = MaterialTheme.typography.bodyLarge,
                        textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                        color = if (item.checked) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    item.remoteChange?.let { RemoteChangeBadge(it) }
                }
                item.subtitle?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            when {
                dragHandle != null -> {
                    if (item.amount.isNotEmpty()) {
                        Spacer(Modifier.width(12.dp))
                        Text(
                            item.amount,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = dragHandle.heightIn(min = 48.dp).widthIn(min = 48.dp),
                    ) {
                        Icon(
                            Icons.Filled.DragHandle,
                            contentDescription = "Drag to reorder",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                canStep && stepper != null && onStep != null && stepper.itemId == item.id -> QuantityStepper(
                    item = item,
                    onStep = { delta ->
                        stepper.touch()
                        onStep(delta)
                    },
                )

                canStep && stepper != null -> AmountButton(item.amount, onClick = { stepper.show(item.id) })

                item.amount.isNotEmpty() -> {
                    Spacer(Modifier.width(12.dp))
                    Text(
                        item.amount,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * The amount as a tap target. Items without a shown amount (a single piece) show a faint "1" so
 * there's something to tap.
 */
@Composable
private fun AmountButton(amount: String, onClick: () -> Unit) {
    Spacer(Modifier.width(4.dp))
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .widthIn(min = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClickLabel = "Change amount", role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
    ) {
        Text(
            amount.ifEmpty { "1" },
            style = MaterialTheme.typography.bodyMedium,
            color = if (amount.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun QuantityStepper(item: RowItem, onStep: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onStep(-1) }, enabled = QuantityStep.canDecrease(item.quantity)) {
            Icon(Icons.Filled.Remove, contentDescription = "Decrease amount")
        }
        Text(
            item.amount.ifEmpty { "1" },
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 24.dp),
        )
        IconButton(onClick = { onStep(1) }) {
            Icon(Icons.Filled.Add, contentDescription = "Increase amount")
        }
    }
}

/** Small "new" or "changed" pill next to an item someone else added or changed. */
@Composable
private fun RemoteChangeBadge(change: String) {
    val isNew = change == RemoteChanges.NEW
    Surface(
        color = if (isNew) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = if (isNew) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onTertiaryContainer,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.padding(start = 8.dp),
    ) {
        Text(
            if (isNew) "new" else "changed",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
