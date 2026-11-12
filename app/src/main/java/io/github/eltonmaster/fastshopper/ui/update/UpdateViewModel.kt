package io.github.eltonmaster.fastshopper.ui.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.eltonmaster.fastshopper.BuildConfig
import io.github.eltonmaster.fastshopper.data.update.AvailableUpdate
import io.github.eltonmaster.fastshopper.data.update.UpdateChecker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface UpdateState {
    data object Hidden : UpdateState
    data class Available(val update: AvailableUpdate) : UpdateState
    data class Downloading(val update: AvailableUpdate, val progress: Float?) : UpdateState
    data class ReadyToInstall(val update: AvailableUpdate, val apk: File) : UpdateState
    data class Failed(val update: AvailableUpdate, val message: String) : UpdateState
}

/** Checks GitHub Releases once per app start and drives the update dialog. */
class UpdateViewModel(
    private val checker: UpdateChecker,
    private val downloadDir: File,
    checkOnStart: Boolean,
) : ViewModel() {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Hidden)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private var downloadJob: Job? = null

    init {
        if (checkOnStart) check()
    }

    private fun check() {
        viewModelScope.launch {
            // Leftovers from an earlier update are no longer needed once it is installed.
            withContext(Dispatchers.IO) { downloadDir.deleteRecursively() }
            // A failed check stays silent: no network in the shop shouldn't nag.
            checker.check().getOrNull()?.let { _state.value = UpdateState.Available(it) }
        }
    }

    fun download() {
        val update = currentUpdate() ?: return
        downloadJob?.cancel()
        _state.value = UpdateState.Downloading(update, progress = null)
        downloadJob = viewModelScope.launch {
            try {
                val apk = checker.download(update, downloadDir) { p ->
                    _state.value = UpdateState.Downloading(update, p)
                }
                _state.value = UpdateState.ReadyToInstall(update, apk)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = UpdateState.Failed(update, e.message ?: "Download failed")
            }
        }
    }

    fun installFailed(message: String) {
        val update = currentUpdate() ?: return
        _state.value = UpdateState.Failed(update, message)
    }

    fun dismiss() {
        downloadJob?.cancel()
        downloadJob = null
        _state.value = UpdateState.Hidden
    }

    private fun currentUpdate(): AvailableUpdate? = when (val s = _state.value) {
        UpdateState.Hidden -> null
        is UpdateState.Available -> s.update
        is UpdateState.Downloading -> s.update
        is UpdateState.ReadyToInstall -> s.update
        is UpdateState.Failed -> s.update
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                UpdateViewModel(
                    checker = UpdateChecker(),
                    downloadDir = File(app.cacheDir, "updates"),
                    // Debug builds use a different signing key, so a release APK can't install over them.
                    checkOnStart = !BuildConfig.DEBUG,
                )
            }
        }
    }
}
