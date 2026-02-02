package de.aarondietz.lehrerlog.ui.screens.parent_management

import androidx.lifecycle.viewModelScope
import de.aarondietz.lehrerlog.*
import de.aarondietz.lehrerlog.data.repository.ParentInviteRepository
import de.aarondietz.lehrerlog.data.repository.ParentLinksRepository
import kotlinx.coroutines.cancel
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ParentInviteManagementViewModelTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun generatesInvitesAndLoadsLinks() = runViewModelTest {
        val tokenStorage = InMemoryTokenStorage().apply {
            saveAccessToken(SharedTestFixtures.testAuthAccessToken)
        }
        val inviteResponse = SharedTestFixtures.testParentInviteCreateResponse()
        val parentLink = SharedTestFixtures.testParentLinkDto()

        val httpClient = createTestHttpClient { request ->
            when (request.url.encodedPath) {
                "/api/parent-invites" -> respondJson(json.encodeToString(inviteResponse))
                "/api/parent-links" -> respondJson(json.encodeToString(listOf(parentLink)))
                "/api/parent-links/${parentLink.id}/revoke" -> respondJson("{}")
                else -> respondJson(json.encodeToString(emptyList<String>()))
            }
        }

        val inviteRepository = ParentInviteRepository(httpClient, tokenStorage, SharedTestFixtures.testBaseUrl)
        val linksRepository = ParentLinksRepository(httpClient, tokenStorage, SharedTestFixtures.testBaseUrl)
        val inviteResult = inviteRepository.createInvite(SharedTestFixtures.testStudentId)
        assertTrue(inviteResult.isSuccess, "Invite failed: ${inviteResult.exceptionOrNull()}")
        val linksResult = linksRepository.listLinks(SharedTestFixtures.testStudentId)
        assertTrue(linksResult.isSuccess, "Links failed: ${linksResult.exceptionOrNull()}")

        val viewModel = ParentInviteManagementViewModel(
            parentInviteRepository = inviteRepository,
            parentLinksRepository = linksRepository
        )

        try {
            viewModel.generateInvite(SharedTestFixtures.testStudentId)
            awaitUntil { viewModel.state.value.inviteResponse != null }
            awaitUntil { viewModel.state.value.parentLinks.isNotEmpty() }
            assertNotNull(viewModel.state.value.inviteResponse)
            assertTrue(viewModel.state.value.parentLinks.isNotEmpty())

            viewModel.revokeLink(parentLink.id, SharedTestFixtures.testStudentId)
            awaitUntil { viewModel.state.value.error == null }
            assertTrue(viewModel.state.value.error == null)
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }
}
