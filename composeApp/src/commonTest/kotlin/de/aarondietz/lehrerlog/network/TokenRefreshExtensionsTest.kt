package de.aarondietz.lehrerlog.network

import de.aarondietz.lehrerlog.InMemoryTokenStorage
import de.aarondietz.lehrerlog.SharedTestFixtures
import de.aarondietz.lehrerlog.auth.AuthRepository
import de.aarondietz.lehrerlog.auth.AuthResult
import de.aarondietz.lehrerlog.createTestHttpClient
import de.aarondietz.lehrerlog.respondJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

class TokenRefreshExtensionsTest {

    @Test
    fun returnsBlockValueWhenNoException() = kotlinx.coroutines.test.runTest {
        val result = withTokenRefresh<String>(authRepository = null) {
            SharedTestFixtures.testTaskTitle
        }

        assertEquals(SharedTestFixtures.testTaskTitle, result)
    }

    @Test
    fun rethrowsUnauthorizedWhenAuthRepositoryMissing() = kotlinx.coroutines.test.runTest {
        val unauthorized = unauthorizedException()

        val thrown = try {
            withTokenRefresh<String>(authRepository = null) {
                throw unauthorized
            }
            fail("Expected ClientRequestException")
        } catch (e: ClientRequestException) {
            e
        }

        assertEquals(HttpStatusCode.Unauthorized, thrown.response.status)
    }

    @Test
    fun refreshesTokenAndRetriesOnce() = kotlinx.coroutines.test.runTest {
        val authRepository = createAuthRepository(refreshShouldSucceed = true)
        val unauthorized = unauthorizedException()
        var attempts = 0

        val result = withTokenRefresh(authRepository) {
            attempts++
            if (attempts == 1) {
                throw unauthorized
            }
            SharedTestFixtures.testTaskDescription
        }

        assertEquals(2, attempts)
        assertEquals(SharedTestFixtures.testTaskDescription, result)
    }

    @Test
    fun throwsOriginalUnauthorizedWhenRefreshFails() = kotlinx.coroutines.test.runTest {
        val authRepository = createAuthRepository(refreshShouldSucceed = false)
        val unauthorized = unauthorizedException()

        val thrown = try {
            withTokenRefresh<String>(authRepository) {
                throw unauthorized
            }
            fail("Expected ClientRequestException")
        } catch (e: ClientRequestException) {
            e
        }

        assertEquals(HttpStatusCode.Unauthorized, thrown.response.status)
    }

    @Test
    fun throwsRetryExceptionWhenRetryAlsoFails() = kotlinx.coroutines.test.runTest {
        val authRepository = createAuthRepository(refreshShouldSucceed = true)
        val unauthorized = unauthorizedException()
        var attempts = 0

        val thrown = try {
            withTokenRefresh<String>(authRepository) {
                attempts++
                if (attempts == 1) {
                    throw unauthorized
                }
                throw IllegalStateException(SharedTestFixtures.testTaskDescription)
            }
            fail("Expected IllegalStateException")
        } catch (e: Exception) {
            e
        }

        assertEquals(2, attempts)
        assertIs<IllegalStateException>(thrown)
        assertTrue(thrown.message?.contains(SharedTestFixtures.testTaskDescription) == true)
    }

    private fun createAuthRepository(refreshShouldSucceed: Boolean): AuthRepository {
        val tokenStorage = InMemoryTokenStorage().apply {
            saveRefreshToken(SharedTestFixtures.testAuthRefreshToken)
        }

        val tokenResponseJson = """
            {
              "accessToken": "${SharedTestFixtures.testAuthAccessToken}",
              "refreshToken": "${SharedTestFixtures.testAuthRefreshToken}",
              "expiresIn": ${SharedTestFixtures.testAuthExpiresIn}
            }
        """.trimIndent()

        val errorJson = """
            {
              "error": "${SharedTestFixtures.testLogMessage}"
            }
        """.trimIndent()

        val client = createTestHttpClient { request ->
            if (request.url.encodedPath == "/auth/refresh") {
                if (refreshShouldSucceed) {
                    respondJson(tokenResponseJson)
                } else {
                    respondJson(errorJson, HttpStatusCode.Unauthorized)
                }
            } else {
                respondJson(errorJson, HttpStatusCode.NotFound)
            }
        }

        return AuthRepository(client, tokenStorage)
    }

    private suspend fun unauthorizedException(): ClientRequestException {
        val client = HttpClient(MockEngine { _ ->
            respond(
                content = "{}",
                status = HttpStatusCode.Unauthorized,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }) {
            expectSuccess = true
        }

        return try {
            client.get(SharedTestFixtures.testBaseUrl)
            fail("Expected ClientRequestException")
        } catch (e: ClientRequestException) {
            e
        } finally {
            client.close()
        }
    }
}
