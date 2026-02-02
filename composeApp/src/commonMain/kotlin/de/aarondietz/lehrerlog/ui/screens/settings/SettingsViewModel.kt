package de.aarondietz.lehrerlog.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.aarondietz.lehrerlog.data.StorageQuotaDto
import de.aarondietz.lehrerlog.data.repository.StorageRepository
import de.aarondietz.lehrerlog.logging.LogOverview
import de.aarondietz.lehrerlog.logging.LogRepository
import de.aarondietz.lehrerlog.logging.logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class StorageQuotaState(
    val quota: StorageQuotaDto? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

data class LogUiState(
    val overview: LogOverview? = null,
    val isLoading: Boolean = false,
    val isSharing: Boolean = false,
    val error: String? = null
)

class SettingsViewModel(
    private val storageRepository: StorageRepository,
    private val logRepository: LogRepository
) : ViewModel() {
    private val logger by lazy { logger() }
    private val _quotaState = MutableStateFlow(StorageQuotaState())
    val quotaState: StateFlow<StorageQuotaState> = _quotaState.asStateFlow()

    private val _logState = MutableStateFlow(LogUiState())
    val logState: StateFlow<LogUiState> = _logState.asStateFlow()

    init {
        loadQuota()
        loadLogs()
    }

    fun loadQuota() {
        viewModelScope.launch {
            _quotaState.value = StorageQuotaState(isLoading = true)
            storageRepository.getQuota()
                .onSuccess { quota ->
                    _quotaState.value = StorageQuotaState(quota = quota, isLoading = false)
                }
                .onFailure { e ->
                    logger.e(e) { "Failed to load storage quota" }
                    _quotaState.value = StorageQuotaState(
                        isLoading = false,
                        error = "Failed to load storage quota: ${e.message}"
                    )
                }
        }
    }

    fun loadLogs() {
        viewModelScope.launch {
            _logState.value = _logState.value.copy(isLoading = true, error = null)
            logRepository.loadOverview()
                .onSuccess { overview ->
                    _logState.value = _logState.value.copy(
                        overview = overview,
                        isLoading = false,
                        error = null
                    )
                }
                .onFailure { e ->
                    logger.e(e) { "Failed to load logs" }
                    _logState.value = _logState.value.copy(
                        isLoading = false,
                        error = "Failed to load logs: ${e.message}"
                    )
                }
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            _logState.value = _logState.value.copy(isLoading = true, error = null)
            logRepository.clearLogs()
                .onSuccess {
                    loadLogs()
                }
                .onFailure { e ->
                    logger.e(e) { "Failed to clear logs" }
                    _logState.value = _logState.value.copy(
                        isLoading = false,
                        error = "Failed to clear logs: ${e.message}"
                    )
                }
        }
    }

    fun shareLogs() {
        viewModelScope.launch {
            _logState.value = _logState.value.copy(isSharing = true, error = null)
            logRepository.buildSharePayload()
                .onSuccess { payload ->
                    de.aarondietz.lehrerlog.logging.shareLogs(payload)
                    _logState.value = _logState.value.copy(isSharing = false)
                }
                .onFailure { e ->
                    logger.e(e) { "Failed to share logs" }
                    _logState.value = _logState.value.copy(
                        isSharing = false,
                        error = "Failed to share logs: ${e.message}"
                    )
                }
        }
    }
}
