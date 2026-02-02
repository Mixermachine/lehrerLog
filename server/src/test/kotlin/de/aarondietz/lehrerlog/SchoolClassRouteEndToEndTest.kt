package de.aarondietz.lehrerlog

import de.aarondietz.lehrerlog.auth.TokenService
import de.aarondietz.lehrerlog.data.CreateSchoolClassRequest
import de.aarondietz.lehrerlog.data.SchoolClassDto
import de.aarondietz.lehrerlog.data.UpdateSchoolClassRequest
import de.aarondietz.lehrerlog.db.DatabaseFactory
import de.aarondietz.lehrerlog.db.tables.*
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

class SchoolClassRouteEndToEndTest {

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
                it[email] = "$TEST_PREFIX.class.user@example.com"
                it[passwordHash] = "test"
                it[firstName] = "Test"
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

            if (userIds.isNotEmpty()) {
                RefreshTokens.deleteWhere { RefreshTokens.userId inList userIds }
            }

            val studentIds = if (schoolIds.isNotEmpty()) {
                Students.selectAll()
                    .where { Students.schoolId inList schoolIds }
                    .map { it[Students.id] }
            } else {
                emptyList()
            }

            val classIds = if (schoolIds.isNotEmpty()) {
                SchoolClasses.selectAll()
                    .where { SchoolClasses.schoolId inList schoolIds }
                    .map { it[SchoolClasses.id] }
            } else {
                emptyList()
            }

            if (schoolIds.isNotEmpty()) {
                SyncLog.deleteWhere { SyncLog.schoolId inList schoolIds }
            }

            if (studentIds.isNotEmpty()) {
                StudentClasses.deleteWhere { StudentClasses.studentId inList studentIds }
                Students.deleteWhere { Students.id inList studentIds }
            }

            if (classIds.isNotEmpty()) {
                StudentClasses.deleteWhere { StudentClasses.schoolClassId inList classIds }
                SchoolClasses.deleteWhere { SchoolClasses.id inList classIds }
            }

            if (userIds.isNotEmpty()) {
                Users.deleteWhere { Users.id inList userIds }
            }

