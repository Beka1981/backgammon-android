package ge.gogichaishvili.backgammon.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ge.gogichaishvili.backgammon.domain.settings.*
import ge.gogichaishvili.backgammon.domain.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.IOException

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val loaded: Boolean = false,
    val readFailed: Boolean = false,
    val saveFailed: Boolean = false,
) {
    val theme: BoardTheme get() = ThemeRegistry.resolve(settings.themeId)
}

class SettingsViewModel(
    private val repository: SettingsRepository,
    private val themes: ThemeRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = mutableState.asStateFlow()
    val availableThemes = themes.themes
    // Serialize user actions; rapid taps must not overwrite unrelated settings.
    private val writes = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private var readJob: Job? = null

    init {
        retryRead()
        viewModelScope.launch {
            for (write in writes) {
                try {
                    write()
                    mutableState.update { it.copy(saveFailed = false) }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: IOException) {
                    mutableState.update { it.copy(saveFailed = true) }
                }
            }
        }
    }

    fun retryRead() {
        readJob?.cancel()
        mutableState.update { it.copy(readFailed = false) }
        readJob = viewModelScope.launch {
            try {
                repository.settings.collect { settings ->
                    mutableState.update { it.copy(settings = settings, loaded = true, readFailed = false) }
                }
            } catch (_: IOException) {
                mutableState.update { it.copy(readFailed = true) }
            }
        }
    }

    fun selectTheme(id: String) {
        if (availableThemes.any { it.id == id }) writes.trySend { themes.select(id) }
    }
    fun update(transform: (AppSettings) -> AppSettings) {
        writes.trySend { repository.update(transform) }
    }
    fun dismissSaveError() = mutableState.update { it.copy(saveFailed = false) }
    override fun onCleared() {
        writes.close()
        super.onCleared()
    }
}
