package de.aarondietz.lehrerlog

import de.aarondietz.lehrerlog.db.DatabaseFactory
import de.aarondietz.lehrerlog.db.tables.*
import de.aarondietz.lehrerlog.services.StudentService
import de.aarondietz.lehrerlog.services.UpdateResult
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*
import kotlin.test.*

/**
 * End-to-end tests for StudentService with sync logging.
 * Tests verify CRUD operations and automatic sync log entries.
 */
class StudentServiceEndToEndTest {

    companion object {
        private var testSchoolId: UUID? = null
        private var testUserId: UUID? = null
        private var isInitialized = false

        private val TEST_PREFIX = "testing${(10000..99999).random()}"
        private val TEST_PREFIX_UPPER = TEST_PREFIX.uppercase()
    }

    private lateinit var studentService: StudentService

    @BeforeTest
    fun setup() {
        if (!isInitialized) {
            DatabaseFactory.init()
            isInitialized = true
            println("Test run using prefix: $TEST_PREFIX")
        }

        studentService = StudentService()
        cleanupTestData()

        // Create test school and user
        testSchoolId = transaction {
            Schools.insert {
                it[name] = "$TEST_PREFIX Test School"
                it[code] = "${TEST_PREFIX_UPPER}_SCHOOL"
            } get Schools.id
        }.value

        testUserId = transaction {
            Users.insert {
                it[email] = "$TEST_PREFIX.user@example.com"
                it[passwordHash] = "test_hash"
                it[firstName] = "Test"
                it[lastName] = "User"
                it[role] = UserRole.TEACHER
                it[schoolId] = testSchoolId
            } get Users.id
        }.value
    }

    @AfterTest
    fun teardown() {
        cleanupTestData()
    }

    private fun cleanupTestData() {
        transaction {
            val emailPattern = "$TEST_PREFIX.%@example.com"
            val schoolCodePattern = "${TEST_PREFIX_UPPER}_%"

            // Delete in reverse order of foreign key dependencies
            val testUserIds = Users.selectAll()
                .where { Users.email like emailPattern }
                .map { it[Users.id] }

            if (testUserIds.isNotEmpty()) {
                RefreshTokens.deleteWhere { RefreshTokens.userId inList testUserIds }
            }

            val testSchoolIds = Schools.selectAll()
                .where { Schools.code like schoolCodePattern }
                .map { it[Schools.id] }

            if (testSchoolIds.isNotEmpty()) {
                // Delete sync logs for test schools
                SyncLog.deleteWhere { SyncLog.schoolId inList testSchoolIds }

                // Delete students from test schools
                Students.deleteWhere { Students.schoolId inList testSchoolIds }
            }

            Users.deleteWhere { Users.email like emailPattern }
            Schools.deleteWhere { Schools.code like schoolCodePattern }
        }
    }

    @Test
    fun `test create student creates sync log entry`() {
        val student = studentService.createStudent(
            schoolId = testSchoolId!!,
            firstName = "John",
            lastName = "Doe",
            classIds = emptyList(),
            userId = testUserId!!
        )

        assertNotNull(student.id)
        assertEquals("John", student.firstName)
        assertEquals("Doe", student.lastName)
        assertEquals(1L, student.version)

        // Verify sync log entry was created
        val syncLogs = transaction {
            SyncLog.selectAll()
                .where {
                    (SyncLog.schoolId eq testSchoolId!!) and
                    (SyncLog.entityType eq EntityType.STUDENT.name) and
                    (SyncLog.entityId eq UUID.fromString(student.id))
                }
                .toList()
        }

        assertEquals(1, syncLogs.size, "Should have exactly one sync log entry")
        val log = syncLogs.first()
        assertEquals(SyncOperation.CREATE, log[SyncLog.operation])
        assertEquals(testUserId, log[SyncLog.userId].value)
    }

    @Test
    fun `test update student creates sync log entry and increments version`() {
        // Create a student
        val student = studentService.createStudent(
            schoolId = testSchoolId!!,
            firstName = "Jane",
            lastName = "Smith",
            classIds = emptyList(),
            userId = testUserId!!
        )

        // Update the student
        val result = studentService.updateStudent(
            studentId = UUID.fromString(student.id),
            schoolId = testSchoolId!!,
            firstName = "Janet",
            lastName = "Smith-Jones",
            classIds = emptyList(),
            version = student.version,
            userId = testUserId!!
        )

        assertTrue(result is UpdateResult.Success)
        val updated = (result as UpdateResult.Success).data
        assertEquals("Janet", updated.firstName)
        assertEquals("Smith-Jones", updated.lastName)
        assertEquals(2L, updated.version, "Version should increment")

        // Verify sync log entries (CREATE and UPDATE)
        val syncLogs = transaction {
            SyncLog.selectAll()
                .where {
                    (SyncLog.schoolId eq testSchoolId!!) and
                    (SyncLog.entityType eq EntityType.STUDENT.name) and
                    (SyncLog.entityId eq UUID.fromString(student.id))
                }
                .orderBy(SyncLog.id)
                .toList()
        }

        assertEquals(2, syncLogs.size, "Should have CREATE and UPDATE entries")
        assertEquals(SyncOperation.CREATE, syncLogs[0][SyncLog.operation])
        assertEquals(SyncOperation.UPDATE, syncLogs[1][SyncLog.operation])
    }

