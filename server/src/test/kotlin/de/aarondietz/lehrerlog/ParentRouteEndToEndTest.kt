package de.aarondietz.lehrerlog

import de.aarondietz.lehrerlog.auth.AuthResponse
import de.aarondietz.lehrerlog.auth.TokenService
import de.aarondietz.lehrerlog.data.CreateTaskRequest
import de.aarondietz.lehrerlog.data.CreateTaskSubmissionRequest
import de.aarondietz.lehrerlog.data.ParentInviteCreateRequest
import de.aarondietz.lehrerlog.data.ParentInviteCreateResponse
import de.aarondietz.lehrerlog.data.ParentInviteRedeemRequest
import de.aarondietz.lehrerlog.data.ParentLinkDto
import de.aarondietz.lehrerlog.data.ParentLinkStatus
import de.aarondietz.lehrerlog.data.TaskDto
import de.aarondietz.lehrerlog.data.TaskSubmissionDto
import de.aarondietz.lehrerlog.data.TaskSubmissionType
import de.aarondietz.lehrerlog.db.DatabaseFactory
import de.aarondietz.lehrerlog.db.tables.ParentInvites
import de.aarondietz.lehrerlog.db.tables.ParentStudentLinks
import de.aarondietz.lehrerlog.db.tables.SchoolClasses
import de.aarondietz.lehrerlog.db.tables.Schools
import de.aarondietz.lehrerlog.db.tables.StudentClasses
import de.aarondietz.lehrerlog.db.tables.Students
import de.aarondietz.lehrerlog.db.tables.UserRole
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
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ParentRouteEndToEndTest {

    private val tokenService = TokenService()
    private var schoolId: UUID? = null
    private var teacherId: UUID? = null
    private var classId: UUID? = null
    private var studentId: UUID? = null
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

            teacherId = Users.insertAndGetId {
                it[email] = "$suffix.teacher@example.com"
                it[passwordHash] = "test"
                it[firstName] = "Teacher"
                it[lastName] = "User"
                it[role] = UserRole.TEACHER
                it[Users.schoolId] = schoolIdValue
                it[isActive] = true
            }.value

            classId = SchoolClasses.insertAndGetId {
                it[SchoolClasses.schoolId] = schoolIdValue
                it[name] = "1a"
                it[alternativeName] = null
                it[createdBy] = teacherId!!
            }.value

            studentId = Students.insertAndGetId {
                it[Students.schoolId] = schoolIdValue
                it[firstName] = "Student"
                it[lastName] = "One"
                it[createdBy] = teacherId!!
            }.value
            val studentIdValue = studentId!!
            val classIdValue = classId!!

            StudentClasses.insert {
                it[StudentClasses.studentId] = studentIdValue
                it[StudentClasses.schoolClassId] = classIdValue
                it[StudentClasses.validFrom] = OffsetDateTime.now(ZoneOffset.UTC)
                it[StudentClasses.validTill] = null
            }
        }
    }

    @AfterTest
    fun teardown() {
        transaction {
            studentId?.let { id ->
                ParentStudentLinks.deleteWhere { ParentStudentLinks.studentId eq id }
                ParentInvites.deleteWhere { ParentInvites.studentId eq id }
                Students.deleteWhere { Students.id eq id }
            }
            classId?.let { id -> SchoolClasses.deleteWhere { SchoolClasses.id eq id } }
            teacherId?.let { id -> Users.deleteWhere { Users.id eq id } }
            schoolId?.let { id -> Schools.deleteWhere { Schools.id eq id } }
        }
    }

    @Test
    fun `parent invite redeem and list assignments`() = testApplication {
        application { module() }

        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        val teacherToken = tokenService.generateAccessToken(
            userId = teacherId!!,
            email = "teacher@example.com",
            role = UserRole.TEACHER,
            schoolId = schoolId
        )

        val inviteResponse = client.post("/api/parent-invites") {
            header("Authorization", "Bearer $teacherToken")
            contentType(ContentType.Application.Json)
            setBody(ParentInviteCreateRequest(studentId = studentId!!.toString()))
        }
        assertEquals(HttpStatusCode.Created, inviteResponse.status)
        val invite = inviteResponse.body<ParentInviteCreateResponse>()
        assertNotNull(invite.code)

        val redeemResponse = client.post("/api/parent-invites/redeem") {
            contentType(ContentType.Application.Json)
            setBody(
                ParentInviteRedeemRequest(
                    code = invite.code,
                    email = "parent@example.com",
                    password = "ParentPassword1!",
                    firstName = "Parent",
                    lastName = "User"
                )
            )
        }
        assertEquals(HttpStatusCode.OK, redeemResponse.status)
        val auth = redeemResponse.body<AuthResponse>()
        assertEquals(UserRole.PARENT.name, auth.user.role)

        val taskResponse = client.post("/api/tasks") {
            header("Authorization", "Bearer $teacherToken")
            contentType(ContentType.Application.Json)
            setBody(
                CreateTaskRequest(
                    schoolClassId = classId!!.toString(),
                    title = "Homework",
                    description = "Worksheet",
                    dueAt = "2026-02-01"
                )
            )
        }
        assertEquals(HttpStatusCode.Created, taskResponse.status)
        val task = taskResponse.body<TaskDto>()

        val submissionResponse = client.post("/api/tasks/${task.id}/submissions") {
            header("Authorization", "Bearer $teacherToken")
            contentType(ContentType.Application.Json)
            setBody(
                CreateTaskSubmissionRequest(
                    studentId = studentId!!.toString(),
                    submissionType = TaskSubmissionType.FILE,
                    grade = 1.0,
                    note = "Good"
                )
            )
        }
        assertEquals(HttpStatusCode.Created, submissionResponse.status)
        val submission = submissionResponse.body<TaskSubmissionDto>()
        assertNotNull(submission.id)

        val parentToken = auth.accessToken
        val studentsResponse = client.get("/api/parent/students") {
            header("Authorization", "Bearer $parentToken")
        }
        assertEquals(HttpStatusCode.OK, studentsResponse.status)

        val assignmentsResponse = client.get("/api/parent/assignments") {
            header("Authorization", "Bearer $parentToken")
            url { parameters.append("studentId", studentId!!.toString()) }
        }
        assertEquals(HttpStatusCode.OK, assignmentsResponse.status)

        val submissionsResponse = client.get("/api/parent/submissions") {
            header("Authorization", "Bearer $parentToken")
            url { parameters.append("studentId", studentId!!.toString()) }
        }
        assertEquals(HttpStatusCode.OK, submissionsResponse.status)
    }

    @Test
    fun `teacher can list and revoke parent links`() = testApplication {
        application { module() }

        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        val teacherToken = tokenService.generateAccessToken(
            userId = teacherId!!,
            email = "teacher@example.com",
            role = UserRole.TEACHER,
            schoolId = schoolId
        )

        val inviteResponse = client.post("/api/parent-invites") {
            header("Authorization", "Bearer $teacherToken")
            contentType(ContentType.Application.Json)
            setBody(ParentInviteCreateRequest(studentId = studentId!!.toString()))
        }
        assertEquals(HttpStatusCode.Created, inviteResponse.status)
        val invite = inviteResponse.body<ParentInviteCreateResponse>()

        val redeemResponse = client.post("/api/parent-invites/redeem") {
            contentType(ContentType.Application.Json)
            setBody(
                ParentInviteRedeemRequest(
                    code = invite.code,
                    email = "parent.links@example.com",
                    password = "ParentPassword1!",
                    firstName = "Parent",
                    lastName = "Links"
                )
            )
        }
        assertEquals(HttpStatusCode.OK, redeemResponse.status)

        val linksResponse = client.get("/api/parent-links") {
            header("Authorization", "Bearer $teacherToken")
            url { parameters.append("studentId", studentId!!.toString()) }
        }
        assertEquals(HttpStatusCode.OK, linksResponse.status)
        val links = linksResponse.body<List<ParentLinkDto>>()
        assertEquals(ParentLinkStatus.ACTIVE, links.first().status)

        val revokeResponse = client.post("/api/parent-links/${links.first().id}/revoke") {
            header("Authorization", "Bearer $teacherToken")
        }
        assertEquals(HttpStatusCode.NoContent, revokeResponse.status)

        val linksAfter = client.get("/api/parent-links") {
            header("Authorization", "Bearer $teacherToken")
            url { parameters.append("studentId", studentId!!.toString()) }
        }.body<List<ParentLinkDto>>()
        assertEquals(ParentLinkStatus.REVOKED, linksAfter.first().status)
    }
}
