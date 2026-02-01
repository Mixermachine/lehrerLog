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
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*
import kotlin.test.*

class LatePolicyRouteEndToEndTest {

    private val tokenService = TokenService()
    private var schoolId: UUID? = null
    private var userId: UUID? = null
    private var classId: UUID? = null
    private var studentId: UUID? = null
    private var taskId: UUID? = null
    private var submissionId: UUID? = null
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
                it[email] = "$suffix.teacher@example.com"
                it[passwordHash] = "test"
                it[firstName] = "Test"
                it[lastName] = "Teacher"
                it[role] = de.aarondietz.lehrerlog.db.tables.UserRole.TEACHER
                it[Users.schoolId] = schoolIdValue
                it[isActive] = true
            }.value

            classId = SchoolClasses.insertAndGetId {
                it[SchoolClasses.schoolId] = schoolIdValue
                it[name] = "2a"
                it[alternativeName] = null
                it[createdBy] = userId!!
            }.value

            studentId = Students.insertAndGetId {
                it[Students.schoolId] = schoolIdValue
                it[firstName] = "Lara"
                it[lastName] = "Late"
                it[createdBy] = userId!!
            }.value

            TeacherLatePolicy.insertIgnore {
                it[TeacherLatePolicy.teacherId] = userId!!
                it[TeacherLatePolicy.threshold] = 1
            }
        }
    }

    @AfterTest
    fun teardown() {
        transaction {
            submissionId?.let { id -> TaskSubmissions.deleteWhere { TaskSubmissions.id eq id } }
            taskId?.let { id -> TaskTargets.deleteWhere { TaskTargets.taskId eq id } }
            taskId?.let { id -> Tasks.deleteWhere { Tasks.id eq id } }
            studentId?.let { id ->
                StudentClasses.deleteWhere { StudentClasses.studentId eq id }
                StudentLateStats.deleteWhere { StudentLateStats.studentId eq id }
                PunishmentRecords.deleteWhere { PunishmentRecords.studentId eq id }
            }
            studentId?.let { id -> Students.deleteWhere { Students.id eq id } }
            classId?.let { id -> SchoolClasses.deleteWhere { SchoolClasses.id eq id } }
            userId?.let { id ->
                PunishmentRecords.deleteWhere { PunishmentRecords.teacherId eq id }
                LatePeriods.deleteWhere { LatePeriods.teacherId eq id }
                TeacherLatePolicy.deleteWhere { TeacherLatePolicy.teacherId eq id }
                SyncLog.deleteWhere { SyncLog.userId eq id }
                Users.deleteWhere { Users.id eq id }
            }
            schoolId?.let { id -> Schools.deleteWhere { Schools.id eq id } }
        }
    }

    @Test
    fun `late decision creates stats and punishment record`() = testApplication {
        application { module() }

        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        val token = tokenService.generateAccessToken(
            userId = userId!!,
            email = "teacher@example.com",
            role = de.aarondietz.lehrerlog.db.tables.UserRole.TEACHER,
            schoolId = schoolId
        )

        val linkResponse = client.post("/api/classes/${classId}/students/${studentId}") {
            header("Authorization", "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, linkResponse.status)

        val taskResponse = client.post("/api/tasks") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                CreateTaskRequest(
                    schoolClassId = classId!!.toString(),
                    title = "Late Assignment",
                    description = "Past due",
                    dueAt = "2025-01-01"
                )
            )
        }
        assertEquals(HttpStatusCode.Created, taskResponse.status)
        val task = taskResponse.body<TaskDto>()
        taskId = UUID.fromString(task.id)

        val submissionResponse = client.post("/api/tasks/${task.id}/submissions") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                CreateTaskSubmissionRequest(
                    studentId = studentId!!.toString(),
                    submissionType = TaskSubmissionType.FILE
                )
            )
        }
        assertEquals(HttpStatusCode.Created, submissionResponse.status)
        val submission = submissionResponse.body<TaskSubmissionDto>()
        submissionId = UUID.fromString(submission.id)
        assertEquals(LateStatus.LATE_UNDECIDED, submission.lateStatus)

        val decisionResponse = client.patch("/api/submissions/${submission.id}") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(UpdateTaskSubmissionRequest(lateStatus = LateStatus.LATE_PUNISH))
        }
        assertEquals(HttpStatusCode.OK, decisionResponse.status)

        val periods = client.get("/api/late-periods") {
            header("Authorization", "Bearer $token")
        }.body<List<LatePeriodDto>>()

        val activePeriod = periods.firstOrNull { it.isActive } ?: periods.first()
        val periodId = activePeriod.id

        val statsResponse = client.get("/api/late-stats/students") {
            header("Authorization", "Bearer $token")
            url { parameters.append("periodId", periodId) }
        }
        assertEquals(HttpStatusCode.OK, statsResponse.status)
        val stats = statsResponse.body<List<LateStudentStatsDto>>()
        assertTrue(stats.any { it.studentId == studentId!!.toString() })

        val punishmentsResponse = client.get("/api/punishments") {
            header("Authorization", "Bearer $token")
            url {
                parameters.append("studentId", studentId!!.toString())
                parameters.append("periodId", periodId)
            }
        }
        assertEquals(HttpStatusCode.OK, punishmentsResponse.status)
        val punishments = punishmentsResponse.body<List<PunishmentRecordDto>>()
        assertEquals(1, punishments.size)
        assertNotNull(punishments.first().id)

        val resolveResponse = client.post("/api/punishments/resolve") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                ResolvePunishmentRequest(
                    studentId = studentId!!.toString(),
                    periodId = periodId,
                    note = "Resolved"
                )
            )
        }
        assertEquals(HttpStatusCode.OK, resolveResponse.status)
    }
}
