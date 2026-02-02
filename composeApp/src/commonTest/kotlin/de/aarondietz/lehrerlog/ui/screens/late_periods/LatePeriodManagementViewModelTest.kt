package de.aarondietz.lehrerlog.ui.screens.late_periods

import androidx.lifecycle.viewModelScope
import de.aarondietz.lehrerlog.*
import de.aarondietz.lehrerlog.data.repository.LatePeriodRepository
import kotlinx.coroutines.cancel
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertTrue

class LatePeriodManagementViewModelTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun managesLatePeriods() = runViewModelTest {
        val tokenStorage = InMemoryTokenStorage().apply {
            saveAccessToken(SharedTestFixtures.testAuthAccessToken)
        }
        val period = SharedTestFixtures.testLatePeriodDto()

        val httpClient = createTestHttpClient { request ->
            when (request.url.encodedPath) {
                "/api/late-periods" -> {
                    if (request.method.value == "GET") {
                        respondJson(json.encodeToString(listOf(period)))
                    } else {
                        respondJson(json.encodeToString(period))
                    }
                }

                "/api/late-periods/${period.id}" -> respondJson(json.encodeToString(period))
                "/api/late-periods/${period.id}/activate" -> respondJson(json.encodeToString(period))
                "/api/late-periods/${period.id}/recalculate" -> respondJson("{}")
                else -> respondJson(json.encodeToString(period))
            }
        }

        val viewModel = LatePeriodManagementViewModel(
            latePeriodRepository = LatePeriodRepository(httpClient, tokenStorage, SharedTestFixtures.testBaseUrl)
        )

        try {
            awaitUntil { !viewModel.state.value.isLoading }

            viewModel.createPeriod(
                SharedTestFixtures.testLatePeriodName,
                SharedTestFixtures.testLatePeriodStartsAt,
                null
            )
            awaitUntil { !viewModel.state.value.isLoading }

            viewModel.updatePeriod(period.id, SharedTestFixtures.testLatePeriodName, null, null)
            awaitUntil { !viewModel.state.value.isLoading }

            viewModel.activatePeriod(period.id)
            awaitUntil { !viewModel.state.value.isLoading }

            viewModel.recalculatePeriod(period.id)
            awaitUntil { !viewModel.state.value.isLoading }

            assertTrue(viewModel.state.value.error == null)
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }
}
