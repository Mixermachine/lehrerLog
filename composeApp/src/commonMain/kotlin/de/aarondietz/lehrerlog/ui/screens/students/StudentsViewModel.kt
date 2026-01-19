package de.aarondietz.lehrerlog.ui.screens.students

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import de.aarondietz.lehrerlog.auth.AuthRepository
import de.aarondietz.lehrerlog.auth.AuthResult
import de.aarondietz.lehrerlog.data.SchoolClassDto
import de.aarondietz.lehrerlog.data.StudentDto
import de.aarondietz.lehrerlog.data.repository.SchoolClassRepository
import de.aarondietz.lehrerlog.data.repository.StudentRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for Students screen with server integration.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StudentsViewModel(
    private val studentRepository: StudentRepository,
    private val schoolClassRepository: SchoolClassRepository,
    private val authRepository: AuthRepository,
    private val logger: Logger
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _schoolId = MutableStateFlow<String?>(null)
    private val schoolId: StateFlow<String?> = _schoolId.asStateFlow()

    // Classes with their students
    val classes: StateFlow<List<SchoolClassDto>> = schoolId
        .filterNotNull()
        .flatMapLatest { id ->
            schoolClassRepository.getClassesFlow(id)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val students: StateFlow<List<StudentDto>> = schoolId
        .filterNotNull()
        .flatMapLatest { id ->
            studentRepository.getStudentsFlow(id)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        loadCurrentUser()
    }

    private fun loadCurrentUser() {
        viewModelScope.launch {
            _isLoading.value = true
            logger.d { "Loading current user" }
            when (val result = authRepository.getCurrentUser()) {
                is AuthResult.Success -> {
                    logger.d { "User loaded: id=${result.data.id}, email=${result.data.email}, schoolId=${result.data.schoolId}" }
                    val userSchoolId = result.data.schoolId
                    if (userSchoolId != null) {
                        logger.i { "Setting schoolId to: $userSchoolId" }
                        _schoolId.value = userSchoolId
                        loadData()
                    } else {
                        logger.w { "User does not have a schoolId assigned" }
                        _error.value = "User is not associated with a school. Please contact your administrator."
                        _isLoading.value = false
                    }
                }
                is AuthResult.Error -> {
                    logger.e { "Failed to get user information: ${result.message}" }
                    _error.value = "Failed to get user information: ${result.message}"
                    _isLoading.value = false
                }
            }
        }
    }

    fun loadData() {
        val currentSchoolId = _schoolId.value ?: return

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            // Load classes
            schoolClassRepository.refreshClasses(currentSchoolId)
                .onFailure { e ->
                    logger.e(e) { "Failed to load classes" }
                    _error.value = "Failed to load classes: ${e.message}"
                }

            // Load students
            studentRepository.refreshStudents(currentSchoolId)
                .onFailure { e ->
                    logger.e(e) { "Failed to load students" }
                    _error.value = "Failed to load students: ${e.message}"
                }

            _isLoading.value = false
        }
    }

    fun createClass(name: String, alternativeName: String? = null) {
        logger.i { "createClass called: name=$name, alternativeName=$alternativeName" }
        val currentSchoolId = _schoolId.value
        if (currentSchoolId == null) {
            logger.w { "Cannot create class: schoolId is null" }
            _error.value = "Cannot create class without a school. Please contact your administrator."
            return
        }

        logger.d { "Creating class for schoolId=$currentSchoolId" }

        viewModelScope.launch {
            val result = schoolClassRepository.createClass(currentSchoolId, name, alternativeName)
            result
                .onSuccess {
                    logger.i { "Class created successfully: ${it.id}" }
                }
                .onFailure { e ->
                    logger.e(e) { "Failed to create class: ${e.message}" }
                    _error.value = "Failed to create class: ${e.message}"
                }
        }
    }

    fun deleteClass(classId: String) {
        viewModelScope.launch {
            schoolClassRepository.deleteClass(classId)
                .onFailure { e ->
                    logger.e(e) { "Failed to delete class" }
                    _error.value = "Failed to delete class: ${e.message}"
                }
        }
    }

    fun createStudent(classId: String, firstName: String, lastName: String) {
        val currentSchoolId = _schoolId.value ?: return

        viewModelScope.launch {
            studentRepository.createStudent(currentSchoolId, classId, firstName, lastName)
                .onFailure { e ->
                    logger.e(e) { "Failed to create student" }
                    _error.value = "Failed to create student: ${e.message}"
                }
        }
    }

    fun deleteStudent(studentId: String) {
        viewModelScope.launch {
            studentRepository.deleteStudent(studentId)
                .onFailure { e ->
                    logger.e(e) { "Failed to delete student" }
                    _error.value = "Failed to delete student: ${e.message}"
                }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
