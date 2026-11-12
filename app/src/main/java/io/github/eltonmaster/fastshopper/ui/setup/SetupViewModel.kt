package io.github.eltonmaster.fastshopper.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.eltonmaster.fastshopper.data.MealieRepository
import io.github.eltonmaster.fastshopper.data.ServerUrl
import io.github.eltonmaster.fastshopper.data.SettingsStore
import io.github.eltonmaster.fastshopper.data.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SetupUiState(
    val serverUrl: String = "",
    val token: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val connectedAs: String? = null,
)

class SetupViewModel(
    private val repository: MealieRepository,
    private val settingsStore: SettingsStore,
) : ViewModel() {
    private val _state = MutableStateFlow(SetupUiState())
    val state: StateFlow<SetupUiState> = _state.asStateFlow()

    fun onServerUrlChange(value: String) = _state.update { it.copy(serverUrl = value, error = null) }
    fun onTokenChange(value: String) = _state.update { it.copy(token = value.trim(), error = null) }

    fun connect() {
        val current = _state.value
        val url = ServerUrl.normalize(current.serverUrl)
        when {
            url == null -> { _state.update { it.copy(error = "Enter a valid server URL.") }; return }
            current.token.isBlank() -> { _state.update { it.copy(error = "Paste your Mealie API token.") }; return }
        }
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            repository.verify(url!!, current.token)
                .onSuccess { user ->
                    settingsStore.saveLogin(url, current.token, user.displayName)
                    _state.update { it.copy(busy = false, connectedAs = user.displayName, serverUrl = url) }
                }
                .onFailure { e ->
                    _state.update { it.copy(busy = false, error = e.toUserMessage()) }
                }
        }
    }
}
