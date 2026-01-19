package de.aarondietz.lehrerlog.ui.screens.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import de.aarondietz.lehrerlog.auth.AuthRepository
import de.aarondietz.lehrerlog.auth.AuthResult
import de.aarondietz.lehrerlog.data.SchoolClassDto
import de.aarondietz.lehrerlog.data.TaskDto
import de.aarondietz.lehrerlog.data.TaskSubmissionSummaryDto
import de.aarondietz.lehrerlog.data.repository.SchoolClassRepository
import de.aarondietz.lehrerlog.data.repository.TaskRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class TasksViewModel(
    private val taskRepository: TaskRepository,
    private val schoolClassRepository: SchoolClassRepository,
    private val authRepository: AuthRepository,
    private val logger: Logger
) : ViewModel() {
    private val _schoolId = MutableStateFlow<String?>(null)
    private val _selectedClassId = MutableStateFlow<String?>(null)
    private val _tasks = MutableStateFlow<List<TaskDto>>(emptyList())
    private val _summaries = MutableStateFlow<Map<String, TaskSubmissionSummaryDto>>(emptyMap())
    private val _isLoading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)

    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    val error: StateFlow<String?> = _error.asStateFlow()
    val tasks: StateFlow<List<TaskDto>> = _tasks.asStateFlow()
    val summaries: StateFlow<Map<String, TaskSubmissionSummaryDto>> = _summaries.asStateFlow()
    val selectedClassId: StateFlow<String?> = _selectedClassId.asStateFlow()

    val classes: StateFlow<List<SchoolClassDto>> = _schoolId
        .filterNotNull()
        .flatMapLatest { id -> schoolClassRepository.getClassesFlow(id) }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadCurrentUser()
    }

    private fun loadCurrentUser() {
        viewModelScope.launch {
            _isLoading.value = true
            when (val result = authRepository.getCurrentUser()) {
                is AuthResult.Success -> {
                    _schoolId.value = result.data.schoolId
                    if (result.data.schoolId != null) {
                        refreshClasses()
                    } else {
                        logger.e { "User is not associated with a school." }
                        _error.value = "User is not associated with a school."
                    }
                }
                is AuthResult.Error -> {
                    logger.e { "Failed to load user: ${result.message}" }
                    _error.value = "Failed to load user: ${result.message}"
                }
            }
            _isLoading.value = false
        }
    }

    fun selectClass(classId: String) {
        if (_selectedClassId.value == classId) return
        _selectedClassId.value = classId
        loadTasks()
    }

    fun refreshClasses() {
        val schoolId = _schoolId.value ?: return
        viewModelScope.launch {
            schoolClassRepository.refreshClasses(schoolId)
                .onFailure { e ->
                    logger.e(e) { "Failed to load classes" }
                    _error.value = "Failed to load classes: ${e.message}"
                }
        }
    }

    fun loadTasks() {
        val classId = _selectedClassId.value ?: return
        viewModelScope.launch {
            _isLoading.value = true
            taskRepository.getTasks(classId)
                .onSuccess { tasks ->
                    _tasks.value = tasks
                    loadSummaries(tasks)
                }
                .onFailure { e ->
                    logger.e(e) { "Failed to load tasks" }
                    _error.value = "Failed to load tasks: ${e.message}"
                }
            _isLoading.value = false
        }
    }

    fun createTask(
        classId: String,
        title: String,
        description: String?,
        dueAt: String
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            taskRepository.createTask(classId, title, description, dueAt)
                .onSuccess {
                    loadTasks()
                }
                .onFailure { e ->
                    logger.e(e) { "Failed to create task" }
                    _error.value = "Failed to create task: ${e.message}"
                }
            _isLoading.value = false
        }
    }

    private suspend fun loadSummaries(tasks: List<TaskDto>) {
        val summaries = mutableMapOf<String, TaskSubmissionSummaryDto>()
        tasks.forEach { task ->
            taskRepository.getSubmissionSummary(task.id)
                .onSuccess { summary -> summaries[task.id] = summary }
        }
        _summaries.value = summaries
    }

    fun clearError() {
        _error.value = null
    }
}
