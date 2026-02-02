package de.aarondietz.lehrerlog

import de.aarondietz.lehrerlog.auth.*
import de.aarondietz.lehrerlog.db.DatabaseFactory
import de.aarondietz.lehrerlog.db.tables.*
import de.aarondietz.lehrerlog.db.tables.UserRole
import de.aarondietz.lehrerlog.schools.SchoolCatalogEntry
import de.aarondietz.lehrerlog.schools.SchoolCatalogService
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.test.*

class AuthServiceTest {
    private val json = Json { encodeDefaults = true }
    private val passwordService = PasswordService()
    private val tokenService = TokenService()
    private val testPrefix = "testing${(10000..99999).random()}"
    private val schoolCodeA = "${testPrefix.uppercase()}_A"
    private val schoolCodeB = "${testPrefix.uppercase()}_B"
    private val createdUserIds = mutableListOf<UUID>()
    private val createdSchoolIds = mutableListOf<UUID>()
    private var catalogPath: Path? = null
    private var initialized = false
    private lateinit var authService: AuthService

    @BeforeTest
    fun setup() {
        if (!initialized) {
            DatabaseFactory.init()
            initialized = true
        }

        val tempDir = Files.createTempDirectory("lehrerlog-auth-$testPrefix")
        val path = tempDir.resolve("catalog.json")
        catalogPath = path

        val catalogEntries = listOf(
            SchoolCatalogEntry(code = schoolCodeA, name = "$testPrefix School A"),
            SchoolCatalogEntry(code = schoolCodeB, name = "$testPrefix School B")
        )
        Files.writeString(path, json.encodeToString(catalogEntries))

        val catalogService = SchoolCatalogService(path, "http://localhost/ignored")
        catalogService.initialize()

        authService = AuthService(passwordService, tokenService, catalogService)
    }

    @AfterTest
    fun teardown() {
        transaction {
            createdUserIds.forEach { id ->
                Users.deleteWhere { Users.id eq id }
            }
            createdSchoolIds.forEach { id ->
                StorageSubscriptions.deleteWhere {
                    (StorageSubscriptions.ownerType eq StorageOwnerType.SCHOOL.name) and
                            (StorageSubscriptions.ownerId eq id)
                }
                StorageUsage.deleteWhere {
                    (StorageUsage.ownerType eq StorageOwnerType.SCHOOL.name) and
                            (StorageUsage.ownerId eq id)
                }
                Schools.deleteWhere { Schools.id eq id }
            }
        }
        createdUserIds.clear()
        createdSchoolIds.clear()

        catalogPath?.let { path ->
            runCatching {
                Files.deleteIfExists(path)
                Files.deleteIfExists(path.parent)
            }
        }
    }

    @Test
    fun `register creates school and login succeeds`() {
        val email = "$testPrefix.teacher@example.com"
        val password = "Password!12345"

        val (tokens, user) = authService.register(
            RegisterRequest(
                email = email,
                password = password,
                firstName = "Test",
                lastName = "Teacher",
                schoolCode = schoolCodeA
            ),
            deviceInfo = "test-device"
        )

        createdUserIds.add(user.id)
        user.schoolId?.let { createdSchoolIds.add(it) }

        assertEquals(UserRole.TEACHER, user.role)
        val schoolId = user.schoolId
        assertNotNull(schoolId)
        assertTrue(tokens.accessToken.isNotBlank())
        assertTrue(tokens.refreshToken.isNotBlank())

        transaction {
            val schoolRow = Schools.selectAll().where { Schools.id eq schoolId }.firstOrNull()
            assertNotNull(schoolRow)

            val subscription = StorageSubscriptions.selectAll()
                .where {
                    (StorageSubscriptions.ownerType eq StorageOwnerType.SCHOOL.name) and
                            (StorageSubscriptions.ownerId eq schoolId)
                }
                .firstOrNull()
            assertNotNull(subscription)

            val usage = StorageUsage.selectAll()
                .where {
                    (StorageUsage.ownerType eq StorageOwnerType.SCHOOL.name) and
                            (StorageUsage.ownerId eq schoolId)
                }
                .firstOrNull()
            assertNotNull(usage)
        }

        val (loginTokens, loginUser) = authService.login(
            LoginRequest(email = email, password = password),
            deviceInfo = "test-device"
        )

        assertEquals(user.id, loginUser.id)
        assertTrue(loginTokens.accessToken.isNotBlank())
        assertTrue(loginTokens.refreshToken.isNotBlank())
        assertNotEquals(tokens.refreshToken, loginTokens.refreshToken)
    }

