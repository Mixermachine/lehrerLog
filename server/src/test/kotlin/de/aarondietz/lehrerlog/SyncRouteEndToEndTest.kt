package de.aarondietz.lehrerlog

import de.aarondietz.lehrerlog.auth.TokenService
import de.aarondietz.lehrerlog.db.DatabaseFactory
import de.aarondietz.lehrerlog.db.tables.*
import de.aarondietz.lehrerlog.sync.PushChangesRequest
import de.aarondietz.lehrerlog.sync.PushChangesResponse
import de.aarondietz.lehrerlog.sync.SyncChangesResponse
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*
import kotlin.test.*

class SyncRouteEndToEndTest {

    companion object {
        private val TEST_PREFIX = TestPrefixGenerator.next()
        private val TEST_PREFIX_UPPER = TEST_PREFIX.uppercase()
        private var isInitialized = false
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val tokenService = TokenService()
    private var schoolId: UUID? = null
    private var userId: UUID? = null

    @BeforeTest
    fun setup() {
        if (!isInitialized) {
            DatabaseFactory.init()
            isInitialized = true
        }
        cleanupTestData()

        transaction {
            schoolId = Schools.insertAndGetId {
                it[name] = "$TEST_PREFIX School"
                it[code] = "${TEST_PREFIX_UPPER}_SCHOOL"
            }.value

            val schoolIdValue = schoolId!!
            userId = Users.insertAndGetId {
                it[email] = "$TEST_PREFIX.sync.user@example.com"
                it[passwordHash] = "test"
                it[firstName] = "Sync"
                it[lastName] = "User"
                it[role] = UserRole.TEACHER
                it[Users.schoolId] = schoolIdValue
                it[isActive] = true
            }.value
        }
    }

    @AfterTest
    fun teardown() {
        cleanupTestData()
    }

    private fun cleanupTestData() {
        transaction {
            val emailPattern = "$TEST_PREFIX.%@example.com"
            val schoolCodePattern = "${TEST_PREFIX_UPPER}_%"

            val schoolIds = Schools.selectAll()
                .where { Schools.code like schoolCodePattern }
                .map { it[Schools.id] }

            val userIds = Users.selectAll()
                .where { Users.email like emailPattern }
                .map { it[Users.id] }

            if (schoolIds.isNotEmpty()) {
                SyncLog.deleteWhere { SyncLog.schoolId inList schoolIds }
            }

            if (userIds.isNotEmpty()) {
                RefreshTokens.deleteWhere { RefreshTokens.userId inList userIds }
                Users.deleteWhere { Users.id inList userIds }
            }

            if (schoolIds.isNotEmpty()) {
                Schools.deleteWhere { Schools.id inList schoolIds }
            }
        }
    }

    @Test
    fun `sync routes handle missing since and empty changes`() = testApplication {
        application { module() }

        val client = createClient {
            install(ContentNegotiation) { json(json) }
        }

        val token = tokenService.generateAccessToken(
            userId = userId!!,
            email = "sync.user@example.com",
            role = UserRole.TEACHER,
            schoolId = schoolId
        )

        val missingSince = client.get("/api/sync/changes") {
            header("Authorization", "Bearer $token")
        }
        assertEquals(HttpStatusCode.BadRequest, missingSince.status)

        val changesResponse = client.get("/api/sync/changes?since=0") {
            header("Authorization", "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, changesResponse.status)
        val changes = changesResponse.body<SyncChangesResponse>()
        assertEquals(0L, changes.lastSyncId)
        assertTrue(changes.changes.isEmpty())

        val pushResponse = client.post("/api/sync/push") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(PushChangesRequest(changes = emptyList()))
        }
        assertEquals(HttpStatusCode.OK, pushResponse.status)
        val pushResult = pushResponse.body<PushChangesResponse>()
        assertEquals(0, pushResult.successCount)
        assertEquals(0, pushResult.failureCount)
    }

    @Test
    fun `sync routes reject unauthenticated and no school`() = testApplication {
        application { module() }

        val client = createClient {
            install(ContentNegotiation) { json(json) }
        }

        val noAuth = client.get("/api/sync/changes?since=0")
        assertEquals(HttpStatusCode.Unauthorized, noAuth.status)

        val noSchoolToken = tokenService.generateAccessToken(
            userId = userId!!,
            email = "sync.user@example.com",
            role = UserRole.TEACHER,
            schoolId = null
        )

        val noSchool = client.get("/api/sync/changes?since=0") {
            header("Authorization", "Bearer $noSchoolToken")
        }
        assertEquals(HttpStatusCode.BadRequest, noSchool.status)

        val pushNoSchool = client.post("/api/sync/push") {
            header("Authorization", "Bearer $noSchoolToken")
            contentType(ContentType.Application.Json)
            setBody(PushChangesRequest(changes = emptyList()))
        }
        assertEquals(HttpStatusCode.BadRequest, pushNoSchool.status)
    }
}
