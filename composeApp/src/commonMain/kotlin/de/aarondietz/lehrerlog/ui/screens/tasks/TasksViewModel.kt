package de.aarondietz.lehrerlog.ui.screens.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.aarondietz.lehrerlog.auth.AuthRepository
import de.aarondietz.lehrerlog.auth.AuthResult
import de.aarondietz.lehrerlog.data.*
import de.aarondietz.lehrerlog.data.repository.*
import de.aarondietz.lehrerlog.logging.logger
import de.aarondietz.lehrerlog.ui.util.PickedFile
import de.aarondietz.lehrerlog.ui.util.getErrorResource
import de.aarondietz.lehrerlog.ui.util.toStringResource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import lehrerlog.composeapp.generated.resources.*
import org.jetbrains.compose.resources.StringResource

data class TaskDetailState(
    val task: TaskDto? = null,
    val students: List<StudentDto> = emptyList(),
    val submissions: List<TaskSubmissionDto> = emptyList(),
    val assignmentFile: FileMetadataDto? = null,
    val isLoading: Boolean = false,
    val isUploading: Boolean = false,
    val errorResource: StringResource? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class TasksViewModel(
    private val taskRepository: TaskRepository,
    private val schoolClassRepository: SchoolClassRepository,
    private val studentRepository: StudentRepository,
    private val authRepository: AuthRepository
) : ViewModel() {
    private val logger by lazy { logger() }
    private val _schoolId = MutableStateFlow<String?>(null)
    private val _selectedClassId = MutableStateFlow<String?>(null)
    private val _tasks = MutableStateFlow<List<TaskDto>>(emptyList())
    private val _summaries = MutableStateFlow<Map<String, TaskSubmissionSummaryDto>>(emptyMap())
    private val _isLoading = MutableStateFlow(false)
    private val _error = MutableStateFlow<StringResource?>(null)
    private val _detailState = MutableStateFlow(TaskDetailState())

    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    val error: StateFlow<StringResource?> = _error.asStateFlow()
    val tasks: StateFlow<List<TaskDto>> = _tasks.asStateFlow()
    val summaries: StateFlow<Map<String, TaskSubmissionSummaryDto>> = _summaries.asStateFlow()
    val selectedClassId: StateFlow<String?> = _selectedClassId.asStateFlow()
    val detailState: StateFlow<TaskDetailState> = _detailState.asStateFlow()

    val classes: StateFlow<List<SchoolClassDto>> = _schoolId
        .filterNotNull()
        .flatMapLatest { _ -> schoolClassRepository.getClassesFlow() }
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
                        _error.value = Res.string.error_auth_session_expired
                    }
                }

                is AuthResult.Error -> {
                    logger.e { "Failed to load user: ${result.message}" }
                    _error.value = result.toStringResource()
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
                    _error.value = e.toStringResource()
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
                    _error.value = e.toStringResource()
                }
            _isLoading.value = false
        }
    }

    fun createTask(
        classId: String,
        title: String,
        description: String?,
        dueAt: String,
        file: PickedFile? = null
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            taskRepository.createTask(classId, title, description, dueAt)
                .onSuccess { createdTask ->
                    // Upload file if provided
                    if (file != null) {
                        uploadAssignmentFile(createdTask.id, file)
                    }
                    loadTasks()
                }
                .onFailure { e ->
                    logger.e(e) { "Failed to create task" }
                    _error.value = e.toStringResource()
                }
            _isLoading.value = false
        }
    }

    fun updateTask(
        taskId: String,
        title: String,
        description: String?,
        dueAt: String
    ) {
        viewModelScope.launch {
            _detailState.value = _detailState.value.copy(isLoading = true, errorResource = null)
            taskRepository.updateTask(taskId, title, description, dueAt)
                .onSuccess { updatedTask ->
                    _detailState.value = _detailState.value.copy(task = updatedTask, isLoading = false)
                    loadTasks()
                }
                .onFailure { e ->
                    logger.e(e) { "Failed to update task" }
                    _detailState.value = _detailState.value.copy(
                        isLoading = false,
                        errorResource = e.toStringResource()
                    )
                }
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            _detailState.value = _detailState.value.copy(isLoading = true, errorResource = null)
            taskRepository.deleteTask(taskId)
                .onSuccess {
                    closeTask()
                    loadTasks()
                }
                .onFailure { e ->
                    logger.e(e) { "Failed to delete task" }
                    _detailState.value = _detailState.value.copy(
                        isLoading = false,
                        errorResource = e.toStringResource()
                    )
                }
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

    fun openTask(task: TaskDto) {
        _detailState.value = TaskDetailState(task = task, isLoading = true)
        viewModelScope.launch {
            loadTaskDetails(task)
        }
    }

    fun closeTask() {
        _detailState.value = TaskDetailState()
    }

    fun refreshTaskDetails() {
        val task = _detailState.value.task ?: return
        _detailState.value = _detailState.value.copy(isLoading = true, isUploading = false)
        viewModelScope.launch {
            loadTaskDetails(task)
        }
    }

    fun markInPersonSubmission(taskId: String, studentId: String) {
        viewModelScope.launch {
            taskRepository.createSubmission(
                taskId,
                de.aarondietz.lehrerlog.data.CreateTaskSubmissionRequest(
                    studentId = studentId,
                    submissionType = TaskSubmissionType.IN_PERSON
                )
            ).onSuccess {
                refreshTaskDetails()
            }.onFailure { e ->
                logger.e(e) { "Failed to create in-person submission" }
                _detailState.value = _detailState.value.copy(errorResource = e.toStringResource())
            }
        }
    }

    fun updateSubmission(submissionId: String, grade: Double?, note: String?) {
        viewModelScope.launch {
            taskRepository.updateSubmission(submissionId, grade, note)
                .onSuccess {
                    refreshTaskDetails()
                }
                .onFailure { e ->
                    logger.e(e) { "Failed to update submission" }
                    _detailState.value = _detailState.value.copy(errorResource = e.toStringResource())
                }
        }
    }

    fun uploadAssignmentFile(taskId: String, file: PickedFile) {
        _detailState.value = _detailState.value.copy(isUploading = true, errorResource = null)
        viewModelScope.launch {
            when (val result = taskRepository.uploadTaskFile(taskId, file.toPayload())) {
                is FileUploadResult.Success -> refreshTaskDetails()
                is FileUploadResult.FileTooLarge -> showUploadError(result.toStringResource())
                is FileUploadResult.QuotaExceeded -> showUploadError(result.toStringResource())
                is FileUploadResult.Error -> showUploadError(result.toStringResource())
            }
        }
    }

    fun uploadSubmissionFile(taskId: String, studentId: String, submissionId: String?, file: PickedFile) {
        _detailState.value = _detailState.value.copy(isUploading = true, errorResource = null)
        viewModelScope.launch {
            val resolvedSubmissionId = submissionId ?: run {
                val created = taskRepository.createSubmission(
                    taskId,
                    de.aarondietz.lehrerlog.data.CreateTaskSubmissionRequest(
                        studentId = studentId,
                        submissionType = TaskSubmissionType.FILE
                    )
                ).getOrElse { e ->
                    showUploadError(e.toStringResource())
                    return@launch
                }
                created.id
            }

            when (val result = taskRepository.uploadSubmissionFile(taskId, resolvedSubmissionId, file.toPayload())) {
                is FileUploadResult.Success -> refreshTaskDetails()
                is FileUploadResult.FileTooLarge -> showUploadError(result.toStringResource())
                is FileUploadResult.QuotaExceeded -> showUploadError(result.toStringResource())
                is FileUploadResult.Error -> showUploadError(result.toStringResource())
            }
        }
    }

    private fun showUploadError(errorResource: StringResource) {
        _detailState.value = _detailState.value.copy(isUploading = false, errorResource = errorResource)
    }

    private suspend fun loadTaskDetails(task: TaskDto) {
        val schoolId = _schoolId.value
        if (schoolId == null) {
            _detailState.value = _detailState.value.copy(
                isLoading = false,
                errorResource = Res.string.error_auth_session_expired
            )
            return
        }

        val studentsResult = studentRepository.refreshStudents(schoolId)
        val submissionsResult = taskRepository.getSubmissions(task.id)
        val fileResult = taskRepository.getTaskFile(task.id)

        val students = studentsResult.getOrElse { emptyList() }
            .filter { it.classIds.contains(task.schoolClassId) }
        val submissions = submissionsResult.getOrElse { emptyList() }
        val assignmentFile = fileResult.getOrNull()

        // Only treat students/submissions errors as critical, file errors are optional
        _detailState.value = TaskDetailState(
            task = task,
            students = students,
            submissions = submissions,
            assignmentFile = assignmentFile,
            isLoading = false,
            isUploading = false,
            errorResource = studentsResult.getErrorResource()
                ?: submissionsResult.getErrorResource()
        )
    }
}

private fun PickedFile.toPayload(): UploadFilePayload {
    return UploadFilePayload(
        fileName = name,
        bytes = bytes,
        contentType = contentType
    )
}