    @Test
    fun `register rejects duplicate email`() {
        val email = "$testPrefix.dup@example.com"
        val password = "Password!12345"

        val (_, user) = authService.register(
            RegisterRequest(
                email = email,
                password = password,
                firstName = "Test",
                lastName = "User",
                schoolCode = schoolCodeA
            ),
            deviceInfo = null
        )
        createdUserIds.add(user.id)
        user.schoolId?.let { createdSchoolIds.add(it) }

        val error = assertFailsWith<AuthException> {
            authService.register(
                RegisterRequest(
                    email = email,
                    password = password,
                    firstName = "Test",
                    lastName = "User",
                    schoolCode = schoolCodeA
                ),
                deviceInfo = null
            )
        }
        assertTrue(error.message?.contains("Email already registered") == true)
    }

    @Test
    fun `refresh revokes old token and logout works`() {
        val email = "$testPrefix.refresh@example.com"
        val password = "Password!12345"

        val (tokens, user) = authService.register(
            RegisterRequest(
                email = email,
                password = password,
                firstName = "Refresh",
                lastName = "User",
                schoolCode = schoolCodeA
            ),
            deviceInfo = "refresh-device"
        )
        createdUserIds.add(user.id)
        user.schoolId?.let { createdSchoolIds.add(it) }

        val refreshed = authService.refresh(tokens.refreshToken, deviceInfo = "refresh-device")
        assertNotEquals(tokens.refreshToken, refreshed.refreshToken)
        assertTrue(refreshed.accessToken.isNotBlank())

        transaction {
            val tokenRows = RefreshTokens.selectAll()
                .where { RefreshTokens.userId eq user.id }
                .toList()
            val activeTokens = tokenRows.count { it[RefreshTokens.revokedAt] == null }
            val revokedTokens = tokenRows.count { it[RefreshTokens.revokedAt] != null }
            assertEquals(1, activeTokens)
            assertTrue(revokedTokens >= 1)
        }

        assertTrue(authService.logout(refreshed.refreshToken))
        assertFalse(authService.logout(refreshed.refreshToken))

        assertFailsWith<AuthException> {
            authService.refresh(tokens.refreshToken, deviceInfo = "refresh-device")
        }
    }

    @Test
    fun `joinSchool associates user and rejects other school`() {
        val schoolAId = transaction {
            Schools.insertAndGetId {
                it[name] = "$testPrefix Join A"
                it[code] = schoolCodeA
            }.value
        }
        val schoolBId = transaction {
            Schools.insertAndGetId {
                it[name] = "$testPrefix Join B"
                it[code] = schoolCodeB
            }.value
        }
        createdSchoolIds.addAll(listOf(schoolAId, schoolBId))

        val email = "$testPrefix.join@example.com"
        val password = "Password!12345"
        val (_, user) = authService.register(
            RegisterRequest(
                email = email,
                password = password,
                firstName = "Join",
                lastName = "User",
                schoolCode = null
            ),
            deviceInfo = null
        )
        createdUserIds.add(user.id)

        assertNull(user.schoolId)

        val (_, joinedUser) = authService.joinSchool(user.id, schoolCodeA, deviceInfo = null)
        assertEquals(schoolAId, joinedUser.schoolId)

        val error = assertFailsWith<AuthException> {
            authService.joinSchool(user.id, schoolCodeB, deviceInfo = null)
        }
        assertTrue(error.message?.contains("different school") == true)
    }
}
