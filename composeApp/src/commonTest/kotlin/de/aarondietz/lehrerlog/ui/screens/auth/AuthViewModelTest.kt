package de.aarondietz.lehrerlog.ui.screens.auth

import androidx.lifecycle.viewModelScope
import de.aarondietz.lehrerlog.*
import de.aarondietz.lehrerlog.auth.AuthRepository
import de.aarondietz.lehrerlog.data.repository.SchoolRepository
import io.ktor.http.*
import kotlinx.coroutines.cancel
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AuthViewModelTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun loginSuccessUpdatesAuthState() = runViewModelTest {
        val tokenStorage = InMemoryTokenStorage()
        val authResponse = SharedTestFixtures.testAuthResponse()

        val httpClient = createTestHttpClient { request ->
            when (request.url.encodedPath) {
                "/auth/login" -> respondJson(json.encodeToString(authResponse))
                else -> respondJson(json.encodeToString(authResponse))
            }
        }

        val viewModel = AuthViewModel(
            authRepository = AuthRepository(httpClient, tokenStorage),
            schoolRepository = SchoolRepository(httpClient)
        )
        try {
            viewModel.updateLoginEmail(SharedTestFixtures.testLoginEmail)
            viewModel.updateLoginPassword(SharedTestFixtures.testLoginPassword)
            viewModel.login()

            awaitUntil { viewModel.authState.value is AuthState.Authenticated }
            assertTrue(viewModel.authState.value is AuthState.Authenticated)
            assertEquals("", viewModel.loginState.value.email)
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    @Test
    fun registerSearchAndRegisterSuccess() = runViewModelTest {
        val tokenStorage = InMemoryTokenStorage()
        val authResponse = SharedTestFixtures.testAuthResponse()
        val schoolResult = SharedTestFixtures.testSchoolSearchResultDto()

        val httpClient = createTestHttpClient { request ->
            when (request.url.encodedPath) {
                "/schools/search" -> respondJson(json.encodeToString(listOf(schoolResult)))
                "/auth/register" -> respondJson(json.encodeToString(authResponse), HttpStatusCode.Created)
                else -> respondJson(json.encodeToString(authResponse))
            }
        }

        val viewModel = AuthViewModel(
            authRepository = AuthRepository(httpClient, tokenStorage),
            schoolRepository = SchoolRepository(httpClient)
        )
        try {
            viewModel.selectRegisterSchool(schoolResult)
            viewModel.updateRegisterEmail(SharedTestFixtures.testLoginEmail)
            viewModel.updateRegisterPassword(SharedTestFixtures.testLoginPassword)
            viewModel.updateRegisterConfirmPassword(SharedTestFixtures.testLoginPassword)
            viewModel.updateRegisterFirstName(SharedTestFixtures.testAuthFirstName)
            viewModel.updateRegisterLastName(SharedTestFixtures.testAuthLastName)
            viewModel.register()

            awaitUntil { viewModel.authState.value is AuthState.Authenticated }
            assertTrue(viewModel.authState.value is AuthState.Authenticated)
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    @Test
    fun loginRequiresCredentials() = runViewModelTest {
        val tokenStorage = InMemoryTokenStorage()
        val httpClient = createTestHttpClient {
            respondJson(json.encodeToString(SharedTestFixtures.testAuthResponse()))
        }

        val viewModel = AuthViewModel(
            authRepository = AuthRepository(httpClient, tokenStorage),
            schoolRepository = SchoolRepository(httpClient)
        )
        try {
            viewModel.login()

            val error = viewModel.loginState.value.error
            assertNotNull(error)
            assertTrue(
                viewModel.authState.value is AuthState.Unauthenticated ||
                        viewModel.authState.value is AuthState.Initial
            )
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }
}
