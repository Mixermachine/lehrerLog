package de.aarondietz.lehrerlog

import de.aarondietz.lehrerlog.auth.TokenService
import de.aarondietz.lehrerlog.data.CreateTaskRequest
import de.aarondietz.lehrerlog.data.CreateTaskSubmissionRequest
import de.aarondietz.lehrerlog.data.TaskDto
import de.aarondietz.lehrerlog.data.TaskSubmissionDto
import de.aarondietz.lehrerlog.data.TaskSubmissionType
import de.aarondietz.lehrerlog.data.TaskTargetsRequest
import de.aarondietz.lehrerlog.data.UpdateTaskRequest
import de.aarondietz.lehrerlog.data.UpdateTaskSubmissionRequest
import de.aarondietz.lehrerlog.db.DatabaseFactory
import de.aarondietz.lehrerlog.db.tables.SchoolClasses
import de.aarondietz.lehrerlog.db.tables.Schools
import de.aarondietz.lehrerlog.db.tables.StudentClasses
import de.aarondietz.lehrerlog.db.tables.Students
import de.aarondietz.lehrerlog.db.tables.Users
import de.aarondietz.lehrerlog.db.tables.UserRole
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class TaskRouteEndToEndTest {

    private val tokenService = TokenService()
    private var schoolId: UUID? = null
    private var userId: UUID? = null
    private var classId: UUID? = null
    private var studentId: UUID? = null
    private var studentTwoId: UUID? = null
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
                it[role] = UserRole.TEACHER
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
                it[firstName] = "Student"
                it[lastName] = "One"
                it[createdBy] = userId!!
            }.value

            studentTwoId = Students.insertAndGetId {
                it[Students.schoolId] = schoolIdValue
                it[firstName] = "Student"
                it[lastName] = "Two"
                it[createdBy] = userId!!
            }.value

            val studentIdValue = studentId!!
            val classIdValue = classId!!
            StudentClasses.insert {
                it[StudentClasses.studentId] = studentIdValue
                it[StudentClasses.schoolClassId] = classIdValue
                it[validFrom] = OffsetDateTime.now(ZoneOffset.UTC)
                it[validTill] = null
            }
        }
    }

    @AfterTest
    fun teardown() {
        transaction {
            studentTwoId?.let { id ->
                StudentClasses.deleteWhere { StudentClasses.studentId eq id }
                Students.deleteWhere { Students.id eq id }
            }
            studentId?.let { id ->
                StudentClasses.deleteWhere { StudentClasses.studentId eq id }
                Students.deleteWhere { Students.id eq id }
            }
            classId?.let { id -> SchoolClasses.deleteWhere { SchoolClasses.id eq id } }
            userId?.let { id -> Users.deleteWhere { Users.id eq id } }
            schoolId?.let { id -> Schools.deleteWhere { Schools.id eq id } }
        }
    }

    @Test
    fun `create task and list by class and student`() = testApplication {
        application { module() }

        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        val token = tokenService.generateAccessToken(
            userId = userId!!,
            email = "teacher@example.com",
            role = UserRole.TEACHER,
            schoolId = schoolId
        )

        val createTaskResponse = client.post("/api/tasks") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                CreateTaskRequest(
                    schoolClassId = classId!!.toString(),
                    title = "Math Homework",
                    description = "Page 12",
                    dueAt = "2026-02-01"
                )
            )
        }
        assertEquals(HttpStatusCode.Created, createTaskResponse.status)
        val task = createTaskResponse.body<TaskDto>()
        assertNotNull(task.id)

        val byClassResponse = client.get("/api/tasks?classId=${classId!!}") {
            header("Authorization", "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, byClassResponse.status)
        val tasksByClass = byClassResponse.body<List<TaskDto>>()
        assertEquals(1, tasksByClass.size)
        assertEquals(task.id, tasksByClass.first().id)

        val byStudentResponse = client.get("/api/tasks?studentId=${studentId!!}") {
            header("Authorization", "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, byStudentResponse.status)
        val tasksByStudent = byStudentResponse.body<List<TaskDto>>()
        assertEquals(1, tasksByStudent.size)
        assertEquals(task.id, tasksByStudent.first().id)
    }

    @Test
    fun `update targets add and remove student`() = testApplication {
        application { module() }

        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        val token = tokenService.generateAccessToken(
            userId = userId!!,
            email = "teacher@example.com",
            role = UserRole.TEACHER,
            schoolId = schoolId
        )

        val createTaskResponse = client.post("/api/tasks") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                CreateTaskRequest(
                    schoolClassId = classId!!.toString(),
                    title = "History",
                    description = "Chapter 2",
                    dueAt = "2026-02-02"
                )
            )
        }
        assertEquals(HttpStatusCode.Created, createTaskResponse.status)
        val task = createTaskResponse.body<TaskDto>()

        val addTargets = client.post("/api/tasks/${task.id}/targets") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                TaskTargetsRequest(
                    addStudentIds = listOf(studentTwoId!!.toString()),
                    removeStudentIds = emptyList()
                )
            )
        }
        assertEquals(HttpStatusCode.NoContent, addTargets.status)

        val bySecondStudent = client.get("/api/tasks?studentId=${studentTwoId!!}") {
            header("Authorization", "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, bySecondStudent.status)
        assertEquals(1, bySecondStudent.body<List<TaskDto>>().size)

        val removeTargets = client.post("/api/tasks/${task.id}/targets") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                TaskTargetsRequest(
                    addStudentIds = emptyList(),
                    removeStudentIds = listOf(studentTwoId!!.toString())
                )
            )
        }
        assertEquals(HttpStatusCode.NoContent, removeTargets.status)

        val bySecondStudentAfter = client.get("/api/tasks?studentId=${studentTwoId!!}") {
            header("Authorization", "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, bySecondStudentAfter.status)
        assertEquals(0, bySecondStudentAfter.body<List<TaskDto>>().size)
    }

    @Test
    fun `update submission and delete task`() = testApplication {
        application { module() }

        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        val token = tokenService.generateAccessToken(
            userId = userId!!,
            email = "teacher@example.com",
            role = UserRole.TEACHER,
            schoolId = schoolId
        )

        val createTaskResponse = client.post("/api/tasks") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                CreateTaskRequest(
                    schoolClassId = classId!!.toString(),
                    title = "Science",
                    description = "Lab",
                    dueAt = "2000-01-01"
                )
            )
        }
        assertEquals(HttpStatusCode.Created, createTaskResponse.status)
        val task = createTaskResponse.body<TaskDto>()

        val updateTask = client.put("/api/tasks/${task.id}") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(UpdateTaskRequest(title = "Science Updated", description = "Lab 2", dueAt = "2026-02-04"))
        }
        assertEquals(HttpStatusCode.OK, updateTask.status)

        val submissionResponse = client.post("/api/tasks/${task.id}/submissions") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                CreateTaskSubmissionRequest(
                    studentId = studentId!!.toString(),
                    submissionType = TaskSubmissionType.FILE,
                    grade = null,
                    note = null
                )
            )
        }
        assertEquals(HttpStatusCode.Created, submissionResponse.status)
        val submission = submissionResponse.body<TaskSubmissionDto>()

        val updateSubmission = client.patch("/api/submissions/${submission.id}") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                UpdateTaskSubmissionRequest(
                    grade = 1.0,
                    note = "Well done"
                )
            )
        }
        assertEquals(HttpStatusCode.OK, updateSubmission.status)

        val deleteTask = client.delete("/api/tasks/${task.id}") {
            header("Authorization", "Bearer $token")
        }
        assertEquals(HttpStatusCode.NoContent, deleteTask.status)

        val byClassAfter = client.get("/api/tasks?classId=${classId!!}") {
            header("Authorization", "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, byClassAfter.status)
        assertEquals(0, byClassAfter.body<List<TaskDto>>().size)
    }
}
