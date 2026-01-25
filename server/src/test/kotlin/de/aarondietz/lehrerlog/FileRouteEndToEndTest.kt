package de.aarondietz.lehrerlog

import de.aarondietz.lehrerlog.auth.TokenService
import de.aarondietz.lehrerlog.data.CreateTaskRequest
import de.aarondietz.lehrerlog.data.TaskDto
import de.aarondietz.lehrerlog.data.FileMetadataDto
import de.aarondietz.lehrerlog.db.DatabaseFactory
import de.aarondietz.lehrerlog.db.tables.SchoolClasses
import de.aarondietz.lehrerlog.db.tables.Schools
import de.aarondietz.lehrerlog.db.tables.StorageOwnerType
import de.aarondietz.lehrerlog.db.tables.StoragePlans
import de.aarondietz.lehrerlog.db.tables.StorageSubscriptions
import de.aarondietz.lehrerlog.db.tables.StorageUsage
import de.aarondietz.lehrerlog.db.tables.Users
import de.aarondietz.lehrerlog.db.tables.UserRole
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class FileRouteEndToEndTest {

    private val tokenService = TokenService()
    private var schoolId: UUID? = null
    private var userId: UUID? = null
    private var classId: UUID? = null
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
}
