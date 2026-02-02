package de.aarondietz.lehrerlog.ui.screens.parent

import androidx.lifecycle.viewModelScope
import de.aarondietz.lehrerlog.*
import de.aarondietz.lehrerlog.data.repository.ParentRepository
import de.aarondietz.lehrerlog.data.repository.ParentSelectionRepository
import kotlinx.coroutines.cancel
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ParentSubmissionsViewModelTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun loadsSubmissionsForSelectedStudent() = runViewModelTest {
        val tokenStorage = InMemoryTokenStorage().apply {
            saveAccessToken(SharedTestFixtures.testAuthAccessToken)
        }
        val student = SharedTestFixtures.testStudentDto(SharedTestFixtures.testClassId)
        val submission = SharedTestFixtures.testSubmissionDto(
            SharedTestFixtures.testTaskId,
            student.id
        )

        val httpClient = createTestHttpClient { request ->
            when (request.url.encodedPath) {
                "/api/parent/students" -> respondJson(json.encodeToString(listOf(student)))
                "/api/parent/submissions" -> respondJson(json.encodeToString(listOf(submission)))
                else -> respondJson(json.encodeToString(emptyList<String>()))
            }
        }

        val repository = ParentRepository(httpClient, tokenStorage, SharedTestFixtures.testBaseUrl)
        val selectionRepository = ParentSelectionRepository()
        val viewModel = ParentSubmissionsViewModel(repository, selectionRepository)

        try {
            awaitUntil { viewModel.state.value.submissions.isNotEmpty() }
            assertNotNull(viewModel.state.value.selectedStudentId)
            assertTrue(viewModel.state.value.submissions.isNotEmpty())
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    @Test
    fun surfacesErrorsWhenSubmissionsFail() = runViewModelTest {
        val tokenStorage = InMemoryTokenStorage().apply {
            saveAccessToken(SharedTestFixtures.testAuthAccessToken)
        }
        val student = SharedTestFixtures.testStudentDto(SharedTestFixtures.testClassId)

        val httpClient = createTestHttpClient { request ->
            when (request.url.encodedPath) {
                "/api/parent/students" -> respondJson(json.encodeToString(listOf(student)))
                "/api/parent/submissions" -> error("Submissions failed")
                else -> respondJson(json.encodeToString(emptyList<String>()))
            }
        }

        val repository = ParentRepository(httpClient, tokenStorage, SharedTestFixtures.testBaseUrl)
        val selectionRepository = ParentSelectionRepository()
        val viewModel = ParentSubmissionsViewModel(repository, selectionRepository)

        try {
            awaitUntil { viewModel.state.value.errorMessage != null }
            assertTrue(viewModel.state.value.submissions.isEmpty())
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }
}
