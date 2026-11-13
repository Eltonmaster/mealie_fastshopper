package io.github.eltonmaster.fastshopper.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.eltonmaster.fastshopper.data.AutoClearDelay
import io.github.eltonmaster.fastshopper.data.SettingsStore
import io.github.eltonmaster.fastshopper.data.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val settingsStore: SettingsStore) : ViewModel() {
    val themeMode: StateFlow<ThemeMode?> = settingsStore.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsStore.setThemeMode(mode) }
    }

    val autoClearDelay: StateFlow<AutoClearDelay?> = settingsStore.autoClearDelay
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setAutoClearDelay(delay: AutoClearDelay) {
        viewModelScope.launch { settingsStore.setAutoClearDelay(delay) }
    }
}
