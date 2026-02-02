package de.aarondietz.lehrerlog

import de.aarondietz.lehrerlog.auth.TokenService
import de.aarondietz.lehrerlog.data.CreateStudentRequest
import de.aarondietz.lehrerlog.data.StudentDto
import de.aarondietz.lehrerlog.data.UpdateStudentRequest
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

class StudentRouteEndToEndTest {

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
                it[email] = "$TEST_PREFIX.student.user@example.com"
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
    fun `create update delete student via routes`() = testApplication {
        application { module() }

        val client = createClient {
            install(ContentNegotiation) { json(json) }
        }

        val token = tokenService.generateAccessToken(
            userId = userId!!,
            email = "student.user@example.com",
            role = UserRole.TEACHER,
            schoolId = schoolId
        )

        val createResponse = client.post("/api/students") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(CreateStudentRequest(firstName = "Alice", lastName = "Anderson"))
        }
        assertEquals(HttpStatusCode.Created, createResponse.status)
        val created = createResponse.body<StudentDto>()

        val listResponse = client.get("/api/students") {
            header("Authorization", "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, listResponse.status)
        val students = listResponse.body<List<StudentDto>>()
        assertTrue(students.any { it.id == created.id })

        val updateResponse = client.put("/api/students/${created.id}") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                UpdateStudentRequest(
                    firstName = "Alicia",
                    lastName = "Anderson",
                    version = created.version
                )
            )
        }
        assertEquals(HttpStatusCode.OK, updateResponse.status)
        val updated = updateResponse.body<StudentDto>()
        assertEquals("Alicia", updated.firstName)

        val invalidIdResponse = client.get("/api/students/not-a-uuid") {
            header("Authorization", "Bearer $token")
        }
        assertEquals(HttpStatusCode.BadRequest, invalidIdResponse.status)

        val deleteResponse = client.delete("/api/students/${created.id}") {
            header("Authorization", "Bearer $token")
        }
        assertEquals(HttpStatusCode.NoContent, deleteResponse.status)

        val fetchAfterDelete = client.get("/api/students/${created.id}") {
            header("Authorization", "Bearer $token")
        }
        assertEquals(HttpStatusCode.NotFound, fetchAfterDelete.status)
    }

    @Test
    fun `student route validation and conflicts`() = testApplication {
        application { module() }

        val client = createClient {
            install(ContentNegotiation) { json(json) }
        }

        val token = tokenService.generateAccessToken(
            userId = userId!!,
            email = "student.user@example.com",
            role = UserRole.TEACHER,
            schoolId = schoolId
        )
        val noSchoolToken = tokenService.generateAccessToken(
            userId = userId!!,
            email = "student.user@example.com",
            role = UserRole.TEACHER,
            schoolId = null
        )

        val forbiddenList = client.get("/api/students") {
            header("Authorization", "Bearer $noSchoolToken")
        }
        assertEquals(HttpStatusCode.Forbidden, forbiddenList.status)

        val missingNames = client.post("/api/students") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(CreateStudentRequest(firstName = "", lastName = ""))
        }
        assertEquals(HttpStatusCode.BadRequest, missingNames.status)

        val created = client.post("/api/students") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(CreateStudentRequest(firstName = "Bob", lastName = "Builder"))
        }.body<StudentDto>()

        val conflictUpdate = client.put("/api/students/${created.id}") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                UpdateStudentRequest(
                    firstName = "Bob",
                    lastName = "Builder",
                    version = created.version + 1
                )
            )
        }
        assertEquals(HttpStatusCode.Conflict, conflictUpdate.status)

        val invalidUpdate = client.put("/api/students/not-a-uuid") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(UpdateStudentRequest(firstName = "X", lastName = "Y", version = 1))
        }
        assertEquals(HttpStatusCode.BadRequest, invalidUpdate.status)

        val invalidDelete = client.delete("/api/students/not-a-uuid") {
            header("Authorization", "Bearer $token")
        }
        assertEquals(HttpStatusCode.BadRequest, invalidDelete.status)
    }

    @Test
    fun `student route forbidden and not found cases`() = testApplication {
        application { module() }

        val client = createClient {
            install(ContentNegotiation) { json(json) }
        }

        val token = tokenService.generateAccessToken(
            userId = userId!!,
            email = "student.user@example.com",
            role = UserRole.TEACHER,
            schoolId = schoolId
        )
        val noSchoolToken = tokenService.generateAccessToken(
            userId = userId!!,
            email = "student.user@example.com",
            role = UserRole.TEACHER,
            schoolId = null
        )

        val forbiddenGet = client.get("/api/students/${UUID.randomUUID()}") {
            header("Authorization", "Bearer $noSchoolToken")
        }
        assertEquals(HttpStatusCode.Forbidden, forbiddenGet.status)

        val forbiddenUpdate = client.put("/api/students/${UUID.randomUUID()}") {
            header("Authorization", "Bearer $noSchoolToken")
            contentType(ContentType.Application.Json)
            setBody(UpdateStudentRequest(firstName = "No", lastName = "School", version = 1))
        }
        assertEquals(HttpStatusCode.Forbidden, forbiddenUpdate.status)

        val forbiddenDelete = client.delete("/api/students/${UUID.randomUUID()}") {
            header("Authorization", "Bearer $noSchoolToken")
        }
        assertEquals(HttpStatusCode.Forbidden, forbiddenDelete.status)

        val created = client.post("/api/students") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(CreateStudentRequest(firstName = "Blank", lastName = "Names"))
        }.body<StudentDto>()

        val blankUpdate = client.put("/api/students/${created.id}") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(UpdateStudentRequest(firstName = "", lastName = "", version = created.version))
        }
        assertEquals(HttpStatusCode.BadRequest, blankUpdate.status)

        val missingUpdate = client.put("/api/students/${UUID.randomUUID()}") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(UpdateStudentRequest(firstName = "Missing", lastName = "Student", version = 1))
        }
        assertEquals(HttpStatusCode.NotFound, missingUpdate.status)

        val missingDelete = client.delete("/api/students/${UUID.randomUUID()}") {
            header("Authorization", "Bearer $token")
        }
        assertEquals(HttpStatusCode.NotFound, missingDelete.status)
    }
}
