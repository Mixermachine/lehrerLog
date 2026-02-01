package de.aarondietz.lehrerlog.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import de.aarondietz.lehrerlog.data.StorageQuotaDto
import de.aarondietz.lehrerlog.data.repository.StorageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class StorageQuotaState(
    val quota: StorageQuotaDto? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

class SettingsViewModel(
    private val storageRepository: StorageRepository,
    private val logger: Logger
) : ViewModel() {
    private val _quotaState = MutableStateFlow(StorageQuotaState())
    val quotaState: StateFlow<StorageQuotaState> = _quotaState.asStateFlow()

    init {
        loadQuota()
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
}