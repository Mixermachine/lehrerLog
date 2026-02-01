package de.aarondietz.lehrerlog

import de.aarondietz.lehrerlog.auth.ErrorResponse
import de.aarondietz.lehrerlog.auth.PasswordService
import de.aarondietz.lehrerlog.auth.TokenService
import de.aarondietz.lehrerlog.db.DatabaseFactory
import de.aarondietz.lehrerlog.db.tables.*
import de.aarondietz.lehrerlog.routes.AuthResponseDto
import de.aarondietz.lehrerlog.routes.LoginRequestDto
import de.aarondietz.lehrerlog.routes.RegisterRequestDto
import de.aarondietz.lehrerlog.schools.SchoolCatalogEntry
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.test.*

/**
 * End-to-end tests for authentication endpoints (registration and login).
 * These tests are designed to work with both in-memory and persistent databases.
 *
 * Test data strategy:
 * - All test data uses a unique prefix: testing{random5digits}
 * - Emails: {prefix}.{name}@example.com
 * - School codes: {PREFIX}_SCHOOL
 * - Cleanup runs before and after each test to ensure idempotency
 */
class AuthEndToEndTest {

    companion object {
        private var testSchoolId: UUID? = null
        private var catalogPath: Path? = null
        private var isInitialized = false

        // Generate unique prefix for this test run (e.g., "testing93821")
        private val TEST_PREFIX = TestPrefixGenerator.next()

        // Uppercase version for school codes
        private val TEST_PREFIX_UPPER = TEST_PREFIX.uppercase()
    }

    @BeforeTest
    fun setup() {
        if (!isInitialized) {
            DatabaseFactory.init()
            isInitialized = true
            println("Test run using prefix: $TEST_PREFIX")
        }

        // Clean up any leftover test data from previous runs with this prefix
        cleanupTestData()

        // Create a fresh test school with unique prefix
        testSchoolId = transaction {
            Schools.insert {
                it[name] = "$TEST_PREFIX Test School"
                it[code] = "${TEST_PREFIX_UPPER}_SCHOOL"
            } get Schools.id
        }.value

        if (catalogPath == null) {
            val tempDir = Files.createTempDirectory("school-catalog-test")
            catalogPath = tempDir.resolve("schools.json")
        }

        val catalogEntry = SchoolCatalogEntry(
            code = testSchoolCode(),
            name = "$TEST_PREFIX Test School",
            city = "Test City",
            postcode = "00000",
            state = "Test State"
        )
        val json = Json { prettyPrint = true }
        Files.writeString(catalogPath!!, json.encodeToString(listOf(catalogEntry)))
        System.setProperty("SCHOOL_CATALOG_PATH", catalogPath!!.toString())
        System.setProperty("AUTH_RATE_LIMIT", "1000")
        System.setProperty("AUTH_RATE_LIMIT_REFILL_SECONDS", "60")
    }

    @AfterTest
    fun teardown() {
        // Clean up after each test for good hygiene
        cleanupTestData()
        System.clearProperty("SCHOOL_CATALOG_PATH")
        System.clearProperty("AUTH_RATE_LIMIT")
        System.clearProperty("AUTH_RATE_LIMIT_REFILL_SECONDS")
        catalogPath?.let { Files.deleteIfExists(it) }
    }

    /**
     * Removes all test data from the database for this test run.
     * Uses the unique TEST_PREFIX to identify data belonging to this test run.
     * This is idempotent - safe to run multiple times.
     */
    private fun cleanupTestData() {
        transaction {
            val emailPattern = "$TEST_PREFIX.%@example.com"
            val schoolCodePattern = "${TEST_PREFIX_UPPER}_%"

            // Delete in reverse order of foreign key dependencies

            // 1. Refresh tokens for test users
            val testUserIds = Users.selectAll()
                .where { Users.email like emailPattern }
                .map { it[Users.id] }

            if (testUserIds.isNotEmpty()) {
                RefreshTokens.deleteWhere { RefreshTokens.userId inList testUserIds }
            }

            // 2. Get test school IDs
            val testSchoolIds = Schools.selectAll()
                .where { Schools.code like schoolCodePattern }
                .map { it[Schools.id] }

            // 3. Students from test schools
            if (testSchoolIds.isNotEmpty()) {
                Students.deleteWhere { Students.schoolId inList testSchoolIds }
            }

            // 4. Classes from test schools
            if (testSchoolIds.isNotEmpty()) {
                SchoolClasses.deleteWhere { SchoolClasses.schoolId inList testSchoolIds }
            }

            // 5. Test users
            Users.deleteWhere { Users.email like emailPattern }

            // 6. Test schools
            Schools.deleteWhere { Schools.code like schoolCodePattern }
        }
    }

    /**
     * Generates a unique test email with the test prefix.
     * Format: {testPrefix}.{name}.{timestamp}@example.com
     * Example: testing93821.john.doe.1736723456789@example.com
     */
    private fun uniqueTestEmail(name: String): String {
        return "$TEST_PREFIX.$name.${System.currentTimeMillis()}@example.com"
    }

