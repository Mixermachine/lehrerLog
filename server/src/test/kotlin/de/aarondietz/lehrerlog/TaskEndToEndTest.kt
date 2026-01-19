package de.aarondietz.lehrerlog

import de.aarondietz.lehrerlog.auth.TokenService
import de.aarondietz.lehrerlog.data.CreateTaskRequest
import de.aarondietz.lehrerlog.data.CreateTaskSubmissionRequest
import de.aarondietz.lehrerlog.data.TaskDto
import de.aarondietz.lehrerlog.data.TaskSubmissionSummaryDto
import de.aarondietz.lehrerlog.db.DatabaseFactory
import de.aarondietz.lehrerlog.db.tables.SchoolClasses
import de.aarondietz.lehrerlog.db.tables.Schools
import de.aarondietz.lehrerlog.db.tables.StudentClasses
import de.aarondietz.lehrerlog.db.tables.Students
import de.aarondietz.lehrerlog.db.tables.Users
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.time.OffsetDateTime
import java.util.UUID
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TaskEndToEndTest {

    private val tokenService = TokenService()
    private var schoolId: UUID? = null
    private var userId: UUID? = null
    private var classId: UUID? = null
    private var studentId: UUID? = null

    @BeforeTest
    fun setup() {
        listOf(
            Paths.get("build", "lehrerlog.mv.db"),
            Paths.get("build", "lehrerlog.trace.db"),
            Paths.get("server", "build", "lehrerlog.mv.db"),
            Paths.get("server", "build", "lehrerlog.trace.db")
        ).forEach { Files.deleteIfExists(it) }
        DatabaseFactory.init()
        transaction {
            val suffix = "testing${(10000..99999).random()}"
            schoolId = Schools.insertAndGetId {
                it[name] = "$suffix School"
                it[code] = "${suffix.uppercase()}_SCHOOL"
            }.value
            val schoolIdValue = schoolId!!

            userId = Users.insertAndGetId {
                it[email] = "$suffix.user@example.com"
                it[passwordHash] = "test"
                it[firstName] = "Test"
                it[lastName] = "User"
                it[role] = de.aarondietz.lehrerlog.db.tables.UserRole.TEACHER
                it[Users.schoolId] = schoolIdValue
                it[isActive] = true
            }.value

            classId = SchoolClasses.insertAndGetId {
                it[SchoolClasses.schoolId] = schoolIdValue
                it[name] = "1a"
                it[alternativeName] = null
                it[createdBy] = userId!!
            }.value

            studentId = Students.insertAndGetId {
                it[Students.schoolId] = schoolIdValue
                it[firstName] = "Max"
                it[lastName] = "Mustermann"
                it[createdBy] = userId!!
            }.value
            val studentIdValue = studentId!!

            StudentClasses.insert {
                it[StudentClasses.studentId] = studentIdValue
                it[StudentClasses.schoolClassId] = classId!!
                it[StudentClasses.validFrom] = OffsetDateTime.now().minusDays(1)
                it[StudentClasses.validTill] = null
            }
        }
    }

    @AfterTest
    fun teardown() {
        transaction {
            studentId?.let { id -> Students.deleteWhere { Students.id eq id } }
            classId?.let { id -> SchoolClasses.deleteWhere { SchoolClasses.id eq id } }
            userId?.let { id -> Users.deleteWhere { Users.id eq id } }
            schoolId?.let { id -> Schools.deleteWhere { Schools.id eq id } }
        }
    }

    @Test
    fun `create task and record submission summary`() = testApplication {
        application { module() }

        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        val token = tokenService.generateAccessToken(
            userId = userId!!,
            email = "test@example.com",
            role = de.aarondietz.lehrerlog.db.tables.UserRole.TEACHER,
            schoolId = schoolId
        )

        val createResponse = client.post("/api/tasks") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                CreateTaskRequest(
                    schoolClassId = classId!!.toString(),
                    title = "Math Homework",
                    description = "Page 10",
                    dueAt = "2026-01-20"
                )
            )
        }

        assertEquals(HttpStatusCode.Created, createResponse.status)
        val task = createResponse.body<TaskDto>()
        assertNotNull(task.id)

        val tasksResponse = client.get("/api/tasks?classId=${classId}") {
            header("Authorization", "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, tasksResponse.status)

        val submissionResponse = client.post("/api/tasks/${task.id}/submissions") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(CreateTaskSubmissionRequest(studentId!!.toString()))
        }
        assertEquals(HttpStatusCode.Created, submissionResponse.status)

        val summaryResponse = client.get("/api/tasks/${task.id}/summary") {
            header("Authorization", "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, summaryResponse.status)
        val summary = summaryResponse.body<TaskSubmissionSummaryDto>()
        assertEquals(1, summary.totalStudents)
        assertEquals(1, summary.submittedStudents)
    }
}
