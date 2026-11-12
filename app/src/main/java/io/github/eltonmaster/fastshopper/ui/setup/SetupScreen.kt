package io.github.eltonmaster.fastshopper.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.eltonmaster.fastshopper.ui.appViewModelFactory

@Composable
fun SetupScreen(
    onConnected: () -> Unit,
    viewModel: SetupViewModel = viewModel(
        factory = appViewModelFactory { SetupViewModel(it.repository, it.settingsStore) }
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showToken by remember { mutableStateOf(false) }

    LaunchedEffect(state.connectedAs) {
        if (state.connectedAs != null) onConnected()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text("Connect to Mealie", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Enter your server address and an API token. Create a token in Mealie under " +
                "Profile > API Tokens.",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = state.serverUrl,
            onValueChange = viewModel::onServerUrlChange,
            label = { Text("Server URL") },
            placeholder = { Text("https://mealie.example.com") },
            singleLine = true,
            enabled = !state.busy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.token,
            onValueChange = viewModel::onTokenChange,
            label = { Text("API token") },
            singleLine = true,
            enabled = !state.busy,
            visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showToken = !showToken }) {
                    Icon(
                        if (showToken) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (showToken) "Hide token" else "Show token",
                    )
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { viewModel.connect() }),
            modifier = Modifier.fillMaxWidth(),
        )
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        Button(
            onClick = viewModel::connect,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            if (state.busy) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text("Connect")
            }
        }
    }
}
