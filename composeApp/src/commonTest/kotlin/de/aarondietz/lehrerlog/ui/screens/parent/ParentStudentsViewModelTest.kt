package de.aarondietz.lehrerlog.ui.screens.parent

import androidx.lifecycle.viewModelScope
import de.aarondietz.lehrerlog.*
import de.aarondietz.lehrerlog.data.repository.ParentRepository
import de.aarondietz.lehrerlog.data.repository.ParentSelectionRepository
import kotlinx.coroutines.cancel
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ParentStudentsViewModelTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun refreshSelectsFirstStudent() = runViewModelTest {
        val tokenStorage = InMemoryTokenStorage().apply {
            saveAccessToken(SharedTestFixtures.testAuthAccessToken)
        }
        val student = SharedTestFixtures.testStudentDto(SharedTestFixtures.testClassId)

        val httpClient = createTestHttpClient { request ->
            when (request.url.encodedPath) {
                "/api/parent/students" -> respondJson(json.encodeToString(listOf(student)))
                else -> respondJson(json.encodeToString(emptyList<String>()))
            }
        }

        val repository = ParentRepository(httpClient, tokenStorage, SharedTestFixtures.testBaseUrl)
        val selectionRepository = ParentSelectionRepository()
        val viewModel = ParentStudentsViewModel(repository, selectionRepository)

        try {
            awaitUntil { !viewModel.state.value.isLoading && viewModel.state.value.students.isNotEmpty() }
            assertEquals(student.id, viewModel.state.value.selectedStudentId)
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    @Test
    fun reportsErrorsOnRefreshFailure() = runViewModelTest {
        val tokenStorage = InMemoryTokenStorage().apply {
            saveAccessToken(SharedTestFixtures.testAuthAccessToken)
        }

        val httpClient = createTestHttpClient { request ->
            when (request.url.encodedPath) {
                "/api/parent/students" -> error("Students failed")
                else -> respondJson(json.encodeToString(emptyList<String>()))
            }
        }

        val repository = ParentRepository(httpClient, tokenStorage, SharedTestFixtures.testBaseUrl)
        val selectionRepository = ParentSelectionRepository()
        val viewModel = ParentStudentsViewModel(repository, selectionRepository)

        try {
            awaitUntil { viewModel.state.value.errorMessage != null }
            assertTrue(viewModel.state.value.students.isEmpty())
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }
}
