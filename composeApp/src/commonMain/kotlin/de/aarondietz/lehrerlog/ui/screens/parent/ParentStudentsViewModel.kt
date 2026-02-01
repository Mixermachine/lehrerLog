package de.aarondietz.lehrerlog.ui.screens.parent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.aarondietz.lehrerlog.data.StudentDto
import de.aarondietz.lehrerlog.data.repository.ParentRepository
import de.aarondietz.lehrerlog.data.repository.ParentSelectionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ParentStudentsUiState(
    val students: List<StudentDto> = emptyList(),
    val selectedStudentId: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class ParentStudentsViewModel(
    private val parentRepository: ParentRepository,
    private val selectionRepository: ParentSelectionRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ParentStudentsUiState())
    val state: StateFlow<ParentStudentsUiState> = _state.asStateFlow()

    init {
        observeSelection()
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            val result = parentRepository.refreshStudents()
            if (result.isSuccess) {
                val students = result.getOrNull().orEmpty()
                val selectedId = selectionRepository.selectedStudentId().value
                if (selectedId == null && students.isNotEmpty()) {
                    selectionRepository.setSelectedStudentId(students.first().id)
                }
                _state.update {
                    it.copy(
                        students = students,
                        isLoading = false,
                        errorMessage = null,
                        selectedStudentId = selectionRepository.selectedStudentId().value
                    )
                }
            } else {
                _state.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "Network error"
                    )
                }
            }
        }
    }

    fun selectStudent(studentId: String) {
        selectionRepository.setSelectedStudentId(studentId)
        _state.update { it.copy(selectedStudentId = studentId) }
    }

    private fun observeSelection() {
        viewModelScope.launch {
            selectionRepository.selectedStudentId().collect { selectedId ->
                _state.update { it.copy(selectedStudentId = selectedId) }
            }
        }
    }
}
