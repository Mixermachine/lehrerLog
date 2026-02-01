package de.aarondietz.lehrerlog.auth

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.*

/**
 * Fake TokenStorage for testing that stores tokens in memory.
 */
class FakeTokenStorage : TokenStorage {
    private var accessToken: String? = null
    private var refreshToken: String? = null

    override fun saveAccessToken(token: String) {
        accessToken = token
    }

    override fun saveRefreshToken(token: String) {
        refreshToken = token
    }

    override fun getAccessToken(): String? = accessToken

    override fun getRefreshToken(): String? = refreshToken

    override fun clearTokens() {
        accessToken = null
        refreshToken = null
    }
}

/**
 * Integration tests for AuthRepository.
 * These tests use MockEngine to simulate server responses without needing a running server.
 */
class AuthRepositoryTest {

    private lateinit var mockEngine: MockEngine
    private lateinit var httpClient: HttpClient
    private lateinit var tokenStorage: FakeTokenStorage
    private lateinit var authRepository: AuthRepository

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    @BeforeTest
    fun setup() {
        tokenStorage = FakeTokenStorage()
    }

    @AfterTest
    fun tearDown() {
        if (::httpClient.isInitialized) {
            httpClient.close()
        }
    }

    private fun createMockClient(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): HttpClient {
        mockEngine = MockEngine(handler)
        return HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(json)
            }
        }
    }

    @Test
    fun testSuccessfulRegistration() = runTest {
        val expectedResponse = AuthResponse(
            accessToken = "mock-access-token",
            refreshToken = "mock-refresh-token",
            expiresIn = 900,
            user = UserDto(
                id = "user-123",
                email = "test@example.com",
                firstName = "Test",
                lastName = "User",
                role = "TEACHER",
                schoolId = null
            )
        )

        httpClient = createMockClient { request ->
            when (request.url.encodedPath) {
                "/auth/register" -> {
                    assertEquals(HttpMethod.Post, request.method)
                    assertEquals(ContentType.Application.Json, request.body.contentType)

                    respond(
                        content = json.encodeToString(expectedResponse),
                        status = HttpStatusCode.Created,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }

                else -> error("Unexpected request: ${request.url.encodedPath}")
            }
        }

        authRepository = AuthRepository(httpClient, tokenStorage)

        val result = authRepository.register(
            email = "test@example.com",
            password = "password123",
            firstName = "Test",
            lastName = "User",
            schoolCode = null
        )

        assertTrue(result is AuthResult.Success)
        val authResponse = (result as AuthResult.Success).data
        assertEquals("mock-access-token", authResponse.accessToken)
        assertEquals("test@example.com", authResponse.user.email)
        assertEquals("Test", authResponse.user.firstName)
        assertEquals("User", authResponse.user.lastName)
    }

    @Test
    fun testRegistrationWithInvalidSchoolCode() = runTest {
        val errorResponse = ErrorResponse("Invalid school code")

        httpClient = createMockClient { request ->
            when (request.url.encodedPath) {
                "/auth/register" -> {
                    respond(
                        content = json.encodeToString(errorResponse),
                        status = HttpStatusCode.Conflict,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }

                else -> error("Unexpected request: ${request.url.encodedPath}")
            }
        }

        authRepository = AuthRepository(httpClient, tokenStorage)

        val result = authRepository.register(
            email = "test@example.com",
            password = "password123",
            firstName = "Test",
            lastName = "User",
            schoolCode = "INVALID"
        )

        assertTrue(result is AuthResult.Error)
        val errorMessage = (result as AuthResult.Error).message
        assertTrue(errorMessage.contains("Invalid school code"))
    }

    @Test
    fun testSuccessfulLogin() = runTest {
        val expectedResponse = AuthResponse(
            accessToken = "mock-access-token",
            refreshToken = "mock-refresh-token",
            expiresIn = 900,
            user = UserDto(
                id = "user-123",
                email = "test@example.com",
                firstName = "Test",
                lastName = "User",
                role = "TEACHER",
                schoolId = null
            )
        )

        httpClient = createMockClient { request ->
            when (request.url.encodedPath) {
                "/auth/login" -> {
                    assertEquals(HttpMethod.Post, request.method)

                    respond(
                        content = json.encodeToString(expectedResponse),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }

                else -> error("Unexpected request: ${request.url.encodedPath}")
            }
        }

        authRepository = AuthRepository(httpClient, tokenStorage)

        val result = authRepository.login(
            email = "test@example.com",
            password = "password123"
        )

        assertTrue(result is AuthResult.Success)
        val authResponse = (result as AuthResult.Success).data
        assertEquals("mock-access-token", authResponse.accessToken)
        assertEquals("test@example.com", authResponse.user.email)
    }

    @Test
    fun testLoginWithInvalidCredentials() = runTest {
        val errorResponse = ErrorResponse("Invalid credentials")

        httpClient = createMockClient { request ->
            when (request.url.encodedPath) {
                "/auth/login" -> {
                    respond(
                        content = json.encodeToString(errorResponse),
                        status = HttpStatusCode.Unauthorized,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }

                else -> error("Unexpected request: ${request.url.encodedPath}")
            }
        }

        authRepository = AuthRepository(httpClient, tokenStorage)

        val result = authRepository.login(
            email = "test@example.com",
            password = "wrongpassword"
        )

        assertTrue(result is AuthResult.Error)
        val errorMessage = (result as AuthResult.Error).message
        assertTrue(errorMessage.contains("Invalid credentials"))
    }

    @Test
    fun testRegistrationWithShortPassword() = runTest {
        val errorResponse = ErrorResponse("Password must be at least 8 characters")

        httpClient = createMockClient { request ->
            when (request.url.encodedPath) {
                "/auth/register" -> {
                    respond(
                        content = json.encodeToString(errorResponse),
                        status = HttpStatusCode.BadRequest,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }

                else -> error("Unexpected request: ${request.url.encodedPath}")
            }
        }

        authRepository = AuthRepository(httpClient, tokenStorage)

        val result = authRepository.register(
            email = "test@example.com",
            password = "short",
            firstName = "Test",
            lastName = "User",
            schoolCode = null
        )

        assertTrue(result is AuthResult.Error)
        val errorMessage = (result as AuthResult.Error).message
        assertTrue(errorMessage.contains("Password must be at least 8 characters"))
    }

    @Test
    fun testNetworkError() = runTest {
        httpClient = createMockClient {
            error("Network error simulated")
        }

        authRepository = AuthRepository(httpClient, tokenStorage)

        val result = authRepository.login(
            email = "test@example.com",
            password = "password123"
        )

        assertTrue(result is AuthResult.Error)
        val errorMessage = (result as AuthResult.Error).message
        assertTrue(errorMessage.contains("error", ignoreCase = true))
    }
}
