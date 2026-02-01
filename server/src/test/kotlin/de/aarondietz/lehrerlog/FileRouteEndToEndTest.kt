package de.aarondietz.lehrerlog

import de.aarondietz.lehrerlog.auth.TokenService
import de.aarondietz.lehrerlog.data.*
import de.aarondietz.lehrerlog.db.DatabaseFactory
import de.aarondietz.lehrerlog.db.tables.*
import de.aarondietz.lehrerlog.db.tables.StorageOwnerType
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*
import kotlin.test.*

class FileRouteEndToEndTest {

    private val tokenService = TokenService()
    private var schoolId: UUID? = null
    private var userId: UUID? = null
    private var classId: UUID? = null
    private var studentId: UUID? = null
    private var classIdTwo: UUID? = null
    private var studentIdTwo: UUID? = null
    private var parentId: UUID? = null
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
            val studentIdValue = studentId!!
            val classIdValue = classId!!

            StudentClasses.insert {
                it[StudentClasses.studentId] = studentIdValue
                it[StudentClasses.schoolClassId] = classIdValue
                it[validFrom] = OffsetDateTime.now(ZoneOffset.UTC)
                it[validTill] = null
            }

            val defaultPlanId = UUID.fromString("00000000-0000-0000-0000-000000000001")
            StoragePlans.insertIgnore {
                it[id] = defaultPlanId
                it[name] = "Default"
                it[maxTotalBytes] = 100L * 1024L * 1024L
                it[maxFileBytes] = 5L * 1024L * 1024L
            }
            StorageSubscriptions.insertIgnore {
                it[id] = schoolIdValue
                it[ownerType] = StorageOwnerType.SCHOOL.name
                it[ownerId] = schoolIdValue
                it[planId] = defaultPlanId
                it[active] = true
            }
            StorageUsage.insertIgnore {
                it[ownerType] = StorageOwnerType.SCHOOL.name
                it[ownerId] = schoolIdValue
                it[usedTotalBytes] = 0
            }
        }
    }

    @AfterTest
    fun teardown() {
        transaction {
            parentId?.let { id -> Users.deleteWhere { Users.id eq id } }
            studentId?.let { id ->
                ParentStudentLinks.deleteWhere { ParentStudentLinks.studentId eq id }
                StudentClasses.deleteWhere { StudentClasses.studentId eq id }
                Students.deleteWhere { Students.id eq id }
            }
            studentIdTwo?.let { id ->
                ParentStudentLinks.deleteWhere { ParentStudentLinks.studentId eq id }
                StudentClasses.deleteWhere { StudentClasses.studentId eq id }
                Students.deleteWhere { Students.id eq id }
            }
            classIdTwo?.let { id -> SchoolClasses.deleteWhere { SchoolClasses.id eq id } }
            classId?.let { id -> SchoolClasses.deleteWhere { SchoolClasses.id eq id } }
            userId?.let { id -> Users.deleteWhere { Users.id eq id } }
            schoolId?.let { id ->
                StorageUsage.deleteWhere { (StorageUsage.ownerType eq StorageOwnerType.SCHOOL.name) and (StorageUsage.ownerId eq id) }
                StorageSubscriptions.deleteWhere { StorageSubscriptions.id eq id }
                Schools.deleteWhere { Schools.id eq id }
            }
        }
    }

    @Test
    fun `upload and download task file`() = testApplication {
        application { module() }

        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        val token = tokenService.generateAccessToken(
            userId = userId!!,
            email = "test@example.com",
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
                    description = "Page 10",
                    dueAt = "2026-01-20"
                )
            )
        }
        assertEquals(HttpStatusCode.Created, createTaskResponse.status)
        val task = createTaskResponse.body<TaskDto>()
        assertNotNull(task.id)

        val fileBytes = "pdf-content".encodeToByteArray()
        val uploadResponse = client.post("/api/tasks/${task.id}/files") {
            header("Authorization", "Bearer $token")
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append(
                            "file",
                            fileBytes,
                            Headers.build {
                                append(HttpHeaders.ContentType, "application/pdf")
                                append(HttpHeaders.ContentDisposition, "filename=\"assignment.pdf\"")
                            }
                        )
                    }
                )
            )
        }
        assertEquals(HttpStatusCode.Created, uploadResponse.status)
        val metadata = uploadResponse.body<FileMetadataDto>()
        assertNotNull(metadata.id)

        val downloadResponse = client.get("/api/files/${metadata.id}") {
            header("Authorization", "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, downloadResponse.status)
        val downloadedBytes = downloadResponse.body<ByteArray>()
        assertEquals(fileBytes.size, downloadedBytes.size)
    }

    @Test
    fun `parent can download linked assignment and submission files`() = testApplication {
        application { module() }

        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        val teacherToken = tokenService.generateAccessToken(
            userId = userId!!,
            email = "teacher@example.com",
            role = UserRole.TEACHER,
            schoolId = schoolId
        )

        val taskResponse = client.post("/api/tasks") {
            header("Authorization", "Bearer $teacherToken")
            contentType(ContentType.Application.Json)
            setBody(
                CreateTaskRequest(
                    schoolClassId = classId!!.toString(),
                    title = "Homework",
                    description = "Page 1",
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
                    grade = null,
                    note = null
                )
            )
        }
        assertEquals(HttpStatusCode.Created, submissionResponse.status)
        val submission = submissionResponse.body<TaskSubmissionDto>()

        val fileBytes = "pdf-content".encodeToByteArray()
        val assignmentUpload = client.post("/api/tasks/${task.id}/files") {
            header("Authorization", "Bearer $teacherToken")
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append(
                            "file",
                            fileBytes,
                            Headers.build {
                                append(HttpHeaders.ContentType, "application/pdf")
                                append(HttpHeaders.ContentDisposition, "filename=\"assignment.pdf\"")
                            }
                        )
                    }
                )
            )
        }
        assertEquals(HttpStatusCode.Created, assignmentUpload.status)
        val assignmentFile = assignmentUpload.body<FileMetadataDto>()

        val submissionUpload = client.post("/api/tasks/${task.id}/submissions/${submission.id}/files") {
            header("Authorization", "Bearer $teacherToken")
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append(
                            "file",
                            fileBytes,
                            Headers.build {
                                append(HttpHeaders.ContentType, "application/pdf")
                                append(HttpHeaders.ContentDisposition, "filename=\"submission.pdf\"")
                            }
                        )
                    }
                )
            )
        }
        assertEquals(HttpStatusCode.Created, submissionUpload.status)
        val submissionFile = submissionUpload.body<FileMetadataDto>()

        val parentEmail = "parent.${(10000..99999).random()}@example.com"
        val studentIdValue = studentId!!
        transaction {
            parentId = Users.insertAndGetId {
                it[email] = parentEmail
                it[passwordHash] = "test"
                it[firstName] = "Parent"
                it[lastName] = "User"
                it[role] = UserRole.PARENT
                it[Users.schoolId] = null
                it[isActive] = true
            }.value

            ParentStudentLinks.insert {
                it[ParentStudentLinks.parentUserId] = parentId!!
                it[ParentStudentLinks.studentId] = studentIdValue
                it[ParentStudentLinks.status] = "ACTIVE"
                it[ParentStudentLinks.createdBy] = userId!!
            }
        }

        val parentToken = tokenService.generateAccessToken(
            userId = parentId!!,
            email = parentEmail,
            role = UserRole.PARENT,
            schoolId = null
        )

        val parentAssignmentDownload = client.get("/api/files/${assignmentFile.id}") {
            header("Authorization", "Bearer $parentToken")
        }
        assertEquals(HttpStatusCode.OK, parentAssignmentDownload.status)

        val parentSubmissionDownload = client.get("/api/files/${submissionFile.id}") {
            header("Authorization", "Bearer $parentToken")
        }
        assertEquals(HttpStatusCode.OK, parentSubmissionDownload.status)
    }

    @Test
    fun `parent cannot download files for unlinked student`() = testApplication {
        application { module() }

        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        val teacherToken = tokenService.generateAccessToken(
            userId = userId!!,
            email = "teacher@example.com",
            role = UserRole.TEACHER,
            schoolId = schoolId
        )

        val schoolIdValue = schoolId!!
        val userIdValue = userId!!
        val classIdValue = classId!!
        val studentIdValue = studentId!!

        transaction {
            classIdTwo = SchoolClasses.insertAndGetId {
                it[SchoolClasses.schoolId] = schoolIdValue
                it[name] = "2b"
                it[alternativeName] = null
                it[createdBy] = userIdValue
            }.value

            studentIdTwo = Students.insertAndGetId {
                it[Students.schoolId] = schoolIdValue
                it[firstName] = "Student"
                it[lastName] = "Two"
                it[createdBy] = userIdValue
            }.value

            StudentClasses.insert {
                it[StudentClasses.studentId] = studentIdTwo!!
                it[StudentClasses.schoolClassId] = classIdTwo!!
                it[validFrom] = OffsetDateTime.now(ZoneOffset.UTC)
                it[validTill] = null
            }
        }

        val linkedTaskResponse = client.post("/api/tasks") {
            header("Authorization", "Bearer $teacherToken")
            contentType(ContentType.Application.Json)
            setBody(
                CreateTaskRequest(
                    schoolClassId = classIdValue.toString(),
                    title = "Linked Homework",
                    description = "Page 5",
                    dueAt = "2026-02-05"
                )
            )
        }
        assertEquals(HttpStatusCode.Created, linkedTaskResponse.status)
        val linkedTask = linkedTaskResponse.body<TaskDto>()

        val unlinkedTaskResponse = client.post("/api/tasks") {
            header("Authorization", "Bearer $teacherToken")
            contentType(ContentType.Application.Json)
            setBody(
                CreateTaskRequest(
                    schoolClassId = classIdTwo!!.toString(),
                    title = "Unlinked Homework",
                    description = "Page 6",
                    dueAt = "2026-02-06"
                )
            )
        }
        assertEquals(HttpStatusCode.Created, unlinkedTaskResponse.status)
        val unlinkedTask = unlinkedTaskResponse.body<TaskDto>()

        val submissionResponse = client.post("/api/tasks/${unlinkedTask.id}/submissions") {
            header("Authorization", "Bearer $teacherToken")
            contentType(ContentType.Application.Json)
            setBody(
                CreateTaskSubmissionRequest(
                    studentId = studentIdTwo!!.toString(),
                    submissionType = TaskSubmissionType.FILE,
                    grade = null,
                    note = null
                )
            )
        }
        assertEquals(HttpStatusCode.Created, submissionResponse.status)
        val submission = submissionResponse.body<TaskSubmissionDto>()

        val fileBytes = "pdf-content".encodeToByteArray()
        val linkedAssignmentUpload = client.post("/api/tasks/${linkedTask.id}/files") {
            header("Authorization", "Bearer $teacherToken")
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append(
                            "file",
                            fileBytes,
                            Headers.build {
                                append(HttpHeaders.ContentType, "application/pdf")
                                append(HttpHeaders.ContentDisposition, "filename=\"linked.pdf\"")
                            }
                        )
                    }
                )
            )
        }
        assertEquals(HttpStatusCode.Created, linkedAssignmentUpload.status)
        linkedAssignmentUpload.body<FileMetadataDto>()

        val unlinkedAssignmentUpload = client.post("/api/tasks/${unlinkedTask.id}/files") {
            header("Authorization", "Bearer $teacherToken")
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append(
                            "file",
                            fileBytes,
                            Headers.build {
                                append(HttpHeaders.ContentType, "application/pdf")
                                append(HttpHeaders.ContentDisposition, "filename=\"unlinked.pdf\"")
                            }
                        )
                    }
                )
            )
        }
        assertEquals(HttpStatusCode.Created, unlinkedAssignmentUpload.status)
        val unlinkedAssignmentFile = unlinkedAssignmentUpload.body<FileMetadataDto>()

        val unlinkedSubmissionUpload = client.post("/api/tasks/${unlinkedTask.id}/submissions/${submission.id}/files") {
            header("Authorization", "Bearer $teacherToken")
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append(
                            "file",
                            fileBytes,
                            Headers.build {
                                append(HttpHeaders.ContentType, "application/pdf")
                                append(HttpHeaders.ContentDisposition, "filename=\"unlinked-submission.pdf\"")
                            }
                        )
                    }
                )
            )
        }
        assertEquals(HttpStatusCode.Created, unlinkedSubmissionUpload.status)
        val unlinkedSubmissionFile = unlinkedSubmissionUpload.body<FileMetadataDto>()

        val parentEmail = "parent.${(10000..99999).random()}@example.com"
        transaction {
            parentId = Users.insertAndGetId {
                it[email] = parentEmail
                it[passwordHash] = "test"
                it[firstName] = "Parent"
                it[lastName] = "User"
                it[role] = UserRole.PARENT
                it[Users.schoolId] = null
                it[isActive] = true
            }.value

            ParentStudentLinks.insert {
                it[ParentStudentLinks.parentUserId] = parentId!!
                it[ParentStudentLinks.studentId] = studentIdValue
                it[ParentStudentLinks.status] = "ACTIVE"
                it[ParentStudentLinks.createdBy] = userId!!
            }
        }

        val parentToken = tokenService.generateAccessToken(
            userId = parentId!!,
            email = parentEmail,
            role = UserRole.PARENT,
            schoolId = null
        )

        val deniedAssignmentDownload = client.get("/api/files/${unlinkedAssignmentFile.id}") {
            header("Authorization", "Bearer $parentToken")
        }
        assertEquals(HttpStatusCode.NotFound, deniedAssignmentDownload.status)

        val deniedSubmissionDownload = client.get("/api/files/${unlinkedSubmissionFile.id}") {
            header("Authorization", "Bearer $parentToken")
        }
        assertEquals(HttpStatusCode.NotFound, deniedSubmissionDownload.status)
    }
}
