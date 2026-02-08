package de.aarondietz.lehrerlog.ui.screens.tasks

import androidx.lifecycle.viewModelScope
import de.aarondietz.lehrerlog.*
import de.aarondietz.lehrerlog.auth.AuthRepository
import de.aarondietz.lehrerlog.data.repository.SchoolClassRepository
import de.aarondietz.lehrerlog.data.repository.StudentRepository
import de.aarondietz.lehrerlog.data.repository.TaskRepository
import de.aarondietz.lehrerlog.ui.util.PickedFile
import io.ktor.http.*
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.serialization.json.Json
import lehrerlog.composeapp.generated.resources.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TasksViewModelTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun loadsTasksAndOpensDetails() = runViewModelTest {
        val tokenStorage = InMemoryTokenStorage().apply {
            saveAccessToken(SharedTestFixtures.testAuthAccessToken)
        }
        val schoolClass = SharedTestFixtures.testSchoolClassDto()
        val task = SharedTestFixtures.testTaskDto(schoolClass.id)
        val summary = SharedTestFixtures.testTaskSubmissionSummaryDto(task.id)
        val student = SharedTestFixtures.testStudentDto(schoolClass.id)
        val submission = SharedTestFixtures.testSubmissionDto(task.id, student.id)
        val user = SharedTestFixtures.testUserDto(SharedTestFixtures.testSchoolId)

        val httpClient = createTestHttpClient(expectSuccess = true) { request ->
            when (request.url.encodedPath) {
                "/auth/me" -> respondJson(json.encodeToString(user))
                "/api/classes" -> respondJson(json.encodeToString(listOf(schoolClass)))
                "/api/tasks" -> respondJson(json.encodeToString(listOf(task)))
                "/api/tasks/${task.id}/summary" -> respondJson(json.encodeToString(summary))
                "/api/tasks/${task.id}/targets" -> respondJson(
                    json.encodeToString(
                        de.aarondietz.lehrerlog.data.TaskTargetsResponse(
                            taskId = task.id,
                            studentIds = listOf(student.id)
                        )
                    )
                )
                "/api/tasks/${task.id}/submissions" -> {
                    if (request.method == HttpMethod.Post) {
                        respondJson(json.encodeToString(submission))
                    } else {
                        respondJson(json.encodeToString(listOf(submission)))
                    }
                }

                "/api/students" -> respondJson(json.encodeToString(listOf(student)))
                "/api/tasks/${task.id}/file" -> respondJson(
                    json.encodeToString(mapOf("error" to "Not found")),
                    status = HttpStatusCode.NotFound
                )
                else -> respondJson(json.encodeToString(emptyList<String>()))
            }
        }

        val taskRepository = TaskRepository(httpClient, tokenStorage, SharedTestFixtures.testBaseUrl)
        val classRepository = SchoolClassRepository(httpClient, tokenStorage, SharedTestFixtures.testBaseUrl)
        val studentRepository = StudentRepository(httpClient, tokenStorage, SharedTestFixtures.testBaseUrl)
        val authRepository = AuthRepository(httpClient, tokenStorage)

        val classesResult = classRepository.refreshClasses(SharedTestFixtures.testSchoolId)
        assertTrue(classesResult.isSuccess, "Classes failed: ${classesResult.exceptionOrNull()}")
        val tasksResult = taskRepository.getTasks(schoolClass.id)
        assertTrue(tasksResult.isSuccess, "Tasks failed: ${tasksResult.exceptionOrNull()}")
        val summaryResult = taskRepository.getSubmissionSummary(task.id)
        assertTrue(summaryResult.isSuccess, "Summary failed: ${summaryResult.exceptionOrNull()}")
        val studentsResult = studentRepository.refreshStudents(SharedTestFixtures.testSchoolId)
        assertTrue(studentsResult.isSuccess, "Students failed: ${studentsResult.exceptionOrNull()}")

        val viewModel = TasksViewModel(
            taskRepository = taskRepository,
            schoolClassRepository = classRepository,
            studentRepository = studentRepository,
            authRepository = authRepository
        )

        try {
            viewModel.selectClass(schoolClass.id)
            awaitUntil {
                !viewModel.isLoading.value &&
                    viewModel.tasks.value.isNotEmpty() &&
                    viewModel.summaries.value.isNotEmpty()
            }
            assertTrue(viewModel.tasks.value.isNotEmpty())
            assertTrue(viewModel.summaries.value.isNotEmpty())

            viewModel.openTask(task)
            awaitUntil { viewModel.detailState.value.task != null }
            awaitUntil { viewModel.detailState.value.students.isNotEmpty() }

            val detailState = viewModel.detailState.value
            assertNotNull(detailState.task)
            assertTrue(detailState.students.isNotEmpty())

            viewModel.markInPersonSubmission(task.id, student.id)
            awaitUntil { viewModel.detailState.value.errorResource == null }
            assertTrue(viewModel.detailState.value.errorResource == null)
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    @Test
    fun uploadsAssignmentAndSubmissionFiles() = runViewModelTest {
        val tokenStorage = InMemoryTokenStorage().apply {
            saveAccessToken(SharedTestFixtures.testAuthAccessToken)
        }
        val schoolClass = SharedTestFixtures.testSchoolClassDto()
        val task = SharedTestFixtures.testTaskDto(schoolClass.id)
        val student = SharedTestFixtures.testStudentDto(schoolClass.id)
        val submission = SharedTestFixtures.testSubmissionDto(task.id, student.id)
        val fileMetadata = SharedTestFixtures.testFileMetadataDto()
        val user = SharedTestFixtures.testUserDto(SharedTestFixtures.testSchoolId)

        val httpClient = createTestHttpClient(expectSuccess = true) { request ->
            when (request.url.encodedPath) {
                "/auth/me" -> respondJson(json.encodeToString(user))
                "/api/classes" -> respondJson(json.encodeToString(listOf(schoolClass)))
                "/api/students" -> respondJson(json.encodeToString(listOf(student)))
                "/api/tasks/${task.id}/submissions" -> {
                    if (request.method == HttpMethod.Post) {
                        respondJson(json.encodeToString(submission))
                    } else {
                        respondJson(json.encodeToString(listOf(submission)))
                    }
                }
                "/api/tasks/${task.id}/targets" -> respondJson(
                    json.encodeToString(
                        de.aarondietz.lehrerlog.data.TaskTargetsResponse(
                            taskId = task.id,
                            studentIds = listOf(student.id)
                        )
                    )
                )

                "/api/tasks/${task.id}/files" -> respondJson(json.encodeToString(fileMetadata))
                "/api/tasks/${task.id}/submissions/${submission.id}/files" -> respondJson(
                    json.encodeToString(fileMetadata)
                )
                "/api/tasks/${task.id}/file" -> respondJson(json.encodeToString(fileMetadata))

                else -> respondJson(json.encodeToString(emptyList<String>()))
            }
        }

        val taskRepository = TaskRepository(httpClient, tokenStorage, SharedTestFixtures.testBaseUrl)
        val classRepository = SchoolClassRepository(httpClient, tokenStorage, SharedTestFixtures.testBaseUrl)
        val studentRepository = StudentRepository(httpClient, tokenStorage, SharedTestFixtures.testBaseUrl)
        val authRepository = AuthRepository(httpClient, tokenStorage)

        val viewModel = TasksViewModel(
            taskRepository = taskRepository,
            schoolClassRepository = classRepository,
            studentRepository = studentRepository,
            authRepository = authRepository
        )

        val pickedFile = PickedFile(
            name = SharedTestFixtures.testFileName,
            bytes = SharedTestFixtures.testFileBytes,
            contentType = SharedTestFixtures.testFileContentType
        )

        try {
            awaitUntil { !viewModel.isLoading.value }
            viewModel.openTask(task)
            awaitUntil { viewModel.detailState.value.task != null }
            awaitUntil { !viewModel.detailState.value.isLoading }

            viewModel.uploadAssignmentFile(task.id, pickedFile)
            awaitUntil(timeoutMs = 3_000) { !viewModel.detailState.value.isUploading }

            viewModel.uploadSubmissionFile(task.id, student.id, null, pickedFile)
            awaitUntil(timeoutMs = 3_000) { !viewModel.detailState.value.isUploading }
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    @Test
    fun createTaskWithFile_surfacesUploadFailureOnScreenErrorState() = runViewModelTest {
        val tokenStorage = InMemoryTokenStorage().apply {
            saveAccessToken(SharedTestFixtures.testAuthAccessToken)
        }
        val schoolClass = SharedTestFixtures.testSchoolClassDto()
        val createdTask = SharedTestFixtures.testTaskDto(schoolClass.id)
        val summary = SharedTestFixtures.testTaskSubmissionSummaryDto(createdTask.id)
        val user = SharedTestFixtures.testUserDto(SharedTestFixtures.testSchoolId)
        var taskCreated = false
        val requestedPaths = mutableListOf<String>()

        val httpClient = createTestHttpClient(expectSuccess = true) { request ->
            val path = request.url.encodedPath
            val normalizedPath = path.trimEnd('/')
            requestedPaths += "${request.method.value}:$normalizedPath"
            when {
                normalizedPath == "/auth/me" ->
                    respondJson(json.encodeToString(user))
                normalizedPath == "/api/classes" ->
                    respondJson(json.encodeToString(listOf(schoolClass)))
                normalizedPath == "/api/tasks" && request.method == HttpMethod.Post -> {
                    taskCreated = true
                    respondJson(json.encodeToString(createdTask), status = HttpStatusCode.Created)
                }
                normalizedPath == "/api/tasks" ->
                    respondJson(json.encodeToString(if (taskCreated) listOf(createdTask) else emptyList()))
                normalizedPath.endsWith("/summary") ->
                    respondJson(json.encodeToString(summary))
                normalizedPath.endsWith("/files") && request.method == HttpMethod.Post ->
                    respondJson(
                        json.encodeToString(mapOf("error" to "quota exceeded")),
                        status = HttpStatusCode.Conflict
                    )
                else ->
                    respondJson(json.encodeToString(emptyList<String>()))
            }
        }

        val taskRepository = TaskRepository(httpClient, tokenStorage, SharedTestFixtures.testBaseUrl)
        val classRepository = SchoolClassRepository(httpClient, tokenStorage, SharedTestFixtures.testBaseUrl)
        val studentRepository = StudentRepository(httpClient, tokenStorage, SharedTestFixtures.testBaseUrl)
        val authRepository = AuthRepository(httpClient, tokenStorage)

        val viewModel = TasksViewModel(
            taskRepository = taskRepository,
            schoolClassRepository = classRepository,
            studentRepository = studentRepository,
            authRepository = authRepository
        )

        val pickedFile = PickedFile(
            name = SharedTestFixtures.testFileName,
            bytes = SharedTestFixtures.testFileBytes,
            contentType = SharedTestFixtures.testFileContentType
        )

        try {
            advanceUntilIdle()
            viewModel.selectClass(schoolClass.id)
            advanceUntilIdle()

            viewModel.createTask(
                classId = schoolClass.id,
                title = createdTask.title,
                description = createdTask.description,
                dueAt = createdTask.dueAt,
                file = pickedFile
            )

            awaitUntil(timeoutMs = 3_000) { taskCreated }
            assertTrue(taskCreated, "Request log: $requestedPaths")
            awaitUntil(timeoutMs = 3_000) { viewModel.error.value != null }
            awaitUntil(timeoutMs = 3_000) { viewModel.tasks.value.any { it.id == createdTask.id } }
            assertTrue(requestedPaths.any { it.startsWith("POST:/api/tasks/") && it.endsWith("/files") })
            assertNotNull(viewModel.error.value)
            assertTrue(viewModel.tasks.value.any { it.id == createdTask.id })
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    @Test
    fun createTaskWithFile_uploadSuccess_refreshesTaskListWithoutError() = runViewModelTest {
        val tokenStorage = InMemoryTokenStorage().apply {
            saveAccessToken(SharedTestFixtures.testAuthAccessToken)
        }
        val schoolClass = SharedTestFixtures.testSchoolClassDto()
        val createdTask = SharedTestFixtures.testTaskDto(schoolClass.id)
        val summary = SharedTestFixtures.testTaskSubmissionSummaryDto(createdTask.id)
        val fileMetadata = SharedTestFixtures.testFileMetadataDto()
        val user = SharedTestFixtures.testUserDto(SharedTestFixtures.testSchoolId)
        var taskCreated = false
        var uploadCalled = false

        val httpClient = createTestHttpClient { request ->
            val normalizedPath = request.url.encodedPath.trimEnd('/')
            when {
                normalizedPath == "/auth/me" ->
                    respondJson(json.encodeToString(user))
                normalizedPath == "/api/classes" ->
                    respondJson(json.encodeToString(listOf(schoolClass)))
                normalizedPath == "/api/tasks" && request.method == HttpMethod.Post -> {
                    taskCreated = true
                    respondJson(json.encodeToString(createdTask), status = HttpStatusCode.Created)
                }
                normalizedPath == "/api/tasks" ->
                    respondJson(json.encodeToString(if (taskCreated) listOf(createdTask) else emptyList()))
                normalizedPath.endsWith("/summary") ->
                    respondJson(json.encodeToString(summary))
                normalizedPath.endsWith("/files") && request.method == HttpMethod.Post -> {
                    uploadCalled = true
                    respondJson(json.encodeToString(fileMetadata), status = HttpStatusCode.Created)
                }
                else ->
                    respondJson(json.encodeToString(emptyList<String>()))
            }
        }

        val taskRepository = TaskRepository(httpClient, tokenStorage, SharedTestFixtures.testBaseUrl)
        val classRepository = SchoolClassRepository(httpClient, tokenStorage, SharedTestFixtures.testBaseUrl)
        val studentRepository = StudentRepository(httpClient, tokenStorage, SharedTestFixtures.testBaseUrl)
        val authRepository = AuthRepository(httpClient, tokenStorage)

        val viewModel = TasksViewModel(
            taskRepository = taskRepository,
            schoolClassRepository = classRepository,
            studentRepository = studentRepository,
            authRepository = authRepository
        )

        val pickedFile = PickedFile(
            name = SharedTestFixtures.testFileName,
            bytes = SharedTestFixtures.testFileBytes,
            contentType = SharedTestFixtures.testFileContentType
        )

        try {
            advanceUntilIdle()
            viewModel.selectClass(schoolClass.id)
            advanceUntilIdle()

            viewModel.createTask(
                classId = schoolClass.id,
                title = createdTask.title,
                description = createdTask.description,
                dueAt = createdTask.dueAt,
                file = pickedFile
            )

            awaitUntil(timeoutMs = 3_000) { taskCreated && uploadCalled }
            awaitUntil(timeoutMs = 3_000) { viewModel.tasks.value.any { it.id == createdTask.id } }
            assertTrue(uploadCalled)
            assertTrue(viewModel.tasks.value.any { it.id == createdTask.id })
            assertEquals(null, viewModel.error.value)
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    @Test
    fun openTask_showsOnlyTargetedStudents() = runViewModelTest {
        val tokenStorage = InMemoryTokenStorage().apply {
            saveAccessToken(SharedTestFixtures.testAuthAccessToken)
        }
        val schoolClass = SharedTestFixtures.testSchoolClassDto()
        val task = SharedTestFixtures.testTaskDto(schoolClass.id)
        val targetedStudent = SharedTestFixtures.testStudentDto(schoolClass.id)
        val nonTargetedStudent = SharedTestFixtures.testStudentDto(schoolClass.id).copy(
            id = "student-not-targeted"
        )
        val user = SharedTestFixtures.testUserDto(SharedTestFixtures.testSchoolId)
        var targetsCalled = false
        val requestedPaths = mutableListOf<String>()

        val httpClient = createTestHttpClient { request ->
            val path = request.url.encodedPath
            val normalizedPath = path.trimEnd('/')
            requestedPaths += "${request.method.value}:$normalizedPath"
            when {
                normalizedPath == "/auth/me" ->
                    respondJson(json.encodeToString(user))
                normalizedPath == "/api/classes" ->
                    respondJson(json.encodeToString(listOf(schoolClass)))
                normalizedPath == "/api/students" ->
                    respondJson(json.encodeToString(listOf(targetedStudent, nonTargetedStudent)))
                normalizedPath.endsWith("/targets") -> {
                    targetsCalled = true
                    respondJson(
                        json.encodeToString(
                            de.aarondietz.lehrerlog.data.TaskTargetsResponse(
                                taskId = task.id,
                                studentIds = listOf(targetedStudent.id)
                            )
                        )
                    )
                }
                normalizedPath.endsWith("/submissions") ->
                    respondJson(json.encodeToString(emptyList<de.aarondietz.lehrerlog.data.TaskSubmissionDto>()))
                normalizedPath.endsWith("/file") ->
                    respondJson(
                        json.encodeToString(mapOf("error" to "Not found")),
                        status = HttpStatusCode.NotFound
                    )
                else ->
                    respondJson(json.encodeToString(emptyList<String>()))
            }
        }

        val taskRepository = TaskRepository(httpClient, tokenStorage, SharedTestFixtures.testBaseUrl)
        val classRepository = SchoolClassRepository(httpClient, tokenStorage, SharedTestFixtures.testBaseUrl)
        val studentRepository = StudentRepository(httpClient, tokenStorage, SharedTestFixtures.testBaseUrl)
        val authRepository = AuthRepository(httpClient, tokenStorage)

        val viewModel = TasksViewModel(
            taskRepository = taskRepository,
            schoolClassRepository = classRepository,
            studentRepository = studentRepository,
            authRepository = authRepository
        )

        try {
            advanceUntilIdle()
            awaitUntil(timeoutMs = 3_000) { requestedPaths.contains("GET:/api/classes") }
            viewModel.openTask(task)
            awaitUntil(timeoutMs = 3_000) { targetsCalled }
            awaitUntil(timeoutMs = 3_000) { !viewModel.detailState.value.isLoading }

            val students = viewModel.detailState.value.students
            assertTrue(targetsCalled)
            assertEquals(1, students.size)
            assertEquals(targetedStudent.id, students.first().id)
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    @Test
    fun markInPersonSubmission_notTargetedShowsSpecificErrorResource() = runViewModelTest {
        val tokenStorage = InMemoryTokenStorage().apply {
            saveAccessToken(SharedTestFixtures.testAuthAccessToken)
        }
        val schoolClass = SharedTestFixtures.testSchoolClassDto()
        val task = SharedTestFixtures.testTaskDto(schoolClass.id)
        val student = SharedTestFixtures.testStudentDto(schoolClass.id)
        val user = SharedTestFixtures.testUserDto(SharedTestFixtures.testSchoolId)
        val nonTargetedStudentId = "student-not-targeted"
        val requestedPaths = mutableListOf<String>()

        val httpClient = createTestHttpClient(expectSuccess = true) { request ->
            val normalizedPath = request.url.encodedPath.trimEnd('/')
            requestedPaths += "${request.method.value}:$normalizedPath"
            when {
                normalizedPath == "/auth/me" ->
                    respondJson(json.encodeToString(user))
                normalizedPath == "/api/classes" ->
                    respondJson(json.encodeToString(listOf(schoolClass)))
                normalizedPath == "/api/students" ->
                    respondJson(json.encodeToString(listOf(student)))
                normalizedPath.endsWith("/targets") ->
                    respondJson(
                        json.encodeToString(
                            de.aarondietz.lehrerlog.data.TaskTargetsResponse(
                                taskId = task.id,
                                studentIds = listOf(student.id)
                            )
                        )
                    )
                normalizedPath.endsWith("/submissions") && request.method == HttpMethod.Post ->
                    respondJson(
                        json.encodeToString(mapOf("error" to "Student not targeted for this task")),
                        status = HttpStatusCode.BadRequest
                    )
                normalizedPath.endsWith("/submissions") ->
                    respondJson(json.encodeToString(emptyList<de.aarondietz.lehrerlog.data.TaskSubmissionDto>()))
                normalizedPath.endsWith("/file") ->
                    respondJson(
                        json.encodeToString(mapOf("error" to "Not found")),
                        status = HttpStatusCode.NotFound
                    )
                else ->
                    respondJson(json.encodeToString(emptyList<String>()))
            }
        }

        val taskRepository = TaskRepository(httpClient, tokenStorage, SharedTestFixtures.testBaseUrl)
        val classRepository = SchoolClassRepository(httpClient, tokenStorage, SharedTestFixtures.testBaseUrl)
        val studentRepository = StudentRepository(httpClient, tokenStorage, SharedTestFixtures.testBaseUrl)
        val authRepository = AuthRepository(httpClient, tokenStorage)

        val viewModel = TasksViewModel(
            taskRepository = taskRepository,
            schoolClassRepository = classRepository,
            studentRepository = studentRepository,
            authRepository = authRepository
        )

        try {
            advanceUntilIdle()
            awaitUntil(timeoutMs = 3_000) { requestedPaths.contains("GET:/api/classes") }
            viewModel.openTask(task)
            awaitUntil(timeoutMs = 3_000) { !viewModel.detailState.value.isLoading }

            viewModel.markInPersonSubmission(task.id, nonTargetedStudentId)
            awaitUntil(timeoutMs = 3_000) { viewModel.detailState.value.errorResource != null }
            assertEquals(
                Res.string.error_task_student_not_targeted.key,
                viewModel.detailState.value.errorResource?.key
            )
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }
}
