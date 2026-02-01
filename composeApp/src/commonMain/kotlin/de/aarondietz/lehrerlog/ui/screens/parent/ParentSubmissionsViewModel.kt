package de.aarondietz.lehrerlog.ui.screens.parent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.aarondietz.lehrerlog.data.StudentDto
import de.aarondietz.lehrerlog.data.TaskSubmissionDto
import de.aarondietz.lehrerlog.data.repository.ParentRepository
import de.aarondietz.lehrerlog.data.repository.ParentSelectionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ParentSubmissionsUiState(
    val students: List<StudentDto> = emptyList(),
    val selectedStudentId: String? = null,
    val submissions: List<TaskSubmissionDto> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class ParentSubmissionsViewModel(
    private val parentRepository: ParentRepository,
    private val selectionRepository: ParentSelectionRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ParentSubmissionsUiState())
    val state: StateFlow<ParentSubmissionsUiState> = _state.asStateFlow()

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
                    _state.update { it.copy(submissions = emptyList(), isLoading = false) }
                } else {
                    loadSubmissions(selectedId)
                }
            }
        }
    }

    private fun loadSubmissions(studentId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            val result = parentRepository.listSubmissions(studentId)
            _state.update {
                if (result.isSuccess) {
                    it.copy(
                        submissions = result.getOrNull().orEmpty(),
                        isLoading = false,
                        errorMessage = null
                    )
                } else {
                    it.copy(
                        submissions = emptyList(),
                        isLoading = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "Network error"
                    )
                }
            }
        }
    }
}
