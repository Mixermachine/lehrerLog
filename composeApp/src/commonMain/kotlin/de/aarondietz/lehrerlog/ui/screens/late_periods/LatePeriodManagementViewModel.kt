package de.aarondietz.lehrerlog.ui.screens.late_periods

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import de.aarondietz.lehrerlog.data.LatePeriodDto
import de.aarondietz.lehrerlog.data.repository.LatePeriodRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LatePeriodManagementState(
    val periods: List<LatePeriodDto> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class LatePeriodManagementViewModel(
    private val latePeriodRepository: LatePeriodRepository,
    private val logger: Logger
) : ViewModel() {
    private val _state = MutableStateFlow(LatePeriodManagementState())
    val state: StateFlow<LatePeriodManagementState> = _state.asStateFlow()

    init {
        loadPeriods()
    }

    fun loadPeriods() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            latePeriodRepository.listPeriods()
                .onSuccess { periods ->
                    _state.value = _state.value.copy(
                        periods = periods,
                        isLoading = false
                    )
                }
                .onFailure { e ->
                    logger.e(e) { "Failed to load periods" }
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = "Failed to load periods: ${e.message}"
                    )
                }
        }
    }

    fun createPeriod(name: String, startsAt: String, endsAt: String?) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            latePeriodRepository.createPeriod(name, startsAt, endsAt)
                .onSuccess {
                    loadPeriods()
                }
                .onFailure { e ->
                    logger.e(e) { "Failed to create period" }
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = "Failed to create period: ${e.message}"
                    )
                }
        }
    }

    fun updatePeriod(periodId: String, name: String?, startsAt: String?, endsAt: String?) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            latePeriodRepository.updatePeriod(periodId, name, startsAt, endsAt)
                .onSuccess {
                    loadPeriods()
                }
                .onFailure { e ->
                    logger.e(e) { "Failed to update period" }
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = "Failed to update period: ${e.message}"
                    )
                }
        }
    }

    fun activatePeriod(periodId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            latePeriodRepository.activatePeriod(periodId)
                .onSuccess {
                    loadPeriods()
                }
                .onFailure { e ->
                    logger.e(e) { "Failed to activate period" }
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = "Failed to activate period: ${e.message}"
                    )
                }
        }
    }

    fun recalculatePeriod(periodId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            latePeriodRepository.recalculatePeriod(periodId)
                .onSuccess {
                    _state.value = _state.value.copy(isLoading = false)
                }
                .onFailure { e ->
                    logger.e(e) { "Failed to recalculate period" }
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = "Failed to recalculate period: ${e.message}"
                    )
                }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}
