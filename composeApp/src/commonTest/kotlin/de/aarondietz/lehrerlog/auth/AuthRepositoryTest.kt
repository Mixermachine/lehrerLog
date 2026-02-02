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
        val authResponse = result.data
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
        val errorMessage = result.message
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
        val authResponse = result.data
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
        val errorMessage = result.message
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
        val errorMessage = result.message
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
        val errorMessage = result.message
        assertTrue(errorMessage.contains("error", ignoreCase = true))
    }

    @Test
    fun testRefreshTokenSuccess() = runTest {
        val tokenResponse = TokenResponse(
            accessToken = "new-access",
            refreshToken = "new-refresh",
            expiresIn = 900
        )
        tokenStorage.saveRefreshToken("old-refresh")

        httpClient = createMockClient { request ->
            when (request.url.encodedPath) {
                "/auth/refresh" -> respond(
                    content = json.encodeToString(tokenResponse),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )

                else -> error("Unexpected request: ${request.url.encodedPath}")
            }
        }

        authRepository = AuthRepository(httpClient, tokenStorage)
        val result = authRepository.refreshToken()

        assertTrue(result is AuthResult.Success)
        assertEquals("new-access", tokenStorage.getAccessToken())
        assertEquals("new-refresh", tokenStorage.getRefreshToken())
    }

    @Test
    fun testRefreshTokenMissing() = runTest {
        httpClient = createMockClient {
            error("Refresh should not be called")
        }
        authRepository = AuthRepository(httpClient, tokenStorage)

        val result = authRepository.refreshToken()
        assertTrue(result is AuthResult.Error)
        assertTrue(result.message.contains("refresh", ignoreCase = true))
    }

    @Test
    fun testRedeemParentInviteSuccess() = runTest {
        val expectedResponse = AuthResponse(
            accessToken = "invite-access",
            refreshToken = "invite-refresh",
            expiresIn = 900,
            user = UserDto(
                id = "parent-123",
                email = "parent@example.com",
                firstName = "Pat",
                lastName = "Guardian",
                role = "PARENT",
                schoolId = null
            )
        )

        httpClient = createMockClient { request ->
            when (request.url.encodedPath) {
                "/api/parent-invites/redeem" -> respond(
                    content = json.encodeToString(expectedResponse),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )

                else -> error("Unexpected request: ${request.url.encodedPath}")
            }
        }

        authRepository = AuthRepository(httpClient, tokenStorage)
        val result = authRepository.redeemParentInvite(
            code = "ABCD1234",
            email = "parent@example.com",
            password = "ParentPass123!",
            firstName = "Pat",
            lastName = "Guardian"
        )

        assertTrue(result is AuthResult.Success)
        assertEquals("invite-access", tokenStorage.getAccessToken())
    }

    @Test
    fun testLogoutClearsTokens() = runTest {
        tokenStorage.saveAccessToken("access")
        tokenStorage.saveRefreshToken("refresh")

        httpClient = createMockClient { request ->
            when (request.url.encodedPath) {
                "/auth/logout" -> respond(
                    content = "{}",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )

                else -> error("Unexpected request: ${request.url.encodedPath}")
            }
        }

        authRepository = AuthRepository(httpClient, tokenStorage)
        val result = authRepository.logout()

        assertTrue(result is AuthResult.Success)
        assertNull(tokenStorage.getAccessToken())
        assertNull(tokenStorage.getRefreshToken())
    }

    @Test
    fun testJoinSchoolRefreshesOnUnauthorized() = runTest {
        val authResponse = AuthResponse(
            accessToken = "joined-access",
            refreshToken = "joined-refresh",
            expiresIn = 900,
            user = UserDto(
                id = "user-join",
                email = "join@example.com",
                firstName = "Join",
                lastName = "School",
                role = "TEACHER",
                schoolId = "school-123"
            )
        )
        val refreshed = TokenResponse(
            accessToken = "refreshed-access",
            refreshToken = "refreshed-refresh",
            expiresIn = 900
        )

        var joinAttempts = 0
        tokenStorage.saveAccessToken("expired-access")
        tokenStorage.saveRefreshToken("refresh-token")

        httpClient = createMockClient { request ->
            when (request.url.encodedPath) {
                "/auth/join-school" -> {
                    joinAttempts += 1
                    if (joinAttempts == 1) {
                        respond(
                            content = json.encodeToString(ErrorResponse("Unauthorized")),
                            status = HttpStatusCode.Unauthorized,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                    } else {
                        respond(
                            content = json.encodeToString(authResponse),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                    }
                }

                "/auth/refresh" -> respond(
                    content = json.encodeToString(refreshed),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )

                else -> error("Unexpected request: ${request.url.encodedPath}")
            }
        }

        authRepository = AuthRepository(httpClient, tokenStorage)
        val result = authRepository.joinSchool("SCHOOLCODE")

        assertTrue(result is AuthResult.Success)
        assertEquals("joined-access", tokenStorage.getAccessToken())
    }

    @Test
    fun testGetCurrentUserRefreshesOnUnauthorized() = runTest {
        val user = UserDto(
            id = "user-1",
            email = "teacher@example.com",
            firstName = "Alex",
            lastName = "Teacher",
            role = "TEACHER",
            schoolId = "school-123"
        )
        val refreshed = TokenResponse(
            accessToken = "refreshed-access",
            refreshToken = "refreshed-refresh",
            expiresIn = 900
        )

        var attempts = 0
        tokenStorage.saveAccessToken("expired-access")
        tokenStorage.saveRefreshToken("refresh-token")

        httpClient = createMockClient { request ->
            when (request.url.encodedPath) {
                "/auth/me" -> {
                    attempts += 1
                    if (attempts == 1) {
                        respond(
                            content = json.encodeToString(ErrorResponse("Unauthorized")),
                            status = HttpStatusCode.Unauthorized,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                    } else {
                        respond(
                            content = json.encodeToString(user),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                    }
                }

                "/auth/refresh" -> respond(
                    content = json.encodeToString(refreshed),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )

                else -> error("Unexpected request: ${request.url.encodedPath}")
            }
        }

        authRepository = AuthRepository(httpClient, tokenStorage)
        val result = authRepository.getCurrentUser()

        assertTrue(result is AuthResult.Success)
        assertEquals("teacher@example.com", result.data.email)
    }
}
