package de.aarondietz.lehrerlog.ui.screens.parent_management

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import de.aarondietz.lehrerlog.data.ParentInviteCreateResponse
import de.aarondietz.lehrerlog.data.ParentLinkDto
import de.aarondietz.lehrerlog.data.repository.ParentInviteRepository
import de.aarondietz.lehrerlog.data.repository.ParentLinksRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ParentInviteManagementState(
    val inviteResponse: ParentInviteCreateResponse? = null,
    val parentLinks: List<ParentLinkDto> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class ParentInviteManagementViewModel(
    private val parentInviteRepository: ParentInviteRepository,
    private val parentLinksRepository: ParentLinksRepository,
    private val logger: Logger
) : ViewModel() {
    private val _state = MutableStateFlow(ParentInviteManagementState())
    val state: StateFlow<ParentInviteManagementState> = _state.asStateFlow()

    fun generateInvite(studentId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            parentInviteRepository.createInvite(studentId)
                .onSuccess { response ->
                    _state.value = _state.value.copy(
                        inviteResponse = response,
                        isLoading = false
                    )
                    loadParentLinks(studentId)
                }
                .onFailure { e ->
                    logger.e(e) { "Failed to generate invite" }
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = "Failed to generate invite: ${e.message}"
                    )
                }
        }
    }

    fun loadParentLinks(studentId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            parentLinksRepository.listLinks(studentId)
                .onSuccess { links ->
                    _state.value = _state.value.copy(
                        parentLinks = links,
                        isLoading = false
                    )
                }
                .onFailure { e ->
                    logger.e(e) { "Failed to load parent links" }
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = "Failed to load parent links: ${e.message}"
                    )
                }
        }
    }

    fun revokeLink(linkId: String, studentId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            parentLinksRepository.revokeLink(linkId)
                .onSuccess {
                    loadParentLinks(studentId)
                }
                .onFailure { e ->
                    logger.e(e) { "Failed to revoke link" }
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = "Failed to revoke link: ${e.message}"
                    )
                }
        }
    }

    fun clearInvite() {
        _state.value = _state.value.copy(inviteResponse = null)
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}
