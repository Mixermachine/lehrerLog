package de.aarondietz.lehrerlog

import de.aarondietz.lehrerlog.db.DatabaseFactory
import de.aarondietz.lehrerlog.db.tables.*
import de.aarondietz.lehrerlog.services.SchoolClassService
import de.aarondietz.lehrerlog.services.StudentService
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*
import kotlin.test.*

/**
 * End-to-end tests for SyncLog functionality.
 * Tests verify sync log querying, filtering, and ordering for incremental sync.
 */
class SyncLogEndToEndTest {

    companion object {
        private var testSchoolId: UUID? = null
        private var testUserId: UUID? = null
        private var isInitialized = false

        private val TEST_PREFIX = "testing${(10000..99999).random()}"
        private val TEST_PREFIX_UPPER = TEST_PREFIX.uppercase()
    }

    private lateinit var studentService: StudentService
    private lateinit var schoolClassService: SchoolClassService

    @BeforeTest
    fun setup() {
        if (!isInitialized) {
            DatabaseFactory.init()
            isInitialized = true
            println("Test run using prefix: $TEST_PREFIX")
        }

        studentService = StudentService()
        schoolClassService = SchoolClassService()
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

                // Delete students and classes from test schools
                Students.deleteWhere { Students.schoolId inList testSchoolIds }
                SchoolClasses.deleteWhere { SchoolClasses.schoolId inList testSchoolIds }
            }

