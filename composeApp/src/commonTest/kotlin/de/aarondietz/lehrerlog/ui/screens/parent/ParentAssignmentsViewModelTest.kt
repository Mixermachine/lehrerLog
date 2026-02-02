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

class ParentAssignmentsViewModelTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun loadsAssignmentsForSelectedStudent() = runViewModelTest {
        val tokenStorage = InMemoryTokenStorage().apply {
            saveAccessToken(SharedTestFixtures.testAuthAccessToken)
        }
        val student = SharedTestFixtures.testStudentDto(SharedTestFixtures.testClassId)
        val task = SharedTestFixtures.testTaskDto(SharedTestFixtures.testClassId)

        val httpClient = createTestHttpClient { request ->
            when (request.url.encodedPath) {
                "/api/parent/students" -> respondJson(json.encodeToString(listOf(student)))
                "/api/parent/assignments" -> respondJson(json.encodeToString(listOf(task)))
                else -> respondJson(json.encodeToString(emptyList<String>()))
            }
        }

        val repository = ParentRepository(httpClient, tokenStorage, SharedTestFixtures.testBaseUrl)
        val selectionRepository = ParentSelectionRepository()
        val viewModel = ParentAssignmentsViewModel(repository, selectionRepository)

        try {
            awaitUntil { viewModel.state.value.assignments.isNotEmpty() }
            assertNotNull(viewModel.state.value.selectedStudentId)
            assertTrue(viewModel.state.value.assignments.isNotEmpty())
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    @Test
    fun surfacesErrorsWhenAssignmentsFail() = runViewModelTest {
        val tokenStorage = InMemoryTokenStorage().apply {
            saveAccessToken(SharedTestFixtures.testAuthAccessToken)
        }
        val student = SharedTestFixtures.testStudentDto(SharedTestFixtures.testClassId)

        val httpClient = createTestHttpClient { request ->
            when (request.url.encodedPath) {
                "/api/parent/students" -> respondJson(json.encodeToString(listOf(student)))
                "/api/parent/assignments" -> error("Assignments failed")
                else -> respondJson(json.encodeToString(emptyList<String>()))
            }
        }

        val repository = ParentRepository(httpClient, tokenStorage, SharedTestFixtures.testBaseUrl)
        val selectionRepository = ParentSelectionRepository()
        val viewModel = ParentAssignmentsViewModel(repository, selectionRepository)

        try {
            awaitUntil { viewModel.state.value.errorMessage != null }
            assertTrue(viewModel.state.value.assignments.isEmpty())
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }
}
