package io.github.eltonmaster.fastshopper.ui.notify

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.eltonmaster.fastshopper.data.SettingsStore
import io.github.eltonmaster.fastshopper.notify.ChangeNotifier
import io.github.eltonmaster.fastshopper.ui.appViewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Returns an action that turns change notifications on: on Android 13+ it first asks for the
 * notification permission, and [onResult] gets whether it was granted (always true before 13).
 */
@Composable
fun rememberNotificationPermissionRequest(onResult: (granted: Boolean) -> Unit): () -> Unit {
    val context = LocalContext.current
    val currentOnResult by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        currentOnResult(granted)
    }
    return remember(context, launcher) {
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                currentOnResult(true)
            }
        }
    }
}

/** The system's notification settings for this app, to unblock notifications there. */
fun openAppNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

/**
 * Tells the notifier which list is on screen (it gets no notification while you look at it) and
 * removes that list's notification when it comes into view.
 */
@Composable
fun TrackVisibleList(listId: String) {
    val context = LocalContext.current.applicationContext
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, listId) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> ChangeNotifier.onListShown(context, listId)
                Lifecycle.Event.ON_PAUSE -> ChangeNotifier.onListHidden(listId)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            ChangeNotifier.onListHidden(listId)
        }
    }
}

class NotificationHintViewModel(private val settingsStore: SettingsStore) : ViewModel() {
    /** Whether the hint may show: notifications are off and the hint wasn't answered yet. */
    val canShow: StateFlow<Boolean> =
        combine(settingsStore.notifyChanges, settingsStore.notifyHintDone) { on, done -> !on && !done }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun answer(turnOn: Boolean) {
        viewModelScope.launch {
            if (turnOn) settingsStore.setNotifyChanges(true)
            settingsStore.setNotifyHintDone()
        }
    }
}

/**
 * Offered once, the first time a "new" badge appears and notifications are still off: turn on
 * notifications for changes, or dismiss it for good. Settings has the same switch.
 */
@Composable
fun NotificationHint(
    hasChanges: Boolean,
    viewModel: NotificationHintViewModel = viewModel(
        factory = appViewModelFactory { NotificationHintViewModel(it.settingsStore) }
    ),
) {
    val canShow by viewModel.canShow.collectAsStateWithLifecycle()
    val requestPermission = rememberNotificationPermissionRequest { granted -> viewModel.answer(turnOn = granted) }
    if (!hasChanges || !canShow) return
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp)) {
            Text(
                "Someone changed this list. Get a notification next time, even when the app is closed?",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(end = 8.dp),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { viewModel.answer(turnOn = false) }) { Text("No thanks") }
                TextButton(onClick = requestPermission) { Text("Turn on") }
            }
        }
    }
}