            Users.deleteWhere { Users.email like emailPattern }
            Schools.deleteWhere { Schools.code like schoolCodePattern }
        }
    }

    @Test
    fun `test sync logs are created for different entity types`() {
        // Create a student
        val student = studentService.createStudent(
            schoolId = testSchoolId!!,
            firstName = "John",
            lastName = "Doe",
            classIds = emptyList(),
            userId = testUserId!!
        )

        // Create a class
        val schoolClass = schoolClassService.createClass(
            schoolId = testSchoolId!!,
            name = "5A",
            alternativeName = "Class 5A",
            userId = testUserId!!
        )

        // Query sync logs for this school
        val syncLogs = transaction {
            SyncLog.selectAll()
                .where { SyncLog.schoolId eq testSchoolId!! }
                .orderBy(SyncLog.id)
                .toList()
        }

        assertEquals(2, syncLogs.size, "Should have 2 sync log entries")

        // Verify student log
        val studentLog = syncLogs.find { it[SyncLog.entityType] == EntityType.STUDENT.name }
        assertNotNull(studentLog, "Should have student sync log")
        assertEquals(UUID.fromString(student.id), studentLog[SyncLog.entityId])
        assertEquals(SyncOperation.CREATE, studentLog[SyncLog.operation])

        // Verify class log
        val classLog = syncLogs.find { it[SyncLog.entityType] == EntityType.SCHOOL_CLASS.name }
        assertNotNull(classLog, "Should have class sync log")
        assertEquals(UUID.fromString(schoolClass.id), classLog[SyncLog.entityId])
        assertEquals(SyncOperation.CREATE, classLog[SyncLog.operation])
    }

    @Test
    fun `test sync logs are isolated by school`() {
        // Create another school
        val otherSchoolId = transaction {
            Schools.insert {
                it[name] = "$TEST_PREFIX Other School"
                it[code] = "${TEST_PREFIX_UPPER}_OTHER"
            } get Schools.id
        }.value

        // Create student in test school
        studentService.createStudent(
            schoolId = testSchoolId!!,
            firstName = "Alice",
            lastName = "Smith",
            classIds = emptyList(),
            userId = testUserId!!
        )

        // Create student in other school
        studentService.createStudent(
            schoolId = otherSchoolId,
            firstName = "Bob",
            lastName = "Jones",
            classIds = emptyList(),
            userId = testUserId!!
        )

        // Query sync logs for test school only
        val testSchoolLogs = transaction {
            SyncLog.selectAll()
                .where { SyncLog.schoolId eq testSchoolId!! }
                .toList()
        }

        // Query sync logs for other school only
        val otherSchoolLogs = transaction {
            SyncLog.selectAll()
                .where { SyncLog.schoolId eq otherSchoolId }
                .toList()
        }

        assertEquals(1, testSchoolLogs.size, "Test school should have 1 log entry")
        assertEquals(1, otherSchoolLogs.size, "Other school should have 1 log entry")

        // Verify they are different
        assertNotEquals(
            testSchoolLogs.first()[SyncLog.entityId],
            otherSchoolLogs.first()[SyncLog.entityId],
            "Should track different students"
        )

        // Cleanup
        transaction {
            SyncLog.deleteWhere { SyncLog.schoolId eq otherSchoolId }
            Students.deleteWhere { Students.schoolId eq otherSchoolId }
            Schools.deleteWhere { Schools.id eq otherSchoolId }
        }
    }

    @Test
    fun `test query sync logs since specific ID for incremental sync`() {
        // Create multiple students to generate sync logs
        studentService.createStudent(
            schoolId = testSchoolId!!,
            firstName = "Student",
            lastName = "One",
            classIds = emptyList(),
            userId = testUserId!!
        )

        studentService.createStudent(
            schoolId = testSchoolId!!,
            firstName = "Student",
            lastName = "Two",
            classIds = emptyList(),
            userId = testUserId!!
        )

        studentService.createStudent(
            schoolId = testSchoolId!!,
            firstName = "Student",
            lastName = "Three",
            classIds = emptyList(),
            userId = testUserId!!
        )

        // Get all sync logs and find the second one's ID
        val allLogs = transaction {
            SyncLog.selectAll()
                .where { SyncLog.schoolId eq testSchoolId!! }
                .orderBy(SyncLog.id)
                .toList()
        }

        assertEquals(3, allLogs.size, "Should have 3 sync log entries")

        val secondLogId = allLogs[1][SyncLog.id].value

        // Query logs since the second ID (should get only the third one)
        val recentLogs = transaction {
            SyncLog.selectAll()
                .where {
                    (SyncLog.schoolId eq testSchoolId!!) and
                    (SyncLog.id greater secondLogId)
                }
                .orderBy(SyncLog.id)
                .toList()
        }

        assertEquals(1, recentLogs.size, "Should have 1 log entry after the second one")
        assertEquals(allLogs[2][SyncLog.id], recentLogs[0][SyncLog.id])
    }

    @Test
    fun `test sync logs maintain operation order for same entity`() {
        // Create, update, and delete a student
        val student = studentService.createStudent(
            schoolId = testSchoolId!!,
            firstName = "Charlie",
            lastName = "Brown",
            classIds = emptyList(),
            userId = testUserId!!
        )

        studentService.updateStudent(
            studentId = UUID.fromString(student.id),
            schoolId = testSchoolId!!,
            firstName = "Charles",
            lastName = "Brown",
            classIds = emptyList(),
            version = student.version,
            userId = testUserId!!
        )

        studentService.deleteStudent(
            studentId = UUID.fromString(student.id),
            schoolId = testSchoolId!!,
            userId = testUserId!!
        )

        // Query sync logs for this student
        val syncLogs = transaction {
            SyncLog.selectAll()
                .where {
                    (SyncLog.schoolId eq testSchoolId!!) and
                    (SyncLog.entityId eq UUID.fromString(student.id))
                }
                .orderBy(SyncLog.id)
                .toList()
        }

        assertEquals(3, syncLogs.size, "Should have 3 operations")
        assertEquals(SyncOperation.CREATE, syncLogs[0][SyncLog.operation])
        assertEquals(SyncOperation.UPDATE, syncLogs[1][SyncLog.operation])
        assertEquals(SyncOperation.DELETE, syncLogs[2][SyncLog.operation])

        // Verify IDs are in ascending order
        assertTrue(syncLogs[0][SyncLog.id].value < syncLogs[1][SyncLog.id].value)
        assertTrue(syncLogs[1][SyncLog.id].value < syncLogs[2][SyncLog.id].value)
    }

    @Test
    fun `test sync logs filter by entity type`() {
        // Create both students and classes
        studentService.createStudent(
            schoolId = testSchoolId!!,
            firstName = "David",
            lastName = "Wilson",
            classIds = emptyList(),
            userId = testUserId!!
        )

        studentService.createStudent(
            schoolId = testSchoolId!!,
            firstName = "Emily",
            lastName = "Taylor",
            classIds = emptyList(),
            userId = testUserId!!
        )

        schoolClassService.createClass(
            schoolId = testSchoolId!!,
            name = "6A",
            alternativeName = "Class 6A",
            userId = testUserId!!
        )

        // Query student logs only
        val studentLogs = transaction {
            SyncLog.selectAll()
                .where {
                    (SyncLog.schoolId eq testSchoolId!!) and
                    (SyncLog.entityType eq EntityType.STUDENT.name)
                }
                .toList()
        }

        // Query class logs only
        val classLogs = transaction {
            SyncLog.selectAll()
                .where {
                    (SyncLog.schoolId eq testSchoolId!!) and
                    (SyncLog.entityType eq EntityType.SCHOOL_CLASS.name)
                }
                .toList()
        }

        assertEquals(2, studentLogs.size, "Should have 2 student logs")
        assertEquals(1, classLogs.size, "Should have 1 class log")

        // Verify all student logs are for students
        assertTrue(studentLogs.all { it[SyncLog.entityType] == EntityType.STUDENT.name })

        // Verify all class logs are for classes
        assertTrue(classLogs.all { it[SyncLog.entityType] == EntityType.SCHOOL_CLASS.name })
    }

    @Test
    fun `test sync logs include user who made the change`() {
        val student = studentService.createStudent(
            schoolId = testSchoolId!!,
            firstName = "Frank",
            lastName = "Miller",
            classIds = emptyList(),
            userId = testUserId!!
        )

        val syncLog = transaction {
            SyncLog.selectAll()
                .where {
                    (SyncLog.schoolId eq testSchoolId!!) and
                    (SyncLog.entityId eq UUID.fromString(student.id))
                }
                .single()
        }

        assertEquals(testUserId, syncLog[SyncLog.userId].value)
    }

    @Test
    fun `test sync logs pagination for large datasets`() {
        // Create 10 students
        repeat(10) { index ->
            studentService.createStudent(
                schoolId = testSchoolId!!,
                firstName = "Student",
                lastName = "Number$index",
                classIds = emptyList(),
                userId = testUserId!!
            )
        }

        // Get first 5 logs
        val firstPage = transaction {
            SyncLog.selectAll()
                .where { SyncLog.schoolId eq testSchoolId!! }
                .orderBy(SyncLog.id)
                .limit(5)
                .toList()
        }

        assertEquals(5, firstPage.size, "First page should have 5 entries")

        val lastIdFromFirstPage = firstPage.last()[SyncLog.id].value

        // Get next 5 logs (second page)
        val secondPage = transaction {
            SyncLog.selectAll()
                .where {
                    (SyncLog.schoolId eq testSchoolId!!) and
                    (SyncLog.id greater lastIdFromFirstPage)
                }
                .orderBy(SyncLog.id)
                .limit(5)
                .toList()
        }

        assertEquals(5, secondPage.size, "Second page should have 5 entries")

        // Verify no overlap between pages
        val firstPageIds = firstPage.map { it[SyncLog.id].value }.toSet()
        val secondPageIds = secondPage.map { it[SyncLog.id].value }.toSet()
        assertTrue(firstPageIds.intersect(secondPageIds).isEmpty(), "Pages should not overlap")
    }
}
