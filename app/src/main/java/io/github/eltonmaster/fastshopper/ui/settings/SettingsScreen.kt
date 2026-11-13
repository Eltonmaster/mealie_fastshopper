package io.github.eltonmaster.fastshopper.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.eltonmaster.fastshopper.BuildConfig
import io.github.eltonmaster.fastshopper.data.AutoClearDelay
import io.github.eltonmaster.fastshopper.data.ThemeMode
import io.github.eltonmaster.fastshopper.data.update.AvailableUpdate
import io.github.eltonmaster.fastshopper.data.update.UpdateChecker
import io.github.eltonmaster.fastshopper.notify.ChangeNotifier
import io.github.eltonmaster.fastshopper.ui.appViewModelFactory
import io.github.eltonmaster.fastshopper.ui.notify.openAppNotificationSettings
import io.github.eltonmaster.fastshopper.ui.notify.rememberNotificationPermissionRequest
import io.github.eltonmaster.fastshopper.ui.update.UpdateCheck
import io.github.eltonmaster.fastshopper.ui.update.UpdateState
import io.github.eltonmaster.fastshopper.ui.update.UpdateViewModel
import io.github.eltonmaster.fastshopper.ui.update.formatSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    updateViewModel: UpdateViewModel,
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(
        factory = appViewModelFactory { SettingsViewModel(it.settingsStore) }
    ),
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val autoClearDelay by viewModel.autoClearDelay.collectAsStateWithLifecycle()
    val notifyChanges by viewModel.notifyChanges.collectAsStateWithLifecycle()
    val check by updateViewModel.check.collectAsStateWithLifecycle()
    val updateState by updateViewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionHeader("Appearance")
            ThemeChoice(selected = themeMode, onSelect = viewModel::setThemeMode)

            HorizontalDivider(Modifier.padding(top = 8.dp))

            SectionHeader("Remove checked items after")
            AutoClearChoice(selected = autoClearDelay, onSelect = viewModel::setAutoClearDelay)

            HorizontalDivider(Modifier.padding(top = 8.dp))

            SectionHeader("Notifications")
            ChangeNotificationSetting(enabled = notifyChanges, onChange = viewModel::setNotifyChanges)

            HorizontalDivider(Modifier.padding(top = 8.dp))

            SectionHeader("About")
            AboutSection(
                check = check,
                downloading = updateState is UpdateState.Downloading,
                onCheck = updateViewModel::checkNow,
                onInstall = { update -> updateViewModel.download(update) },
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}

/**
 * Switch for background change notifications. Turning it on asks for the notification
 * permission on Android 13+; if notifications are blocked for the app, says so with a way to fix it.
 */
@Composable
private fun ChangeNotificationSetting(enabled: Boolean?, onChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(ChangeNotifier.canPost(context)) }
    var denied by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        allowed = ChangeNotifier.canPost(context)
        onPauseOrDispose {}
    }
    val requestPermission = rememberNotificationPermissionRequest { granted ->
        allowed = ChangeNotifier.canPost(context)
        denied = !granted
        if (granted) onChange(true)
    }
    val on = enabled == true
    ListItem(
        headlineContent = { Text("Notify me about list changes") },
        supportingContent = {
            Text(
                "When someone else adds or changes items on a list you've opened on this phone. " +
                    "Checked about every 15 minutes while the app is closed."
            )
        },
        trailingContent = {
            Switch(checked = on, onCheckedChange = null, enabled = enabled != null)
        },
        modifier = Modifier.toggleable(
            value = on,
            enabled = enabled != null,
            role = Role.Switch,
            onValueChange = { turnOn -> if (turnOn) requestPermission() else onChange(false) },
        ),
    )
    if ((on || denied) && !allowed) {
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Notifications are blocked for FastShopper. Allow them in the system settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            TextButton(onClick = { openAppNotificationSettings(context) }) { Text("Open notification settings") }
        }
    }
}

private val ThemeMode.label: String
    get() = when (this) {
        ThemeMode.LIGHT -> "Light"
        ThemeMode.DARK -> "Dark"
        ThemeMode.SYSTEM -> "Auto"
    }

private val ThemeMode.description: String?
    get() = if (this == ThemeMode.SYSTEM) "Follow the system's dark mode setting" else null

