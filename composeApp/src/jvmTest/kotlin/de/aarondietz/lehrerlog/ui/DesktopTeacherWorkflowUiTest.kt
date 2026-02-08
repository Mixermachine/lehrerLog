package de.aarondietz.lehrerlog.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import de.aarondietz.lehrerlog.InMemoryTokenStorage
import de.aarondietz.lehrerlog.SharedTestFixtures
import de.aarondietz.lehrerlog.createTestHttpClient
import de.aarondietz.lehrerlog.data.FileMetadataDto
import de.aarondietz.lehrerlog.data.LateStatus
import de.aarondietz.lehrerlog.data.SchoolClassDto
import de.aarondietz.lehrerlog.data.StudentDto
import de.aarondietz.lehrerlog.data.TaskDto
import de.aarondietz.lehrerlog.data.TaskSubmissionDto
import de.aarondietz.lehrerlog.data.TaskSubmissionSummaryDto
import de.aarondietz.lehrerlog.data.TaskSubmissionType
import de.aarondietz.lehrerlog.data.TaskTargetsResponse
import de.aarondietz.lehrerlog.data.repository.ParentInviteRepository
import de.aarondietz.lehrerlog.data.repository.ParentLinksRepository
import de.aarondietz.lehrerlog.data.repository.SchoolClassRepository
import de.aarondietz.lehrerlog.data.repository.SchoolRepository
import de.aarondietz.lehrerlog.data.repository.StudentRepository
import de.aarondietz.lehrerlog.data.repository.TaskRepository
import de.aarondietz.lehrerlog.respondJson
import de.aarondietz.lehrerlog.ui.screens.auth.AuthState
import de.aarondietz.lehrerlog.ui.screens.auth.AuthViewModel
import de.aarondietz.lehrerlog.ui.screens.auth.RegisterScreen
import de.aarondietz.lehrerlog.ui.screens.students.StudentsScreen
import de.aarondietz.lehrerlog.ui.screens.students.StudentsViewModel
import de.aarondietz.lehrerlog.ui.screens.tasks.TasksScreen
import de.aarondietz.lehrerlog.ui.screens.tasks.TasksViewModel
import de.aarondietz.lehrerlog.ui.test.UiTestTags
import de.aarondietz.lehrerlog.ui.theme.LehrerLogTheme
import de.aarondietz.lehrerlog.ui.util.FilePickerJvmTestHooks
import de.aarondietz.lehrerlog.ui.util.PickedFile
import de.aarondietz.lehrerlog.auth.AuthRepository
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class DesktopTeacherWorkflowUiTest {
    private val json = Json { ignoreUnknownKeys = true }

    @AfterTest
    fun clearFilePickerHook() {
        FilePickerJvmTestHooks.launcherOverride = null
    }

    @Test
    fun accountCreation_registerFlowAuthenticatesUser() = runComposeUiTest {
        val tokenStorage = InMemoryTokenStorage()
        val school = SharedTestFixtures.testSchoolSearchResultDto()
        val user = SharedTestFixtures.testUserDto()
        val authResponse = SharedTestFixtures.testAuthResponse()
        var registerRequests = 0

        val httpClient = createTestHttpClient(expectSuccess = true) { request ->
            when {
                request.url.encodedPath == "/schools/search" && request.method == HttpMethod.Get ->
                    respondJson(json.encodeToString(listOf(school)))

                request.url.encodedPath == "/auth/register" && request.method == HttpMethod.Post -> {
                    registerRequests += 1
                    respondJson(json.encodeToString(authResponse), status = HttpStatusCode.Created)
                }

                request.url.encodedPath == "/auth/me" && request.method == HttpMethod.Get ->
                    respondJson(json.encodeToString(user))

                else -> respondJson(
                    json.encodeToString(mapOf("error" to "not found")),
                    status = HttpStatusCode.NotFound
                )
            }
        }

        val authViewModel = AuthViewModel(
            authRepository = AuthRepository(httpClient, tokenStorage),
            schoolRepository = SchoolRepository(httpClient, SharedTestFixtures.testBaseUrl)
        )

        setContent {
            LehrerLogTheme {
                RegisterScreen(
                    onNavigateToLogin = {},
                    viewModel = authViewModel
                )
            }
        }

        onNodeWithTag(UiTestTags.registerSchoolQueryField).performTextInput(SharedTestFixtures.testSchoolQuery)
        waitUntil(timeoutMillis = 5_000) {
            runCatching {
                onNodeWithTag(UiTestTags.registerSchoolSuggestion(school.code)).fetchSemanticsNode()
            }.isSuccess
        }
        onNodeWithTag(UiTestTags.registerSchoolSuggestion(school.code)).performClick()
        onNodeWithTag(UiTestTags.registerFirstNameField).performTextInput(SharedTestFixtures.testAuthFirstName)
        onNodeWithTag(UiTestTags.registerLastNameField).performTextInput(SharedTestFixtures.testAuthLastName)
        onNodeWithTag(UiTestTags.registerEmailField).performTextInput(SharedTestFixtures.testLoginEmail)
        onNodeWithTag(UiTestTags.registerPasswordField).performTextInput(SharedTestFixtures.testLoginPassword)
        onNodeWithTag(UiTestTags.registerConfirmPasswordField).performTextInput(SharedTestFixtures.testLoginPassword)
        onNodeWithTag(UiTestTags.registerSubmitButton).performClick()

        waitUntil(timeoutMillis = 5_000) { authViewModel.authState.value is AuthState.Authenticated }
        assertEquals(1, registerRequests)
        assertTrue(authViewModel.authState.value is AuthState.Authenticated)
        assertEquals(SharedTestFixtures.testAuthAccessToken, tokenStorage.getAccessToken())
    }

    @Test
    fun studentsFlow_createClassAddAndDeleteStudent() = runComposeUiTest {
        val backend = WorkflowBackend()
        val viewModel = createStudentsViewModel(backend)

        setContent {
            LehrerLogTheme {
                StudentsScreen(viewModel = viewModel)
            }
        }

        onNodeWithTag(UiTestTags.studentsAddClassFab).performClick()
        onNodeWithTag(UiTestTags.addClassDialogNameField).performTextInput(SharedTestFixtures.testClassName)
        onNodeWithTag(UiTestTags.addClassDialogConfirmButton).performClick()

        waitUntil(timeoutMillis = 5_000) { viewModel.classes.value.isNotEmpty() }
        val classId = viewModel.classes.value.first().id

        onNodeWithTag(UiTestTags.studentsClassExpandButton(classId)).performClick()
        onNodeWithTag(UiTestTags.studentsAddStudentButton(classId)).performClick()
        onNodeWithTag(UiTestTags.addStudentDialogFirstNameField).performTextInput(SharedTestFixtures.testStudentFirstName)
        onNodeWithTag(UiTestTags.addStudentDialogLastNameField).performTextInput(SharedTestFixtures.testStudentLastName)
        onNodeWithTag(UiTestTags.addStudentDialogConfirmButton).performClick()

        waitUntil(timeoutMillis = 5_000) { viewModel.students.value.isNotEmpty() }
        val studentId = viewModel.students.value.first().id

        onNodeWithTag(UiTestTags.studentsDeleteStudentButton(studentId)).performClick()
        onNodeWithTag(UiTestTags.studentsDeleteStudentConfirmButton).performClick()

        waitUntil(timeoutMillis = 5_000) { viewModel.students.value.isEmpty() }
        assertTrue(backend.createdClassIds.contains(classId))
        assertTrue(backend.deletedStudentIds.contains(studentId))
    }

    @Test
    fun tasksFlow_createWithAndWithoutFile_thenMarkInPerson() = runComposeUiTest {
        val backend = WorkflowBackend(
            initialClasses = mutableListOf(
                SharedTestFixtures.testSchoolClassDto()
            ),
            initialStudents = mutableListOf(
                SharedTestFixtures.testStudentDto(SharedTestFixtures.testClassId)
            )
        )
        val viewModel = createTasksViewModel(backend)

        FilePickerJvmTestHooks.launcherOverride = { _, onFilePicked, _ ->
            onFilePicked(
                PickedFile(
                    name = SharedTestFixtures.testFileName,
                    bytes = SharedTestFixtures.testFileBytes,
                    contentType = SharedTestFixtures.testFileContentType
                )
            )
        }

        setContent {
            LehrerLogTheme {
                TasksScreen(viewModel = viewModel)
            }
        }

        waitUntil(timeoutMillis = 5_000) { viewModel.classes.value.isNotEmpty() }
        val classId = viewModel.classes.value.first().id
        viewModel.selectClass(classId)
        waitUntil(timeoutMillis = 5_000) { !viewModel.isLoading.value }

        // Create task without file.
        onNodeWithTag(UiTestTags.tasksAddFab).performClick()
        onNodeWithTag(UiTestTags.addTaskDialogTitleField).performTextInput("${SharedTestFixtures.testTaskTitle} A")
        onNodeWithTag(UiTestTags.addTaskDialogOpenDatePickerButton).performClick()
        onNodeWithText("OK").performClick()
        onNodeWithTag(UiTestTags.addTaskDialogConfirmButton).performClick()
        waitUntil(timeoutMillis = 5_000) { viewModel.tasks.value.size == 1 }
        assertTrue(backend.uploadedTaskIds.isEmpty())

        // Create task with file.
        onNodeWithTag(UiTestTags.tasksAddFab).performClick()
        onNodeWithTag(UiTestTags.addTaskDialogTitleField).performTextInput("${SharedTestFixtures.testTaskTitle} B")
        onNodeWithTag(UiTestTags.addTaskDialogOpenDatePickerButton).performClick()
        onNodeWithText("OK").performClick()
        onNodeWithTag(UiTestTags.addTaskDialogUploadButton).performClick()
        onNodeWithText(SharedTestFixtures.testFileName).assertIsDisplayed()
        onNodeWithTag(UiTestTags.addTaskDialogConfirmButton).performClick()

        waitUntil(timeoutMillis = 5_000) { viewModel.tasks.value.size == 2 && backend.uploadedTaskIds.isNotEmpty() }
        val taskWithFileId = viewModel.tasks.value.last().id
        assertTrue(backend.uploadedTaskIds.contains(taskWithFileId))

        // Mark submission in-person for the first task.
        val firstTaskId = viewModel.tasks.value.first().id
        val studentId = backend.students.first().id
        onNodeWithTag(UiTestTags.tasksCard(firstTaskId)).performClick()
        waitUntil(timeoutMillis = 5_000) {
            viewModel.detailState.value.task?.id == firstTaskId && viewModel.detailState.value.students.isNotEmpty()
        }
        onNodeWithTag(UiTestTags.taskDetailMarkInPersonButton(studentId)).performClick()
        waitUntil(timeoutMillis = 5_000) {
            backend.submissionsByTask[firstTaskId].orEmpty().isNotEmpty()
        }
        assertTrue(backend.submissionsByTask[firstTaskId].orEmpty().isNotEmpty())
    }

    private fun createStudentsViewModel(backend: WorkflowBackend): StudentsViewModel {
        val tokenStorage = InMemoryTokenStorage().apply {
            saveAccessToken(SharedTestFixtures.testAuthAccessToken)
            saveRefreshToken(SharedTestFixtures.testAuthRefreshToken)
        }
        val httpClient = backend.createHttpClient()
        val authRepository = AuthRepository(httpClient, tokenStorage)
        return StudentsViewModel(
            studentRepository = StudentRepository(httpClient, tokenStorage, SharedTestFixtures.testBaseUrl),
            schoolClassRepository = SchoolClassRepository(httpClient, tokenStorage, SharedTestFixtures.testBaseUrl),
            parentInviteRepository = ParentInviteRepository(httpClient, tokenStorage, SharedTestFixtures.testBaseUrl),
            parentLinksRepository = ParentLinksRepository(httpClient, tokenStorage, SharedTestFixtures.testBaseUrl),
            authRepository = authRepository
        )
    }

    private fun createTasksViewModel(backend: WorkflowBackend): TasksViewModel {
        val tokenStorage = InMemoryTokenStorage().apply {
            saveAccessToken(SharedTestFixtures.testAuthAccessToken)
            saveRefreshToken(SharedTestFixtures.testAuthRefreshToken)
        }
        val httpClient = backend.createHttpClient()
        val authRepository = AuthRepository(httpClient, tokenStorage)
        return TasksViewModel(
            taskRepository = TaskRepository(httpClient, tokenStorage, SharedTestFixtures.testBaseUrl),
            schoolClassRepository = SchoolClassRepository(httpClient, tokenStorage, SharedTestFixtures.testBaseUrl),
            studentRepository = StudentRepository(httpClient, tokenStorage, SharedTestFixtures.testBaseUrl),
            authRepository = authRepository
        )
    }

    private inner class WorkflowBackend(
        initialClasses: MutableList<SchoolClassDto> = mutableListOf(),
        initialStudents: MutableList<StudentDto> = mutableListOf()
    ) {
        val classes = initialClasses
        val students = initialStudents
        val tasks = mutableListOf<TaskDto>()
        val submissionsByTask = mutableMapOf<String, MutableList<TaskSubmissionDto>>()
        val taskFiles = mutableMapOf<String, FileMetadataDto>()
        val uploadedTaskIds = mutableSetOf<String>()
        val createdClassIds = mutableSetOf<String>()
        val deletedStudentIds = mutableSetOf<String>()
        private var classCounter = 0
        private var studentCounter = 0
        private var taskCounter = 0
        private var submissionCounter = 0

        fun createHttpClient() = createTestHttpClient(expectSuccess = true) { request ->
            when {
                request.url.encodedPath == "/auth/me" && request.method == HttpMethod.Get -> {
                    respondJson(json.encodeToString(SharedTestFixtures.testUserDto(SharedTestFixtures.testSchoolId)))
                }

                request.url.encodedPath == "/api/classes" && request.method == HttpMethod.Get -> {
                    respondJson(json.encodeToString(classes))
                }

                request.url.encodedPath == "/api/classes" && request.method == HttpMethod.Post -> {
                    classCounter += 1
                    val createdClass = SchoolClassDto(
                        id = "class-ui-$classCounter",
                        schoolId = SharedTestFixtures.testSchoolId,
                        name = SharedTestFixtures.testClassName,
                        alternativeName = null,
                        studentCount = students.count { it.classIds.contains("class-ui-$classCounter") },
                        version = SharedTestFixtures.testVersion,
                        createdAt = SharedTestFixtures.testCreatedAt,
                        updatedAt = SharedTestFixtures.testUpdatedAt
                    )
                    classes += createdClass
                    createdClassIds += createdClass.id
                    respondJson(json.encodeToString(createdClass), status = HttpStatusCode.Created)
                }

                request.url.encodedPath.startsWith("/api/classes/") && request.method == HttpMethod.Delete -> {
                    val classId = request.url.encodedPath.substringAfterLast('/')
                    classes.removeAll { it.id == classId }
                    respondJson(json.encodeToString(mapOf("ok" to true)))
                }

                request.url.encodedPath == "/api/students" && request.method == HttpMethod.Get -> {
                    respondJson(json.encodeToString(students))
                }

                request.url.encodedPath == "/api/students" && request.method == HttpMethod.Post -> {
                    studentCounter += 1
                    val classId = classes.firstOrNull()?.id ?: SharedTestFixtures.testClassId
                    val createdStudent = StudentDto(
                        id = "student-ui-$studentCounter",
                        schoolId = SharedTestFixtures.testSchoolId,
                        firstName = SharedTestFixtures.testStudentFirstName,
                        lastName = SharedTestFixtures.testStudentLastName,
                        classIds = listOf(classId),
                        version = SharedTestFixtures.testVersion,
                        createdAt = SharedTestFixtures.testCreatedAt,
                        updatedAt = SharedTestFixtures.testUpdatedAt
                    )
                    students += createdStudent
                    respondJson(json.encodeToString(createdStudent), status = HttpStatusCode.Created)
                }

                request.url.encodedPath.startsWith("/api/students/") && request.method == HttpMethod.Delete -> {
                    val studentId = request.url.encodedPath.substringAfterLast('/')
                    deletedStudentIds += studentId
                    students.removeAll { it.id == studentId }
                    respondJson(json.encodeToString(mapOf("ok" to true)))
                }

                request.url.encodedPath == "/api/tasks" && request.method == HttpMethod.Get -> {
                    val classId = request.url.parameters["classId"]
                    val filtered = if (classId == null) tasks else tasks.filter { it.schoolClassId == classId }
                    respondJson(json.encodeToString(filtered))
                }

                request.url.encodedPath == "/api/tasks" && request.method == HttpMethod.Post -> {
                    taskCounter += 1
                    val classId = classes.firstOrNull()?.id ?: SharedTestFixtures.testClassId
                    val createdTask = TaskDto(
                        id = "task-ui-$taskCounter",
                        schoolId = SharedTestFixtures.testSchoolId,
                        schoolClassId = classId,
                        title = "${SharedTestFixtures.testTaskTitle} $taskCounter",
                        description = SharedTestFixtures.testTaskDescription,
                        dueAt = SharedTestFixtures.testDueAt,
                        version = SharedTestFixtures.testVersion,
                        createdAt = SharedTestFixtures.testCreatedAt,
                        updatedAt = SharedTestFixtures.testUpdatedAt
                    )
                    tasks += createdTask
                    respondJson(json.encodeToString(createdTask), status = HttpStatusCode.Created)
                }

                request.url.encodedPath.matches(Regex("/api/tasks/[^/]+/summary")) && request.method == HttpMethod.Get -> {
                    val taskId = request.url.encodedPath.split("/")[3]
                    val submittedStudents = submissionsByTask[taskId].orEmpty().map { it.studentId }.distinct().size
                    val summary = TaskSubmissionSummaryDto(
                        taskId = taskId,
                        totalStudents = students.size,
                        submittedStudents = submittedStudents
                    )
                    respondJson(json.encodeToString(summary))
                }

                request.url.encodedPath.matches(Regex("/api/tasks/[^/]+/targets")) && request.method == HttpMethod.Get -> {
                    val taskId = request.url.encodedPath.split("/")[3]
                    val targets = TaskTargetsResponse(taskId = taskId, studentIds = students.map { it.id })
                    respondJson(json.encodeToString(targets))
                }

                request.url.encodedPath.matches(Regex("/api/tasks/[^/]+/submissions")) && request.method == HttpMethod.Get -> {
                    val taskId = request.url.encodedPath.split("/")[3]
                    respondJson(json.encodeToString(submissionsByTask[taskId].orEmpty()))
                }

                request.url.encodedPath.matches(Regex("/api/tasks/[^/]+/submissions")) && request.method == HttpMethod.Post -> {
                    val taskId = request.url.encodedPath.split("/")[3]
                    val studentId = students.firstOrNull()?.id ?: SharedTestFixtures.testStudentId
                    submissionCounter += 1
                    val createdSubmission = TaskSubmissionDto(
                        id = "submission-ui-$submissionCounter",
                        taskId = taskId,
                        studentId = studentId,
                        submissionType = TaskSubmissionType.IN_PERSON,
                        grade = null,
                        note = null,
                        lateStatus = LateStatus.ON_TIME,
                        latePeriodId = null,
                        decidedBy = null,
                        decidedAt = null,
                        submittedAt = SharedTestFixtures.testSubmittedAt,
                        createdAt = SharedTestFixtures.testCreatedAt,
                        updatedAt = SharedTestFixtures.testUpdatedAt
                    )
                    submissionsByTask.getOrPut(taskId) { mutableListOf() }.add(createdSubmission)
                    respondJson(json.encodeToString(createdSubmission), status = HttpStatusCode.Created)
                }

                request.url.encodedPath.matches(Regex("/api/tasks/[^/]+/files")) && request.method == HttpMethod.Post -> {
                    val taskId = request.url.encodedPath.split("/")[3]
                    uploadedTaskIds += taskId
                    val metadata = FileMetadataDto(
                        id = "${SharedTestFixtures.testFileId}-$taskId",
                        objectKey = "tasks/$taskId/${SharedTestFixtures.testFileName}",
                        sizeBytes = SharedTestFixtures.testFileBytes.size.toLong(),
                        mimeType = SharedTestFixtures.testFileContentType,
                        createdAt = SharedTestFixtures.testFileCreatedAt
                    )
                    taskFiles[taskId] = metadata
                    respondJson(json.encodeToString(metadata), status = HttpStatusCode.Created)
                }

                request.url.encodedPath.matches(Regex("/api/tasks/[^/]+/file")) && request.method == HttpMethod.Get -> {
                    val taskId = request.url.encodedPath.split("/")[3]
                    val metadata = taskFiles[taskId]
                    if (metadata == null) {
                        respondJson(
                            json.encodeToString(mapOf("error" to "Not found")),
                            status = HttpStatusCode.NotFound
                        )
                    } else {
                        respondJson(json.encodeToString(metadata))
                    }
                }

                else -> {
                    respondJson(
                        json.encodeToString(mapOf("error" to "not found")),
                        status = HttpStatusCode.NotFound
                    )
                }
            }
        }
    }
}