    /**
     * Returns the test school code with the test prefix.
     * Example: TESTING93821_SCHOOL
     */
    private fun testSchoolCode(): String {
        return "${TEST_PREFIX_UPPER}_SCHOOL"
    }

    private fun validPassword(): String {
        return "SecurePass123!"
    }

    private fun wrongPassword(): String {
        return "WrongPass123!"
    }

    @Test
    fun `test registration without school code fails`() = testApplication {
        application { module() }

        val testClient = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        val email = uniqueTestEmail("john.doe")

        val response = testClient.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(
                RegisterRequestDto(
                    email = email,
                    password = validPassword(),
                    firstName = "John",
                    lastName = "Doe",
                    schoolCode = null
                )
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val errorResponse = response.body<ErrorResponse>()
        assertTrue(errorResponse.error.contains("School code is required"))
    }

    @Test
    fun `test registration with weak password fails`() = testApplication {
        application { module() }

        val testClient = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        val email = uniqueTestEmail("weak.password")

        val response = testClient.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(
                RegisterRequestDto(
                    email = email,
                    password = "weakpass",
                    firstName = "Weak",
                    lastName = "Password",
                    schoolCode = testSchoolCode()
                )
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val errorResponse = response.body<ErrorResponse>()
        assertTrue(errorResponse.error.contains("Password must be at least 12 characters"))
    }

    @Test
    fun `test successful registration with valid school code`() = testApplication {
        application { module() }

        val testClient = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        val email = uniqueTestEmail("jane.smith")

        val response = testClient.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(
                RegisterRequestDto(
                    email = email,
                    password = validPassword(),
                    firstName = "Jane",
                    lastName = "Smith",
                    schoolCode = testSchoolCode()
                )
            )
        }

        assertEquals(HttpStatusCode.Created, response.status)

        val authResponse = response.body<AuthResponseDto>()
        assertEquals(email, authResponse.user.email)
        assertEquals(testSchoolId.toString(), authResponse.user.schoolId)
    }

    @Test
    fun `test registration with invalid school code`() = testApplication {
        application { module() }

        val testClient = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        val email = uniqueTestEmail("invalid.school")

        val response = testClient.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(
                RegisterRequestDto(
                    email = email,
                    password = validPassword(),
                    firstName = "Test",
                    lastName = "User",
                    schoolCode = "INVALID_CODE"
                )
            )
        }

        assertEquals(HttpStatusCode.Conflict, response.status)

        val errorResponse = response.body<ErrorResponse>()
        assertTrue(errorResponse.error.contains("Invalid school code"))
    }

    @Test
    fun `test auth rate limit triggers after repeated login attempts`() = testApplication {
        System.setProperty("AUTH_RATE_LIMIT", "5")
        System.setProperty("AUTH_RATE_LIMIT_REFILL_SECONDS", "60")
        application { module() }

        val testClient = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        val request = LoginRequestDto(
            email = "rate.limit@example.com",
            password = validPassword()
        )

        repeat(5) {
            val response = testClient.post("/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            assertNotEquals(HttpStatusCode.TooManyRequests, response.status)
        }

        val limitedResponse = testClient.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        assertEquals(HttpStatusCode.TooManyRequests, limitedResponse.status)
    }

    @Test
    fun `test registration with duplicate email`() = testApplication {
        application { module() }

        val testClient = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        val email = uniqueTestEmail("duplicate")

        val registerRequest = RegisterRequestDto(
            email = email,
            password = validPassword(),
            firstName = "Duplicate",
            lastName = "User",
            schoolCode = testSchoolCode()
        )

        // First registration
        val firstResponse = testClient.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(registerRequest)
        }
        assertEquals(HttpStatusCode.Created, firstResponse.status)

        // Second registration with same email
        val secondResponse = testClient.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(registerRequest)
        }
        assertEquals(HttpStatusCode.Conflict, secondResponse.status)

