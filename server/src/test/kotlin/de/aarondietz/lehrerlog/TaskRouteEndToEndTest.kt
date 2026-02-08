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
import java.time.ZoneOffset
import java.util.*
import kotlin.test.*

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

        val initialTargetsResponse = client.get("/api/tasks/${task.id}/targets") {
            header("Authorization", "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, initialTargetsResponse.status)
        val initialTargets = initialTargetsResponse.body<TaskTargetsResponse>()
        assertEquals(task.id, initialTargets.taskId)
        assertTrue(initialTargets.studentIds.contains(studentId!!.toString()))
        assertTrue(initialTargets.studentIds.none { it == studentTwoId!!.toString() })

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

        val targetsAfterRemoveResponse = client.get("/api/tasks/${task.id}/targets") {
            header("Authorization", "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, targetsAfterRemoveResponse.status)
        val targetsAfterRemove = targetsAfterRemoveResponse.body<TaskTargetsResponse>()
        assertTrue(targetsAfterRemove.studentIds.contains(studentId!!.toString()))
        assertTrue(targetsAfterRemove.studentIds.none { it == studentTwoId!!.toString() })

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

        val listSubmissions = client.get("/api/tasks/${task.id}/submissions") {
            header("Authorization", "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, listSubmissions.status)
        val submissions = listSubmissions.body<List<TaskSubmissionDto>>()
        assertTrue(submissions.any { it.id == submission.id })

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

        val inPersonSubmission = client.post("/api/tasks/${task.id}/submissions/in-person") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                CreateTaskSubmissionRequest(
                    studentId = studentTwoId!!.toString(),
                    submissionType = TaskSubmissionType.IN_PERSON,
                    grade = null,
                    note = "In person"
                )
            )
        }
        assertEquals(HttpStatusCode.Created, inPersonSubmission.status)

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

    @Test
    fun `task route validation errors`() = testApplication {
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
        val noSchoolToken = tokenService.generateAccessToken(
            userId = userId!!,
            email = "teacher@example.com",
            role = UserRole.TEACHER,
            schoolId = null
        )

        val missingQuery = client.get("/api/tasks") {
            header("Authorization", "Bearer $token")
        }
        assertEquals(HttpStatusCode.BadRequest, missingQuery.status)

        val invalidClassId = client.get("/api/tasks?classId=not-a-uuid") {
            header("Authorization", "Bearer $token")
        }
        assertEquals(HttpStatusCode.BadRequest, invalidClassId.status)

        val invalidStudentId = client.get("/api/tasks?studentId=not-a-uuid") {
            header("Authorization", "Bearer $token")
        }
        assertEquals(HttpStatusCode.BadRequest, invalidStudentId.status)

        val forbiddenSchool = client.get("/api/tasks?classId=${classId!!}") {
            header("Authorization", "Bearer $noSchoolToken")
        }
        assertEquals(HttpStatusCode.Forbidden, forbiddenSchool.status)

        val missingTitle = client.post("/api/tasks") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                CreateTaskRequest(
                    schoolClassId = classId!!.toString(),
                    title = "",
                    description = null,
                    dueAt = ""
                )
            )
        }
        assertEquals(HttpStatusCode.BadRequest, missingTitle.status)

        val task = client.post("/api/tasks") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                CreateTaskRequest(
                    schoolClassId = classId!!.toString(),
                    title = "Validation Task",
                    description = null,
                    dueAt = "2026-02-01"
                )
            )
        }.body<TaskDto>()

        val invalidUpdateId = client.put("/api/tasks/not-a-uuid") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(UpdateTaskRequest(title = "Title", description = null, dueAt = "2026-02-01"))
        }
        assertEquals(HttpStatusCode.BadRequest, invalidUpdateId.status)

        val invalidDelete = client.delete("/api/tasks/not-a-uuid") {
            header("Authorization", "Bearer $token")
        }
        assertEquals(HttpStatusCode.BadRequest, invalidDelete.status)

        val notFoundDelete = client.delete("/api/tasks/${UUID.randomUUID()}") {
            header("Authorization", "Bearer $token")
        }
        assertEquals(HttpStatusCode.NotFound, notFoundDelete.status)

        val invalidSummary = client.get("/api/tasks/not-a-uuid/summary") {
            header("Authorization", "Bearer $token")
        }
        assertEquals(HttpStatusCode.BadRequest, invalidSummary.status)

        val invalidSubmissions = client.get("/api/tasks/not-a-uuid/submissions") {
            header("Authorization", "Bearer $token")
        }
        assertEquals(HttpStatusCode.BadRequest, invalidSubmissions.status)

        val invalidTargets = client.post("/api/tasks/${task.id}/targets") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                TaskTargetsRequest(
                    addStudentIds = listOf("not-a-uuid"),
                    removeStudentIds = emptyList()
                )
            )
        }
        assertEquals(HttpStatusCode.BadRequest, invalidTargets.status)

        val invalidTargetTask = client.post("/api/tasks/not-a-uuid/targets") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                TaskTargetsRequest(
                    addStudentIds = emptyList(),
                    removeStudentIds = emptyList()
                )
            )
        }
        assertEquals(HttpStatusCode.BadRequest, invalidTargetTask.status)

        val invalidSubmissionStudent = client.post("/api/tasks/${task.id}/submissions") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                CreateTaskSubmissionRequest(
                    studentId = "not-a-uuid",
                    submissionType = TaskSubmissionType.FILE
                )
            )
        }
        assertEquals(HttpStatusCode.BadRequest, invalidSubmissionStudent.status)

        val invalidInPerson = client.post("/api/tasks/not-a-uuid/submissions/in-person") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                CreateTaskSubmissionRequest(
                    studentId = "not-a-uuid",
                    submissionType = TaskSubmissionType.IN_PERSON
                )
            )
        }
        assertEquals(HttpStatusCode.BadRequest, invalidInPerson.status)

        val invalidInPersonStudent = client.post("/api/tasks/${task.id}/submissions/in-person") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                CreateTaskSubmissionRequest(
                    studentId = "not-a-uuid",
                    submissionType = TaskSubmissionType.IN_PERSON
                )
            )
        }
        assertEquals(HttpStatusCode.BadRequest, invalidInPersonStudent.status)

        val invalidPatch = client.patch("/api/submissions/not-a-uuid") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(UpdateTaskSubmissionRequest())
        }
        assertEquals(HttpStatusCode.BadRequest, invalidPatch.status)
    }

    @Test
    fun `task route additional error branches`() = testApplication {
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

        val invalidClassId = client.post("/api/tasks") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                CreateTaskRequest(
                    schoolClassId = "not-a-uuid",
                    title = "Invalid Class",
                    description = null,
                    dueAt = "2026-02-07"
                )
            )
        }
        assertEquals(HttpStatusCode.BadRequest, invalidClassId.status)

        val missingClass = client.post("/api/tasks") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                CreateTaskRequest(
                    schoolClassId = UUID.randomUUID().toString(),
                    title = "Missing Class",
                    description = null,
                    dueAt = "2026-02-07"
                )
            )
        }
        assertEquals(HttpStatusCode.BadRequest, missingClass.status)

        val task = client.post("/api/tasks") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                CreateTaskRequest(
                    schoolClassId = classId!!.toString(),
                    title = "Error Branch Task",
                    description = null,
                    dueAt = "2026-02-07"
                )
            )
        }.body<TaskDto>()

        val blankUpdate = client.put("/api/tasks/${task.id}") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(UpdateTaskRequest(title = "", description = null, dueAt = ""))
        }
        assertEquals(HttpStatusCode.BadRequest, blankUpdate.status)

        val missingUpdate = client.put("/api/tasks/${UUID.randomUUID()}") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(UpdateTaskRequest(title = "Missing", description = null, dueAt = "2026-02-07"))
        }
        assertEquals(HttpStatusCode.NotFound, missingUpdate.status)

        val missingSummary = client.get("/api/tasks/${UUID.randomUUID()}/summary") {
            header("Authorization", "Bearer $token")
        }
        assertEquals(HttpStatusCode.NotFound, missingSummary.status)

        val invalidTargetStudent = client.post("/api/tasks/${task.id}/targets") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                TaskTargetsRequest(
                    addStudentIds = listOf(UUID.randomUUID().toString()),
                    removeStudentIds = emptyList()
                )
            )
        }
        assertEquals(HttpStatusCode.BadRequest, invalidTargetStudent.status)

        val missingTargetTask = client.post("/api/tasks/${UUID.randomUUID()}/targets") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(TaskTargetsRequest(addStudentIds = emptyList(), removeStudentIds = emptyList()))
        }
        assertEquals(HttpStatusCode.BadRequest, missingTargetTask.status)

        val notTargetedSubmission = client.post("/api/tasks/${task.id}/submissions") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                CreateTaskSubmissionRequest(
                    studentId = studentTwoId!!.toString(),
                    submissionType = TaskSubmissionType.FILE
                )
            )
        }
        assertEquals(HttpStatusCode.BadRequest, notTargetedSubmission.status)

        val notTargetedInPersonSubmission = client.post("/api/tasks/${task.id}/submissions/in-person") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                CreateTaskSubmissionRequest(
                    studentId = studentTwoId!!.toString(),
                    submissionType = TaskSubmissionType.IN_PERSON
                )
            )
        }
        assertEquals(HttpStatusCode.BadRequest, notTargetedInPersonSubmission.status)

        val submission = client.post("/api/tasks/${task.id}/submissions") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                CreateTaskSubmissionRequest(
                    studentId = studentId!!.toString(),
                    submissionType = TaskSubmissionType.FILE
                )
            )
        }.body<TaskSubmissionDto>()

        val duplicateSubmission = client.post("/api/tasks/${task.id}/submissions") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                CreateTaskSubmissionRequest(
                    studentId = studentId!!.toString(),
                    submissionType = TaskSubmissionType.FILE
                )
            )
        }
        assertEquals(HttpStatusCode.Conflict, duplicateSubmission.status)

        val emptyPatch = client.patch("/api/submissions/${submission.id}") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(UpdateTaskSubmissionRequest())
        }
        assertEquals(HttpStatusCode.BadRequest, emptyPatch.status)

        val missingPatch = client.patch("/api/submissions/${UUID.randomUUID()}") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(UpdateTaskSubmissionRequest(grade = 2.0))
        }
        assertEquals(HttpStatusCode.NotFound, missingPatch.status)
    }
}
