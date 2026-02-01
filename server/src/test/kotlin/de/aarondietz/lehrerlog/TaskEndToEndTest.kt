package de.aarondietz.lehrerlog

import de.aarondietz.lehrerlog.auth.TokenService
import de.aarondietz.lehrerlog.data.*
import de.aarondietz.lehrerlog.db.DatabaseFactory
import de.aarondietz.lehrerlog.db.tables.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import java.util.*
import kotlin.test.*

class TaskEndToEndTest {

    private val tokenService = TokenService()
    private var schoolId: UUID? = null
    private var userId: UUID? = null
    private var classId: UUID? = null
    private var studentId: UUID? = null
    private var nonTargetStudentId: UUID? = null
    private var isInitialized = false

    @BeforeTest
    fun setup() {
        if (!isInitialized) {
            DatabaseFactory.init()
            isInitialized = true
        }
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

            nonTargetStudentId = Students.insertAndGetId {
                it[Students.schoolId] = schoolIdValue
                it[firstName] = "Erika"
                it[lastName] = "Mustermann"
                it[createdBy] = userId!!
            }.value

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
            nonTargetStudentId?.let { id -> Students.deleteWhere { Students.id eq id } }
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

        val updateResponse = client.put("/api/tasks/${task.id}") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                UpdateTaskRequest(
                    title = "Math Homework Updated",
                    description = "Page 12",
                    dueAt = "2026-01-21"
                )
            )
        }
        assertEquals(HttpStatusCode.OK, updateResponse.status)

        val addTargetsResponse = client.post("/api/tasks/${task.id}/targets") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                TaskTargetsRequest(
                    addStudentIds = listOf(nonTargetStudentId!!.toString())
                )
            )
        }
        assertEquals(HttpStatusCode.NoContent, addTargetsResponse.status)

        val submissionResponse = client.post("/api/tasks/${task.id}/submissions") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                CreateTaskSubmissionRequest(
                    studentId = studentId!!.toString(),
                    submissionType = TaskSubmissionType.FILE,
                    grade = 1.3,
                    note = "Well done"
                )
            )
        }
        assertEquals(HttpStatusCode.Created, submissionResponse.status)
        val submission = submissionResponse.body<TaskSubmissionDto>()

        val rejected = client.post("/api/tasks/${task.id}/submissions") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                CreateTaskSubmissionRequest(
                    studentId = nonTargetStudentId!!.toString(),
                    submissionType = TaskSubmissionType.FILE
                )
            )
        }
        assertEquals(HttpStatusCode.Created, rejected.status)

        val duplicate = client.post("/api/tasks/${task.id}/submissions") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                CreateTaskSubmissionRequest(
                    studentId = studentId!!.toString(),
                    submissionType = TaskSubmissionType.FILE
                )
            )
        }
        assertEquals(HttpStatusCode.Conflict, duplicate.status)

        val updateSubmissionResponse = client.patch("/api/submissions/${submission.id}") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                UpdateTaskSubmissionRequest(
                    grade = 1.0,
                    note = "Updated note"
                )
            )
        }
        assertEquals(HttpStatusCode.OK, updateSubmissionResponse.status)

        val summaryResponse = client.get("/api/tasks/${task.id}/summary") {
            header("Authorization", "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, summaryResponse.status)
        val summary = summaryResponse.body<TaskSubmissionSummaryDto>()
        assertEquals(2, summary.totalStudents)
        assertEquals(2, summary.submittedStudents)

        val deleteResponse = client.delete("/api/tasks/${task.id}") {
            header("Authorization", "Bearer $token")
        }
        assertEquals(HttpStatusCode.NoContent, deleteResponse.status)
    }
}
