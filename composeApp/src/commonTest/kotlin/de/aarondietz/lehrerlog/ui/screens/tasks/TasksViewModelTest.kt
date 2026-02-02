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
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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

        val httpClient = createTestHttpClient { request ->
            when (request.url.encodedPath) {
                "/auth/me" -> respondJson(json.encodeToString(user))
                "/api/classes" -> respondJson(json.encodeToString(listOf(schoolClass)))
                "/api/tasks" -> respondJson(json.encodeToString(listOf(task)))
                "/api/tasks/${task.id}/summary" -> respondJson(json.encodeToString(summary))
                "/api/tasks/${task.id}/submissions" -> {
                    if (request.method == HttpMethod.Post) {
                        respondJson(json.encodeToString(submission))
                    } else {
                        respondJson(json.encodeToString(listOf(submission)))
                    }
                }

                "/api/students" -> respondJson(json.encodeToString(listOf(student)))
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
            awaitUntil { !viewModel.isLoading.value && viewModel.tasks.value.isNotEmpty() }
            assertTrue(viewModel.tasks.value.isNotEmpty())
            assertTrue(viewModel.summaries.value.isNotEmpty())

            viewModel.openTask(task)
            awaitUntil { viewModel.detailState.value.task != null }
            awaitUntil { viewModel.detailState.value.students.isNotEmpty() }

            val detailState = viewModel.detailState.value
            assertNotNull(detailState.task)
            assertTrue(detailState.students.isNotEmpty())

            viewModel.markInPersonSubmission(task.id, student.id)
            awaitUntil { viewModel.detailState.value.error == null }
            assertTrue(viewModel.detailState.value.error == null)
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

        val httpClient = createTestHttpClient { request ->
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

                "/api/tasks/${task.id}/files" -> respondJson(json.encodeToString(fileMetadata))
                "/api/tasks/${task.id}/submissions/${submission.id}/files" -> respondJson(
                    json.encodeToString(fileMetadata)
                )

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
}