    @Test
    fun `test update with wrong version returns version conflict`() {
        val student = studentService.createStudent(
            schoolId = testSchoolId!!,
            firstName = "Bob",
            lastName = "Wilson",
            classIds = emptyList(),
            userId = testUserId!!
        )

        // Try to update with wrong version
        val result = studentService.updateStudent(
            studentId = UUID.fromString(student.id),
            schoolId = testSchoolId!!,
            firstName = "Robert",
            lastName = "Wilson",
            classIds = emptyList(),
            version = 999L, // Wrong version
            userId = testUserId!!
        )

        assertTrue(result is UpdateResult.VersionConflict)
    }

    @Test
    fun `test delete student creates sync log entry`() {
        val student = studentService.createStudent(
            schoolId = testSchoolId!!,
            firstName = "Alice",
            lastName = "Brown",
            classIds = emptyList(),
            userId = testUserId!!
        )

        val deleted = studentService.deleteStudent(
            studentId = UUID.fromString(student.id),
            schoolId = testSchoolId!!,
            userId = testUserId!!
        )

        assertTrue(deleted, "Student should be deleted")

        // Verify student is gone
        val found = studentService.getStudent(UUID.fromString(student.id), testSchoolId!!)
        assertNull(found, "Student should not be found after deletion")

        // Verify sync log entries (CREATE and DELETE)
        val syncLogs = transaction {
            SyncLog.selectAll()
                .where {
                    (SyncLog.schoolId eq testSchoolId!!) and
                    (SyncLog.entityType eq EntityType.STUDENT.name) and
                    (SyncLog.entityId eq UUID.fromString(student.id))
                }
                .orderBy(SyncLog.id)
                .toList()
        }

        assertEquals(2, syncLogs.size, "Should have CREATE and DELETE entries")
        assertEquals(SyncOperation.CREATE, syncLogs[0][SyncLog.operation])
        assertEquals(SyncOperation.DELETE, syncLogs[1][SyncLog.operation])
    }

    @Test
    fun `test school isolation - cannot access other school students`() {
        // Create another school
        val otherSchoolId = transaction {
            Schools.insert {
                it[name] = "$TEST_PREFIX Other School"
                it[code] = "${TEST_PREFIX_UPPER}_OTHER"
            } get Schools.id
        }.value

        // Create student in other school
        val student = studentService.createStudent(
            schoolId = otherSchoolId,
            firstName = "Eve",
            lastName = "Davis",
            classIds = emptyList(),
            userId = testUserId!!
        )

        // Try to get student using wrong school ID
        val found = studentService.getStudent(UUID.fromString(student.id), testSchoolId!!)
        assertNull(found, "Should not find student from different school")

        // Try to delete student using wrong school ID
        val deleted = studentService.deleteStudent(
            studentId = UUID.fromString(student.id),
            schoolId = testSchoolId!!,
            userId = testUserId!!
        )
        assertFalse(deleted, "Should not delete student from different school")

        // Cleanup
        transaction {
            Students.deleteWhere { Students.schoolId eq otherSchoolId }
            Schools.deleteWhere { Schools.id eq otherSchoolId }
        }
    }

    @Test
    fun `test get students by school returns all students for that school`() {
        // Create multiple students
        val student1 = studentService.createStudent(
            schoolId = testSchoolId!!,
            firstName = "Student",
            lastName = "One",
            classIds = emptyList(),
            userId = testUserId!!
        )

        val student2 = studentService.createStudent(
            schoolId = testSchoolId!!,
            firstName = "Student",
            lastName = "Two",
            classIds = emptyList(),
            userId = testUserId!!
        )

        val students = studentService.getStudentsBySchool(testSchoolId!!)

        assertTrue(students.size >= 2, "Should have at least 2 students")
        assertTrue(students.any { it.id == student1.id })
        assertTrue(students.any { it.id == student2.id })
    }
}
