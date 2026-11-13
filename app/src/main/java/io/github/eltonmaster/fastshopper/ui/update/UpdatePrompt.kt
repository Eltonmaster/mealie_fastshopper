package io.github.eltonmaster.fastshopper.ui.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.eltonmaster.fastshopper.BuildConfig
import io.github.eltonmaster.fastshopper.data.update.AvailableUpdate
import java.io.File

/** Shows a dialog when a newer release is on GitHub; downloads and opens the installer on request. */
@Composable
fun UpdatePrompt(viewModel: UpdateViewModel = viewModel(factory = UpdateViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val currentState by rememberUpdatedState(state)

    fun openInstaller(apk: File) {
        runCatching { launchInstaller(context, apk) }
            .onFailure { viewModel.installFailed("Couldn't open the installer: ${it.message}") }
    }

    // Android asks once per app for "install unknown apps"; come back here and carry on.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        val ready = currentState as? UpdateState.ReadyToInstall ?: return@rememberLauncherForActivityResult
        if (context.packageManager.canRequestPackageInstalls()) openInstaller(ready.apk)
    }

    fun install(apk: File) {
        if (context.packageManager.canRequestPackageInstalls()) {
            openInstaller(apk)
        } else {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            )
            try {
                permissionLauncher.launch(intent)
            } catch (e: ActivityNotFoundException) {
                viewModel.installFailed("Allow FastShopper to install apps in the system settings, then try again.")
            }
        }
    }

    // Start the install as soon as the download is done; the dialog keeps an Install button
    // in case the system prompt is backed out of.
    val readyApk = (state as? UpdateState.ReadyToInstall)?.apk
    LaunchedEffect(readyApk) {
        if (readyApk != null) install(readyApk)
    }

    when (val s = state) {
        UpdateState.Hidden -> Unit
        is UpdateState.Available -> UpdateDialog(
            update = s.update,
            onDismiss = viewModel::dismiss,
            confirmLabel = "Update",
            onConfirm = { viewModel.download() },
            dismissLabel = "Later",
        )
        is UpdateState.Downloading -> UpdateDialog(
            update = s.update,
            onDismiss = {}, // don't lose a running download to a stray tap outside
            confirmLabel = null,
            onConfirm = {},
            dismissLabel = "Cancel",
            onDismissButton = viewModel::dismiss,
        ) {
            Text(
                if (s.progress == null) "Downloading…" else "Downloading… ${(s.progress * 100).toInt()} %",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (s.progress == null) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
            }
        }
        is UpdateState.ReadyToInstall -> UpdateDialog(
            update = s.update,
            onDismiss = viewModel::dismiss,
            confirmLabel = "Install",
            onConfirm = { install(s.apk) },
            dismissLabel = "Later",
        ) {
            Text("Download complete.", style = MaterialTheme.typography.bodyMedium)
        }
        is UpdateState.Failed -> UpdateDialog(
            update = s.update,
            onDismiss = viewModel::dismiss,
            confirmLabel = "Retry",
            onConfirm = { viewModel.download() },
            dismissLabel = "Close",
        ) {
            Text(
                s.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun UpdateDialog(
    update: AvailableUpdate,
    onDismiss: () -> Unit,
    confirmLabel: String?,
    onConfirm: () -> Unit,
    dismissLabel: String,
    onDismissButton: () -> Unit = onDismiss,
    extra: @Composable () -> Unit = {},
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.SystemUpdate, contentDescription = null) },
        title = { Text("Update available") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "FastShopper ${update.version} is available. You have ${BuildConfig.VERSION_NAME}." +
                        (if (update.sizeBytes > 0) " Download size: ${formatSize(update.sizeBytes)}." else ""),
                )
                if (update.highlights.isNotEmpty()) {
                    Text("What's new", style = MaterialTheme.typography.titleSmall)
                    update.highlights.forEach { line ->
                        Text("• $line", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                extra()
            }
        },
        confirmButton = {
            if (confirmLabel != null) TextButton(onClick = onConfirm) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismissButton) { Text(dismissLabel) }
        },
    )
}

internal fun formatSize(bytes: Long): String =
    if (bytes >= 1_000_000) "%.1f MB".format(bytes / 1_000_000.0) else "${(bytes + 999) / 1000} KB"

private fun launchInstaller(context: Context, apk: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, "application/vnd.android.package-archive")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}