/** Light, Dark, Auto in the order people usually read them; nothing is ticked until loaded. */
@Composable
private fun ThemeChoice(selected: ThemeMode?, onSelect: (ThemeMode) -> Unit) {
    Column(Modifier.selectableGroup()) {
        listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.SYSTEM).forEach { mode ->
            val description = mode.description
            ListItem(
                headlineContent = { Text(mode.label) },
                supportingContent = if (description != null) {
                    { Text(description) }
                } else null,
                leadingContent = { RadioButton(selected = mode == selected, onClick = null) },
                modifier = Modifier.selectable(
                    selected = mode == selected,
                    onClick = { onSelect(mode) },
                    role = Role.RadioButton,
                ),
            )
        }
    }
}

/** The delay options from shortest to Never; nothing is ticked until loaded. */
@Composable
private fun AutoClearChoice(selected: AutoClearDelay?, onSelect: (AutoClearDelay) -> Unit) {
    Column(Modifier.selectableGroup()) {
        AutoClearDelay.entries.forEach { delay ->
            ListItem(
                headlineContent = { Text(delay.label) },
                supportingContent = if (delay == AutoClearDelay.DEFAULT) {
                    { Text("Default") }
                } else null,
                leadingContent = { RadioButton(selected = delay == selected, onClick = null) },
                modifier = Modifier.selectable(
                    selected = delay == selected,
                    onClick = { onSelect(delay) },
                    role = Role.RadioButton,
                ),
            )
        }
    }
    // Each phone deletes on the server, so the phone with the shortest delay clears first.
    Text(
        "Deleted for everyone. If several phones use FastShopper, the shortest delay wins.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun AboutSection(
    check: UpdateCheck,
    downloading: Boolean,
    onCheck: () -> Unit,
    onInstall: (AvailableUpdate) -> Unit,
) {
    val uriHandler = LocalUriHandler.current

    ListItem(
        leadingContent = { Icon(Icons.Filled.Info, contentDescription = null) },
        headlineContent = { Text("Version") },
        supportingContent = {
            Text(
                if (BuildConfig.DEBUG) {
                    "FastShopper ${BuildConfig.VERSION_NAME} (debug build). Releases are signed " +
                        "with a different key, so uninstall this build before installing one."
                } else {
                    "FastShopper ${BuildConfig.VERSION_NAME}"
                },
            )
        },
    )

    UpdateRow(check = check, downloading = downloading, onCheck = onCheck)

    if (check is UpdateCheck.Found) {
        UpdateDetails(update = check.update, downloading = downloading, onInstall = onInstall)
    }

    ListItem(
        leadingContent = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
        headlineContent = { Text("All releases") },
        supportingContent = { Text("Release notes and downloads on GitHub") },
        modifier = Modifier.clickable { uriHandler.openUri(UpdateChecker.RELEASES_PAGE_URL) },
    )
}

@Composable
private fun UpdateRow(check: UpdateCheck, downloading: Boolean, onCheck: () -> Unit) {
    val (headline, supporting) = when (check) {
        UpdateCheck.Idle -> "Check for updates" to "Look for a new release on GitHub"
        UpdateCheck.Checking -> "Checking for updates…" to null
        UpdateCheck.UpToDate -> "You're up to date" to "Tap to check again"
        is UpdateCheck.Found -> "Update available" to "Tap to check again"
        is UpdateCheck.Failed -> "Couldn't check for updates" to check.message
    }
    val canCheck = check != UpdateCheck.Checking && !downloading
    ListItem(
        leadingContent = { Icon(Icons.Filled.SystemUpdate, contentDescription = null) },
        headlineContent = { Text(headline) },
        supportingContent = if (supporting != null) {
            {
                Text(
                    supporting,
                    color = if (check is UpdateCheck.Failed) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        } else null,
        trailingContent = if (check == UpdateCheck.Checking) {
            { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) }
        } else null,
        modifier = Modifier.clickable(enabled = canCheck, onClick = onCheck),
    )
}

@Composable
private fun UpdateDetails(update: AvailableUpdate, downloading: Boolean, onInstall: (AvailableUpdate) -> Unit) {
    Column(
        Modifier.padding(start = 56.dp, end = 16.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "FastShopper ${update.version} is available. You have ${BuildConfig.VERSION_NAME}." +
                (if (update.sizeBytes > 0) " Download size: ${formatSize(update.sizeBytes)}." else ""),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (update.highlights.isNotEmpty()) {
            Text("What's new", style = MaterialTheme.typography.titleSmall)
            update.highlights.forEach { line ->
                Text("• $line", style = MaterialTheme.typography.bodyMedium)
            }
        }
        Button(onClick = { onInstall(update) }, enabled = !downloading) {
            Text(if (downloading) "Downloading…" else "Download and install")
        }
    }
}
