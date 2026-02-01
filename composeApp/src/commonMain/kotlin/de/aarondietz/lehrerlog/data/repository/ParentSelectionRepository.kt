package de.aarondietz.lehrerlog.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class ParentSelectionRepository {
    private val selectedStudentIdState = MutableStateFlow<String?>(null)

    fun selectedStudentId(): StateFlow<String?> = selectedStudentIdState

    fun setSelectedStudentId(studentId: String?) {
        selectedStudentIdState.value = studentId
    }
}