        val errorResponse = secondResponse.body<ErrorResponse>()
        assertTrue(errorResponse.error.contains("Email already registered"))
    }

    @Test
    fun `test registration with short password`() = testApplication {
        application { module() }

        val testClient = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        val email = uniqueTestEmail("short.password")

        val response = testClient.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(
                RegisterRequestDto(
                    email = email,
                    password = "short",  // Less than 12 characters
                    firstName = "Short",
                    lastName = "Password",
                    schoolCode = testSchoolCode()
                )
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)

        val errorResponse = response.body<ErrorResponse>()
        assertTrue(errorResponse.error.contains("Password must be at least 12 characters"))
    }

    @Test
    fun `test registration with missing fields`() = testApplication {
        application { module() }

        val testClient = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        val response = testClient.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(
                RegisterRequestDto(
                    email = "",
                    password = validPassword(),
                    firstName = "",
                    lastName = "User",
                    schoolCode = testSchoolCode()
                )
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `test successful login`() = testApplication {
        application { module() }

        val testClient = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        val email = uniqueTestEmail("login")

        // First, register a user
        testClient.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(
                RegisterRequestDto(
                    email = email,
                    password = validPassword(),
                    firstName = "Login",
                    lastName = "Test",
                    schoolCode = testSchoolCode()
                )
            )
        }

        // Now try to login
        val loginResponse = testClient.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(
                LoginRequestDto(
                    email = email,
                    password = validPassword()
                )
            )
        }

        assertEquals(HttpStatusCode.OK, loginResponse.status)

        val authResponse = loginResponse.body<AuthResponseDto>()
        assertNotNull(authResponse.accessToken)
        assertNotNull(authResponse.refreshToken)
        assertEquals(email, authResponse.user.email)
        assertEquals("Login", authResponse.user.firstName)
        assertEquals("Test", authResponse.user.lastName)
    }

    @Test
    fun `test login with wrong password`() = testApplication {
        application { module() }

        val testClient = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        val email = uniqueTestEmail("wrong.password")

        // Register a user
        testClient.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(
                RegisterRequestDto(
                    email = email,
                    password = validPassword(),
                    firstName = "Wrong",
                    lastName = "Password",
                    schoolCode = testSchoolCode()
                )
            )
        }

        // Try to login with wrong password
        val loginResponse = testClient.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(
                LoginRequestDto(
                    email = email,
                    password = wrongPassword()
                )
            )
        }

        assertEquals(HttpStatusCode.Unauthorized, loginResponse.status)

        val errorResponse = loginResponse.body<ErrorResponse>()
        assertTrue(errorResponse.error.contains("Invalid credentials"))
    }

    @Test
    fun `test login with non-existent email`() = testApplication {
        application { module() }

        val testClient = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        val email = uniqueTestEmail("nonexistent")

        val loginResponse = testClient.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(
                LoginRequestDto(
                    email = email,
                    password = validPassword()
                )
            )
        }

        assertEquals(HttpStatusCode.Unauthorized, loginResponse.status)

        val errorResponse = loginResponse.body<ErrorResponse>()
        assertTrue(errorResponse.error.contains("Invalid credentials"))
    }

    @Test
    fun `test login with empty credentials`() = testApplication {
        application { module() }

        val testClient = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        val loginResponse = testClient.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(
                LoginRequestDto(
                    email = "",
                    password = ""
                )
            )
        }

        assertEquals(HttpStatusCode.BadRequest, loginResponse.status)

        val errorResponse = loginResponse.body<ErrorResponse>()
        assertTrue(errorResponse.error.contains("Email and password are required"))
    }

    @Test
    fun `test access token is valid JWT`() = testApplication {
        application { module() }

        val testClient = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        val passwordService = PasswordService()
        val tokenService = TokenService()

        val email = uniqueTestEmail("jwt")

        // Register and get tokens
        val registerResponse = testClient.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(
                RegisterRequestDto(
                    email = email,
                    password = validPassword(),
                    firstName = "JWT",
                    lastName = "Test",
                    schoolCode = testSchoolCode()
                )
            )
        }

        val authResponse = registerResponse.body<AuthResponseDto>()

        // Verify the access token can be parsed and is valid
        val verifier = tokenService.getJwtVerifier()
        val decodedJWT = verifier.verify(authResponse.accessToken)

        assertEquals(email, decodedJWT.getClaim("email").asString())
        assertEquals("TEACHER", decodedJWT.getClaim("role").asString())
    }

    @Test
    fun `test complete registration and login flow`() = testApplication {
        application { module() }

        val testClient = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        val email = uniqueTestEmail("complete.flow")

        // Step 1: Register a new user
        val registerResponse = testClient.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(
                RegisterRequestDto(
                    email = email,
                    password = validPassword(),
                    firstName = "Complete",
                    lastName = "Flow",
                    schoolCode = testSchoolCode()
                )
            )
        }

        assertEquals(HttpStatusCode.Created, registerResponse.status)
        val registerAuth = registerResponse.body<AuthResponseDto>()
        assertNotNull(registerAuth.accessToken)
        assertNotNull(registerAuth.refreshToken)

        // Step 2: Login with the same credentials
        val loginResponse = testClient.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(
                LoginRequestDto(
                    email = email,
                    password = validPassword()
                )
            )
        }

        assertEquals(HttpStatusCode.OK, loginResponse.status)
        val loginAuth = loginResponse.body<AuthResponseDto>()
        assertNotNull(loginAuth.accessToken)
        assertNotNull(loginAuth.refreshToken)

        // Refresh tokens should be different (new session)
        // Note: Access tokens might be the same if generated within the same second with identical claims
        assertNotEquals(registerAuth.refreshToken, loginAuth.refreshToken)

        // User data should match
        assertEquals(registerAuth.user.id, loginAuth.user.id)
        assertEquals(registerAuth.user.email, loginAuth.user.email)
        assertEquals(registerAuth.user.schoolId, loginAuth.user.schoolId)
    }
}
