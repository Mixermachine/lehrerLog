package de.aarondietz.lehrerlog.ui.screens.parent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.aarondietz.lehrerlog.data.StudentDto
import de.aarondietz.lehrerlog.data.TaskDto
import de.aarondietz.lehrerlog.data.repository.ParentRepository
import de.aarondietz.lehrerlog.data.repository.ParentSelectionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ParentAssignmentsUiState(
    val students: List<StudentDto> = emptyList(),
    val selectedStudentId: String? = null,
    val assignments: List<TaskDto> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class ParentAssignmentsViewModel(
    private val parentRepository: ParentRepository,
    private val selectionRepository: ParentSelectionRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ParentAssignmentsUiState())
    val state: StateFlow<ParentAssignmentsUiState> = _state.asStateFlow()

    init {
        observeStudents()
        observeSelection()
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val studentsResult = parentRepository.refreshStudents()
            if (studentsResult.isSuccess) {
                val students = studentsResult.getOrNull().orEmpty()
                if (selectionRepository.selectedStudentId().value == null && students.isNotEmpty()) {
                    selectionRepository.setSelectedStudentId(students.first().id)
                }
            }
        }
    }

    private fun observeStudents() {
        viewModelScope.launch {
            parentRepository.studentsFlow().collect { students ->
                _state.update { it.copy(students = students) }
            }
        }
    }

    private fun observeSelection() {
        viewModelScope.launch {
            selectionRepository.selectedStudentId().collect { selectedId ->
                _state.update { it.copy(selectedStudentId = selectedId, errorMessage = null) }
                if (selectedId == null) {
                    _state.update { it.copy(assignments = emptyList(), isLoading = false) }
                } else {
                    loadAssignments(selectedId)
                }
            }
        }
    }

    private fun loadAssignments(studentId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            val result = parentRepository.listAssignments(studentId)
            _state.update {
                if (result.isSuccess) {
                    it.copy(
                        assignments = result.getOrNull().orEmpty(),
                        isLoading = false,
                        errorMessage = null
                    )
                } else {
                    it.copy(
                        assignments = emptyList(),
                        isLoading = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "Network error"
                    )
                }
            }
        }
    }
}
