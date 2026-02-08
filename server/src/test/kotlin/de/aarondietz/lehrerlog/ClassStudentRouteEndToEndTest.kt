package de.aarondietz.lehrerlog

import de.aarondietz.lehrerlog.auth.TokenService
import de.aarondietz.lehrerlog.data.StudentDto
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
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*
import kotlin.test.*

class ClassStudentRouteEndToEndTest {

    private val tokenService = TokenService()
    private var schoolId: UUID? = null
    private var userId: UUID? = null
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
                it[name] = "1b"
                it[alternativeName] = null
                it[createdBy] = userId!!
            }.value

            studentId = Students.insertAndGetId {
                it[Students.schoolId] = schoolIdValue
                it[firstName] = "Max"
                it[lastName] = "Mustermann"
                it[createdBy] = userId!!
            }.value
        }
    }

    @AfterTest
    fun teardown() {
        transaction {
            studentId?.let { id ->
                StudentClasses.deleteWhere { StudentClasses.studentId eq id }
                Students.deleteWhere { Students.id eq id }
            }
            classId?.let { id -> SchoolClasses.deleteWhere { SchoolClasses.id eq id } }
            userId?.let { id -> SyncLog.deleteWhere { SyncLog.userId eq id } }
            userId?.let { id -> Users.deleteWhere { Users.id eq id } }
            schoolId?.let { id -> Schools.deleteWhere { Schools.id eq id } }
        }
    }

    @Test
    fun `add and remove student from class`() = testApplicationWithTimeout {
        application { module() }
        createClient {
            install(ContentNegotiation) { json() }
        }.use { client ->
            val token = tokenService.generateAccessToken(
                userId = userId!!,
                email = "teacher@example.com",
                role = de.aarondietz.lehrerlog.db.tables.UserRole.TEACHER,
                schoolId = schoolId
            )

            val addResponse = client.post("/api/classes/${classId}/students/${studentId}") {
                header("Authorization", "Bearer $token")
            }
            assertEquals(HttpStatusCode.OK, addResponse.status)
            val addedStudent = addResponse.body<StudentDto>()
            assertTrue(addedStudent.classIds.contains(classId!!.toString()))

            val fetchResponse = client.get("/api/students/${studentId}") {
                header("Authorization", "Bearer $token")
            }
            assertEquals(HttpStatusCode.OK, fetchResponse.status)
            val fetchedStudent = fetchResponse.body<StudentDto>()
            assertTrue(fetchedStudent.classIds.contains(classId!!.toString()))

            val removeResponse = client.delete("/api/classes/${classId}/students/${studentId}") {
                header("Authorization", "Bearer $token")
            }
            assertEquals(HttpStatusCode.OK, removeResponse.status)
            val removedStudent = removeResponse.body<StudentDto>()
            assertTrue(removedStudent.classIds.none { it == classId!!.toString() })

            val finalResponse = client.get("/api/students/${studentId}") {
                header("Authorization", "Bearer $token")
            }
            assertEquals(HttpStatusCode.OK, finalResponse.status)
            val finalStudent = finalResponse.body<StudentDto>()
            assertTrue(finalStudent.classIds.none { it == classId!!.toString() })
        }
    }
}
