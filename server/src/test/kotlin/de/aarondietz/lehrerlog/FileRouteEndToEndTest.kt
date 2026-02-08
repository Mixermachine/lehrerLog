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
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.readBytes
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.net.ServerSocket
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.use
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

        val metadataResponse = client.get("/api/tasks/${task.id}/file") {
            header("Authorization", "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, metadataResponse.status)
        val resolvedMetadata = metadataResponse.body<FileMetadataDto>()
        assertEquals(metadata.id, resolvedMetadata.id)

        val downloadResponse = client.get("/api/files/${metadata.id}") {
            header("Authorization", "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, downloadResponse.status)
        val downloadedBytes = downloadResponse.body<ByteArray>()
        assertEquals(fileBytes.size, downloadedBytes.size)
    }

    @Test
    fun `task file metadata endpoint returns invalid and missing errors`() = testApplication {
        application { module() }

        createClient {
            install(ContentNegotiation) {
                json()
            }
        }.use { client ->
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
                        title = "No file yet",
                        description = null,
                        dueAt = "2026-01-20"
                    )
                )
            }
            assertEquals(HttpStatusCode.Created, createTaskResponse.status)
            val task = createTaskResponse.body<TaskDto>()

            val missing = client.get("/api/tasks/${task.id}/file") {
                header("Authorization", "Bearer $token")
            }
            assertEquals(HttpStatusCode.NotFound, missing.status)

            val invalid = client.get("/api/tasks/not-a-uuid/file") {
                header("Authorization", "Bearer $token")
            }
            assertEquals(HttpStatusCode.BadRequest, invalid.status)
        }
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

    @Test
    fun `file route upload validation errors`() = testApplication {
        application { module() }

        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        val token = tokenService.generateAccessToken(
            userId = userId!!,
            email = "test@example.com",
            role = UserRole.TEACHER,
            schoolId = schoolId
        )

        val taskResponse = client.post("/api/tasks") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                CreateTaskRequest(
                    schoolClassId = classId!!.toString(),
                    title = "Uploads",
                    description = null,
                    dueAt = "2026-02-12"
                )
            )
        }
        assertEquals(HttpStatusCode.Created, taskResponse.status)
        val task = taskResponse.body<TaskDto>()

        val missingFile = client.post("/api/tasks/${task.id}/files") {
            header("Authorization", "Bearer $token")
            setBody(MultiPartFormDataContent(formData { }))
        }
        assertEquals(HttpStatusCode.BadRequest, missingFile.status)

        val fileBytes = "pdf-content".encodeToByteArray()
        val invalidTaskId = client.post("/api/tasks/not-a-uuid/files") {
            header("Authorization", "Bearer $token")
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append(
                            "file",
                            fileBytes,
                            Headers.build {
                                append(HttpHeaders.ContentType, "application/pdf")
                                append(HttpHeaders.ContentDisposition, "filename=\"invalid.pdf\"")
                            }
                        )
                    }
                )
            )
        }
        assertEquals(HttpStatusCode.BadRequest, invalidTaskId.status)

        val missingSubmission = client.post("/api/tasks/${task.id}/submissions/${UUID.randomUUID()}/files") {
            header("Authorization", "Bearer $token")
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append(
                            "file",
                            fileBytes,
                            Headers.build {
                                append(HttpHeaders.ContentType, "application/pdf")
                                append(HttpHeaders.ContentDisposition, "filename=\"missing.pdf\"")
                            }
                        )
                    }
                )
            )
        }
        assertEquals(HttpStatusCode.NotFound, missingSubmission.status)

        val parentEmail = "parent.${(10000..99999).random()}@example.com"
        transaction {
            parentId = Users.insertAndGetId {
                it[email] = parentEmail
                it[passwordHash] = "test"
                it[firstName] = "Parent"
                it[lastName] = "Upload"
                it[role] = UserRole.PARENT
                it[Users.schoolId] = null
                it[isActive] = true
            }.value
        }

        val parentToken = tokenService.generateAccessToken(
            userId = parentId!!,
            email = parentEmail,
            role = UserRole.PARENT,
            schoolId = null
        )

        val parentUpload = client.post("/api/tasks/${task.id}/files") {
            header("Authorization", "Bearer $parentToken")
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append(
                            "file",
                            fileBytes,
                            Headers.build {
                                append(HttpHeaders.ContentType, "application/pdf")
                                append(HttpHeaders.ContentDisposition, "filename=\"parent.pdf\"")
                            }
                        )
                    }
                )
            )
        }
        assertEquals(HttpStatusCode.Forbidden, parentUpload.status)
    }

    @Test
    fun `file route download and quota errors`() = testApplication {
        application { module() }

        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        val token = tokenService.generateAccessToken(
            userId = userId!!,
            email = "test@example.com",
            role = UserRole.TEACHER,
            schoolId = schoolId
        )
        val noSchoolToken = tokenService.generateAccessToken(
            userId = userId!!,
            email = "test@example.com",
            role = UserRole.TEACHER,
            schoolId = null
        )

        val invalidFileId = client.get("/api/files/not-a-uuid") {
            header("Authorization", "Bearer $token")
        }
        assertEquals(HttpStatusCode.BadRequest, invalidFileId.status)

        val forbiddenDownload = client.get("/api/files/${UUID.randomUUID()}") {
            header("Authorization", "Bearer $noSchoolToken")
        }
        assertEquals(HttpStatusCode.Forbidden, forbiddenDownload.status)

        val notFoundDownload = client.get("/api/files/${UUID.randomUUID()}") {
            header("Authorization", "Bearer $token")
        }
        assertEquals(HttpStatusCode.NotFound, notFoundDownload.status)

        val taskResponse = client.post("/api/tasks") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                CreateTaskRequest(
                    schoolClassId = classId!!.toString(),
                    title = "Quota Task",
                    description = null,
                    dueAt = "2026-02-13"
                )
            )
        }
        assertEquals(HttpStatusCode.Created, taskResponse.status)
        val task = taskResponse.body<TaskDto>()

        val fileBytes = "data".encodeToByteArray()
        val invalidSubmissionId = client.post("/api/tasks/${task.id}/submissions/not-a-uuid/files") {
            header("Authorization", "Bearer $token")
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append(
                            "file",
                            fileBytes,
                            Headers.build {
                                append(HttpHeaders.ContentType, "application/pdf")
                                append(HttpHeaders.ContentDisposition, "filename=\"invalid-submission.pdf\"")
                            }
                        )
                    }
                )
            )
        }
        assertEquals(HttpStatusCode.BadRequest, invalidSubmissionId.status)

        val defaultPlanId = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val (originalMaxFile, originalMaxTotal) = transaction {
            val row = StoragePlans.selectAll()
                .where { StoragePlans.id eq defaultPlanId }
                .single()
            row[StoragePlans.maxFileBytes] to row[StoragePlans.maxTotalBytes]
        }

        val ownerFilter = (StorageUsage.ownerType eq StorageOwnerType.SCHOOL.name) and
                (StorageUsage.ownerId eq schoolId!!)

        try {
            transaction {
                StoragePlans.update({ StoragePlans.id eq defaultPlanId }) {
                    it[maxFileBytes] = 3L
                    it[maxTotalBytes] = originalMaxTotal
                }
                StorageUsage.update({ ownerFilter }) {
                    it[usedTotalBytes] = 0
                }
            }

            val tooLarge = client.post("/api/tasks/${task.id}/files") {
                header("Authorization", "Bearer $token")
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append(
                                "file",
                                fileBytes,
                                Headers.build {
                                    append(HttpHeaders.ContentType, "application/pdf")
                                    append(HttpHeaders.ContentDisposition, "filename=\"too-large.pdf\"")
                                }
                            )
                        }
                    )
                )
            }
            assertEquals(HttpStatusCode.PayloadTooLarge, tooLarge.status)

            transaction {
                StoragePlans.update({ StoragePlans.id eq defaultPlanId }) {
                    it[maxFileBytes] = originalMaxFile
                    it[maxTotalBytes] = 3L
                }
                StorageUsage.update({ ownerFilter }) {
                    it[usedTotalBytes] = 0
                }
            }

            val quotaExceeded = client.post("/api/tasks/${task.id}/files") {
                header("Authorization", "Bearer $token")
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append(
                                "file",
                                fileBytes,
                                Headers.build {
                                    append(HttpHeaders.ContentType, "application/pdf")
                                    append(HttpHeaders.ContentDisposition, "filename=\"quota.pdf\"")
                                }
                            )
                        }
                    )
                )
            }
            assertEquals(HttpStatusCode.Conflict, quotaExceeded.status)
        } finally {
            transaction {
                StoragePlans.update({ StoragePlans.id eq defaultPlanId }) {
                    it[maxFileBytes] = originalMaxFile
                    it[maxTotalBytes] = originalMaxTotal
                }
                StorageUsage.update({ ownerFilter }) {
                    it[usedTotalBytes] = 0
                }
            }
        }
    }

    @Test
    fun `download task file from object storage`() = testApplication {
        val objectStorage = startObjectStorageStub()
        System.setProperty("OBJECT_STORAGE_ENDPOINT", objectStorage.endpoint)
        System.setProperty("OBJECT_STORAGE_BUCKET", objectStorage.bucket)
        System.setProperty("OBJECT_STORAGE_REGION", "us-east-1")
        System.setProperty("OBJECT_STORAGE_ACCESS_KEY", "test-access")
        System.setProperty("OBJECT_STORAGE_SECRET_KEY", "test-secret")
        System.setProperty("OBJECT_STORAGE_PATH_STYLE", "true")

        try {
            application { module() }

            createClient {
                install(ContentNegotiation) { json() }
            }.use { client ->
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
                            title = "Object Storage Homework",
                            description = "Upload",
                            dueAt = "2026-02-10"
                        )
                    )
                }
                assertEquals(HttpStatusCode.Created, createTaskResponse.status)
                val task = createTaskResponse.body<TaskDto>()

                val fileBytes = "object-storage-content".encodeToByteArray()
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
                                        append(HttpHeaders.ContentDisposition, "filename=\"storage.pdf\"")
                                    }
                                )
                            }
                        )
                    )
                }
                assertEquals(HttpStatusCode.Created, uploadResponse.status)
                val metadata = uploadResponse.body<FileMetadataDto>()

                val downloadResponse = client.get("/api/files/${metadata.id}") {
                    header("Authorization", "Bearer $token")
                }
                assertEquals(HttpStatusCode.OK, downloadResponse.status)
                val downloadedBytes = downloadResponse.body<ByteArray>()
                assertEquals(fileBytes.size, downloadedBytes.size)
            }
        } finally {
            System.clearProperty("OBJECT_STORAGE_ENDPOINT")
            System.clearProperty("OBJECT_STORAGE_BUCKET")
            System.clearProperty("OBJECT_STORAGE_REGION")
            System.clearProperty("OBJECT_STORAGE_ACCESS_KEY")
            System.clearProperty("OBJECT_STORAGE_SECRET_KEY")
            System.clearProperty("OBJECT_STORAGE_PATH_STYLE")
            objectStorage.stop()
        }
    }

    private data class ObjectStorageStub(
        val endpoint: String,
        val bucket: String,
        private val engine: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>
    ) {
        fun stop() {
            engine.stop(500, 500)
        }
    }

    private fun startObjectStorageStub(): ObjectStorageStub {
        val bucket = "test-bucket"
        val storedObjects = ConcurrentHashMap<String, ByteArray>()
        val port = ServerSocket(0).use { it.localPort }

        val engine = embeddedServer(Netty, port = port) {
            routing {
                put("/{path...}") {
                    val objectKey = extractObjectKey(call.request.path())
                        ?: return@put call.respond(HttpStatusCode.BadRequest)
                    val rawBytes = call.receiveChannel().readRemaining().readBytes()
                    val decodedLength = call.request.headers["x-amz-decoded-content-length"]?.toIntOrNull()
                    val bytes = if (decodedLength != null) {
                        decodeAwsChunkedPayload(rawBytes, decodedLength)
                    } else {
                        rawBytes
                    }
                    storedObjects[objectKey] = bytes
                    call.response.headers.append(HttpHeaders.ETag, "\"test\"")
                    call.respond(HttpStatusCode.OK)
                }
                get("/{path...}") {
                    val objectKey = extractObjectKey(call.request.path())
                        ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val bytes = storedObjects[objectKey]
                        ?: return@get call.respond(HttpStatusCode.NotFound)
                    call.respondBytes(bytes, ContentType.Application.OctetStream)
                }
            }
        }.start()

        return ObjectStorageStub(endpoint = "http://localhost:$port", bucket = bucket, engine = engine)
    }

    private fun extractObjectKey(path: String): String? {
        val trimmed = path.trimStart('/')
        val parts = trimmed.split("/", limit = 2)
        if (parts.size < 2) {
            return null
        }
        return parts[1]
    }

    private fun decodeAwsChunkedPayload(raw: ByteArray, expectedLength: Int): ByteArray {
        val output = ByteArray(expectedLength)
        var outputOffset = 0
        var index = 0

        while (index < raw.size) {
            val lineEnd = findCrlf(raw, index)
            if (lineEnd == -1) {
                break
            }
            val header = raw.copyOfRange(index, lineEnd).toString(Charsets.US_ASCII)
            val sizeHex = header.substringBefore(';')
            val chunkSize = sizeHex.toIntOrNull(16) ?: break
            index = lineEnd + 2
            if (chunkSize == 0) {
                break
            }
            raw.copyInto(output, outputOffset, index, index + chunkSize)
            outputOffset += chunkSize
            index += chunkSize + 2
            if (outputOffset >= expectedLength) {
                break
            }
        }

        return if (outputOffset == output.size) {
            output
        } else {
            output.copyOf(outputOffset)
        }
    }

    private fun findCrlf(raw: ByteArray, start: Int): Int {
        var index = start
        while (index + 1 < raw.size) {
            if (raw[index] == '\r'.code.toByte() && raw[index + 1] == '\n'.code.toByte()) {
                return index
            }
            index += 1
        }
        return -1
    }
}