            if (schoolIds.isNotEmpty()) {
                Schools.deleteWhere { Schools.id inList schoolIds }
            }
        }
    }

    @Test
    fun `create update delete class via routes`() = testApplication {
        application { module() }

        val client = createClient {
            install(ContentNegotiation) { json(json) }
        }

        val token = tokenService.generateAccessToken(
            userId = userId!!,
            email = "class.user@example.com",
            role = UserRole.TEACHER,
            schoolId = schoolId
        )

        val createResponse = client.post("/api/classes") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(CreateSchoolClassRequest(name = "1A", alternativeName = "Alpha"))
        }
        assertEquals(HttpStatusCode.Created, createResponse.status)
        val created = createResponse.body<SchoolClassDto>()

        val listResponse = client.get("/api/classes") {
            header("Authorization", "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, listResponse.status)
        val classes = listResponse.body<List<SchoolClassDto>>()
        assertTrue(classes.any { it.id == created.id })

        val fetchResponse = client.get("/api/classes/${created.id}") {
            header("Authorization", "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, fetchResponse.status)

        val updateResponse = client.put("/api/classes/${created.id}") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                UpdateSchoolClassRequest(
                    name = "1B",
                    alternativeName = "Beta",
                    version = created.version
                )
            )
        }
        assertEquals(HttpStatusCode.OK, updateResponse.status)
        val updated = updateResponse.body<SchoolClassDto>()
        assertEquals("1B", updated.name)

        val invalidIdResponse = client.get("/api/classes/not-a-uuid") {
            header("Authorization", "Bearer $token")
        }
        assertEquals(HttpStatusCode.BadRequest, invalidIdResponse.status)

        val deleteResponse = client.delete("/api/classes/${created.id}") {
            header("Authorization", "Bearer $token")
        }
        assertEquals(HttpStatusCode.NoContent, deleteResponse.status)

        val fetchAfterDelete = client.get("/api/classes/${created.id}") {
            header("Authorization", "Bearer $token")
        }
        assertEquals(HttpStatusCode.NotFound, fetchAfterDelete.status)
    }

    @Test
    fun `class student links and conflicts`() = testApplication {
        application { module() }

        val client = createClient {
            install(ContentNegotiation) { json(json) }
        }

        val token = tokenService.generateAccessToken(
            userId = userId!!,
            email = "class.user@example.com",
            role = UserRole.TEACHER,
            schoolId = schoolId
        )

        val missingName = client.post("/api/classes") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(CreateSchoolClassRequest(name = "", alternativeName = null))
        }
        assertEquals(HttpStatusCode.BadRequest, missingName.status)

        val created = client.post("/api/classes") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(CreateSchoolClassRequest(name = "2A", alternativeName = null))
        }.body<SchoolClassDto>()

        val conflictUpdate = client.put("/api/classes/${created.id}") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                UpdateSchoolClassRequest(
                    name = "2B",
                    alternativeName = null,
                    version = created.version + 1
                )
            )
        }
        assertEquals(HttpStatusCode.Conflict, conflictUpdate.status)

        val schoolIdValue = schoolId!!
        val studentId = transaction {
            Students.insertAndGetId {
                it[Students.schoolId] = schoolIdValue
                it[firstName] = "Link"
                it[lastName] = "Student"
                it[createdBy] = userId!!
            }.value
        }

        val linkResponse = client.post("/api/classes/${created.id}/students/$studentId") {
            header("Authorization", "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, linkResponse.status)

        val invalidLinkClass = client.post("/api/classes/not-a-uuid/students/$studentId") {
            header("Authorization", "Bearer $token")
        }
        assertEquals(HttpStatusCode.BadRequest, invalidLinkClass.status)

        val missingClassLink = client.post("/api/classes/${UUID.randomUUID()}/students/$studentId") {
            header("Authorization", "Bearer $token")
        }
        assertEquals(HttpStatusCode.NotFound, missingClassLink.status)

        val unlinkResponse = client.delete("/api/classes/${created.id}/students/$studentId") {
            header("Authorization", "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, unlinkResponse.status)

        val invalidUnlinkClass = client.delete("/api/classes/not-a-uuid/students/$studentId") {
            header("Authorization", "Bearer $token")
        }
        assertEquals(HttpStatusCode.BadRequest, invalidUnlinkClass.status)

        val missingClassUnlink = client.delete("/api/classes/${UUID.randomUUID()}/students/$studentId") {
            header("Authorization", "Bearer $token")
        }
        assertEquals(HttpStatusCode.NotFound, missingClassUnlink.status)

        val invalidStudent = client.post("/api/classes/${created.id}/students/not-a-uuid") {
            header("Authorization", "Bearer $token")
        }
        assertEquals(HttpStatusCode.BadRequest, invalidStudent.status)
    }

    @Test
    fun `class route forbidden and not found cases`() = testApplication {
        application { module() }

        val client = createClient {
            install(ContentNegotiation) { json(json) }
        }

        val token = tokenService.generateAccessToken(
            userId = userId!!,
            email = "class.user@example.com",
            role = UserRole.TEACHER,
            schoolId = schoolId
        )
        val noSchoolToken = tokenService.generateAccessToken(
            userId = userId!!,
            email = "class.user@example.com",
            role = UserRole.TEACHER,
            schoolId = null
        )

        val forbiddenList = client.get("/api/classes") {
            header("Authorization", "Bearer $noSchoolToken")
        }
        assertEquals(HttpStatusCode.Forbidden, forbiddenList.status)

        val forbiddenCreate = client.post("/api/classes") {
            header("Authorization", "Bearer $noSchoolToken")
            contentType(ContentType.Application.Json)
            setBody(CreateSchoolClassRequest(name = "No School", alternativeName = null))
        }
        assertEquals(HttpStatusCode.Forbidden, forbiddenCreate.status)

        val forbiddenUpdate = client.put("/api/classes/${UUID.randomUUID()}") {
            header("Authorization", "Bearer $noSchoolToken")
            contentType(ContentType.Application.Json)
            setBody(UpdateSchoolClassRequest(name = "No School", alternativeName = null, version = 1))
        }
        assertEquals(HttpStatusCode.Forbidden, forbiddenUpdate.status)

        val forbiddenDelete = client.delete("/api/classes/${UUID.randomUUID()}") {
            header("Authorization", "Bearer $noSchoolToken")
        }
        assertEquals(HttpStatusCode.Forbidden, forbiddenDelete.status)

        val forbiddenLink = client.post("/api/classes/${UUID.randomUUID()}/students/${UUID.randomUUID()}") {
            header("Authorization", "Bearer $noSchoolToken")
        }
        assertEquals(HttpStatusCode.Forbidden, forbiddenLink.status)

        val forbiddenUnlink = client.delete("/api/classes/${UUID.randomUUID()}/students/${UUID.randomUUID()}") {
            header("Authorization", "Bearer $noSchoolToken")
        }
        assertEquals(HttpStatusCode.Forbidden, forbiddenUnlink.status)

        val blankUpdate = client.put("/api/classes/${UUID.randomUUID()}") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(UpdateSchoolClassRequest(name = "", alternativeName = null, version = 1))
        }
        assertEquals(HttpStatusCode.BadRequest, blankUpdate.status)

        val missingUpdate = client.put("/api/classes/${UUID.randomUUID()}") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(UpdateSchoolClassRequest(name = "Missing", alternativeName = null, version = 1))
        }
        assertEquals(HttpStatusCode.NotFound, missingUpdate.status)

        val missingDelete = client.delete("/api/classes/${UUID.randomUUID()}") {
            header("Authorization", "Bearer $token")
        }
        assertEquals(HttpStatusCode.NotFound, missingDelete.status)
    }
}
