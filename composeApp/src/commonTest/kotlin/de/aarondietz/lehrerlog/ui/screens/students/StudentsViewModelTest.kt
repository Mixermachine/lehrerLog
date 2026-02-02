package de.aarondietz.lehrerlog.ui.screens.students

import androidx.lifecycle.viewModelScope
import de.aarondietz.lehrerlog.*
import de.aarondietz.lehrerlog.auth.AuthRepository
import de.aarondietz.lehrerlog.data.repository.ParentInviteRepository
import de.aarondietz.lehrerlog.data.repository.ParentLinksRepository
import de.aarondietz.lehrerlog.data.repository.SchoolClassRepository
import de.aarondietz.lehrerlog.data.repository.StudentRepository
import kotlinx.coroutines.cancel
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StudentsViewModelTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun loadsStudentsAndParentLinks() = runViewModelTest {
        val tokenStorage = InMemoryTokenStorage().apply {
            saveAccessToken(SharedTestFixtures.testAuthAccessToken)
        }
        val schoolClass = SharedTestFixtures.testSchoolClassDto()
        val student = SharedTestFixtures.testStudentDto(schoolClass.id)
        val inviteResponse = SharedTestFixtures.testParentInviteCreateResponse()
        val parentLink = SharedTestFixtures.testParentLinkDto()
        val user = SharedTestFixtures.testUserDto(SharedTestFixtures.testSchoolId)

        val httpClient = createTestHttpClient { request ->
            when (request.url.encodedPath) {
                "/auth/me" -> respondJson(json.encodeToString(user))
                "/api/classes" -> respondJson(json.encodeToString(listOf(schoolClass)))
                "/api/students" -> respondJson(json.encodeToString(listOf(student)))
                "/api/parent-invites" -> respondJson(json.encodeToString(inviteResponse))
                "/api/parent-links" -> respondJson(json.encodeToString(listOf(parentLink)))
                "/api/parent-links/${parentLink.id}/revoke" -> respondJson("{}")
                else -> respondJson(json.encodeToString(emptyList<String>()))
            }
        }

        val studentRepository = StudentRepository(httpClient, tokenStorage, SharedTestFixtures.testBaseUrl)
        val classRepository = SchoolClassRepository(httpClient, tokenStorage, SharedTestFixtures.testBaseUrl)
        val inviteRepository = ParentInviteRepository(httpClient, tokenStorage, SharedTestFixtures.testBaseUrl)
        val linksRepository = ParentLinksRepository(httpClient, tokenStorage, SharedTestFixtures.testBaseUrl)
        val authRepository = AuthRepository(httpClient, tokenStorage)

        val inviteResult = inviteRepository.createInvite(student.id)
        assertTrue(inviteResult.isSuccess, "Invite failed: ${inviteResult.exceptionOrNull()}")
        val linksResult = linksRepository.listLinks(student.id)
        assertTrue(linksResult.isSuccess, "Links failed: ${linksResult.exceptionOrNull()}")

        val viewModel = StudentsViewModel(
            studentRepository = studentRepository,
            schoolClassRepository = classRepository,
            parentInviteRepository = inviteRepository,
            parentLinksRepository = linksRepository,
            authRepository = authRepository
        )

        try {
            viewModel.createParentInvite(student.id)
            awaitUntil { viewModel.parentInvite.value.invite != null }
            assertNotNull(viewModel.parentInvite.value.invite)

            viewModel.loadParentLinks(student.id)
            awaitUntil { viewModel.parentLinks.value.links.isNotEmpty() }
            assertTrue(viewModel.parentLinks.value.links.isNotEmpty())

            viewModel.revokeParentLink(parentLink.id)
            awaitUntil { viewModel.parentLinks.value.error == null }
            assertTrue(viewModel.parentLinks.value.error == null)
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }
}
