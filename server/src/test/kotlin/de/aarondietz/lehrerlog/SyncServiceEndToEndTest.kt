package de.aarondietz.lehrerlog

import de.aarondietz.lehrerlog.data.StudentDto
import de.aarondietz.lehrerlog.db.DatabaseFactory
import de.aarondietz.lehrerlog.db.tables.*
import de.aarondietz.lehrerlog.services.StudentService
import de.aarondietz.lehrerlog.services.SyncService
import de.aarondietz.lehrerlog.sync.PushChangeRequest
import de.aarondietz.lehrerlog.sync.PushChangesRequest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*
import kotlin.test.*

/**
 * End-to-end tests for SyncService.
 * Tests the complete sync workflow: pull changes, push changes, conflict resolution.
 */
class SyncServiceEndToEndTest {

    companion object {
        private var testSchoolId: UUID? = null
        private var testUserId: UUID? = null
        private var isInitialized = false

        private val TEST_PREFIX = "testing${(10000..99999).random()}"
        private val TEST_PREFIX_UPPER = TEST_PREFIX.uppercase()
    }

    private lateinit var syncService: SyncService
    private lateinit var studentService: StudentService
    private val json = Json { ignoreUnknownKeys = true }

    @BeforeTest
    fun setup() {
        if (!isInitialized) {
            DatabaseFactory.init()
            isInitialized = true
            println("Test run using prefix: $TEST_PREFIX")
        }

        syncService = SyncService()
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
                SyncLog.deleteWhere { SyncLog.schoolId inList testSchoolIds }
                Students.deleteWhere { Students.schoolId inList testSchoolIds }
                SchoolClasses.deleteWhere { SchoolClasses.schoolId inList testSchoolIds }
            }

            Users.deleteWhere { Users.email like emailPattern }
            Schools.deleteWhere { Schools.code like schoolCodePattern }
        }
    }

    @Test
    fun `test pull changes returns empty list when no changes exist`() {
        val response = syncService.getChangesSince(testSchoolId!!, sinceLogId = 0L)

        assertEquals(0, response.changes.size)
        assertEquals(0L, response.lastSyncId)
        assertFalse(response.hasMore)
    }

    @Test
    fun `test pull changes returns all changes since last sync`() {
        // Create 3 students to generate sync log entries
        studentService.createStudent(
            schoolId = testSchoolId!!,
            firstName = "Student",
            lastName = "One",
            userId = testUserId!!
        )

        studentService.createStudent(
            schoolId = testSchoolId!!,
            firstName = "Student",
            lastName = "Two",
            userId = testUserId!!
        )

        studentService.createStudent(
            schoolId = testSchoolId!!,
            firstName = "Student",
            lastName = "Three",
            userId = testUserId!!
        )

        // Pull all changes
        val response = syncService.getChangesSince(testSchoolId!!, sinceLogId = 0L)

        assertEquals(3, response.changes.size)
        assertTrue(response.lastSyncId > 0)
        assertFalse(response.hasMore)

        // Verify all changes are CREATE operations
        assertTrue(response.changes.all { it.operation == "CREATE" })
        assertTrue(response.changes.all { it.entityType == "STUDENT" })

        // Verify all changes have data
        assertTrue(response.changes.all { it.data != null })
    }

    @Test
    fun `test pull changes only returns changes since specific ID`() {
        // Create students
        studentService.createStudent(
            schoolId = testSchoolId!!,
            firstName = "First",
            lastName = "Student",
            userId = testUserId!!
        )

        studentService.createStudent(
            schoolId = testSchoolId!!,
            firstName = "Second",
            lastName = "Student",
            userId = testUserId!!
        )

        // Get first sync
        val firstSync = syncService.getChangesSince(testSchoolId!!, sinceLogId = 0L)
        assertEquals(2, firstSync.changes.size)

        // Create another student
        studentService.createStudent(
            schoolId = testSchoolId!!,
            firstName = "Third",
            lastName = "Student",
            userId = testUserId!!
        )

        // Pull only new changes
        val secondSync = syncService.getChangesSince(testSchoolId!!, sinceLogId = firstSync.lastSyncId)

        assertEquals(1, secondSync.changes.size)
        assertEquals("Third", json.decodeFromString<StudentDto>(secondSync.changes[0].data!!).firstName)
    }

    @Test
    fun `test pull changes includes DELETE operations without data`() {
        // Create and delete a student
        val student = studentService.createStudent(
            schoolId = testSchoolId!!,
            firstName = "ToDelete",
            lastName = "Student",
            userId = testUserId!!
        )

        studentService.deleteStudent(
            studentId = UUID.fromString(student.id),
            schoolId = testSchoolId!!,
            userId = testUserId!!
        )

        // Pull changes
        val response = syncService.getChangesSince(testSchoolId!!, sinceLogId = 0L)

        assertEquals(2, response.changes.size) // CREATE + DELETE

        val deleteChange = response.changes.find { it.operation == "DELETE" }
        assertNotNull(deleteChange)
        assertNull(deleteChange.data, "DELETE operations should not have data")
    }

    @Test
    fun `test push changes creates new student on server`() {
        val newStudentId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis().toString()
        val studentDto = StudentDto(
            id = newStudentId,
            schoolId = testSchoolId.toString(),
            firstName = "Pushed",
            lastName = "Student",
            version = 1L,
            createdAt = now,
            updatedAt = now
        )

        val pushRequest = PushChangesRequest(
            changes = listOf(
                PushChangeRequest(
                    entityType = "STUDENT",
                    entityId = newStudentId,
                    operation = "CREATE",
                    version = 1L,
                    data = json.encodeToString(studentDto)
                )
            )
        )

        val response = syncService.pushChanges(testSchoolId!!, testUserId!!, pushRequest)

        assertEquals(1, response.successCount)
        assertEquals(0, response.failureCount)
        assertTrue(response.results[0].success)

        // Verify student exists on server
        val savedStudent = studentService.getStudentsBySchool(testSchoolId!!)
            .find { it.firstName == "Pushed" && it.lastName == "Student" }

        assertNotNull(savedStudent)
        assertEquals("Pushed", savedStudent.firstName)
    }

    @Test
    fun `test push changes with version conflict is rejected`() {
        // Create a student
        val student = studentService.createStudent(
            schoolId = testSchoolId!!,
            firstName = "Original",
            lastName = "Student",
            userId = testUserId!!
        )

        // Try to push update with wrong version
        val now = System.currentTimeMillis().toString()
        val studentDto = StudentDto(
            id = student.id,
            schoolId = testSchoolId.toString(),
            firstName = "Updated",
            lastName = "Student",
            version = 999L, // Wrong version
            createdAt = now,
            updatedAt = now
        )

        val pushRequest = PushChangesRequest(
            changes = listOf(
                PushChangeRequest(
                    entityType = "STUDENT",
                    entityId = student.id,
                    operation = "UPDATE",
                    version = 999L,
                    data = json.encodeToString(studentDto)
                )
            )
        )

        val response = syncService.pushChanges(testSchoolId!!, testUserId!!, pushRequest)

        assertEquals(0, response.successCount)
        assertEquals(1, response.failureCount)
        assertFalse(response.results[0].success)
        assertTrue(response.results[0].conflict)
        assertEquals("Version conflict", response.results[0].errorMessage)

        // Verify student was NOT updated
        val savedStudent = studentService.getStudent(UUID.fromString(student.id), testSchoolId!!)
        assertEquals("Original", savedStudent?.firstName)
    }

    @Test
    fun `test push changes updates existing student with correct version`() {
        // Create a student
        val student = studentService.createStudent(
            schoolId = testSchoolId!!,
            firstName = "Original",
            lastName = "Student",
            userId = testUserId!!
        )

        // Push update with correct version
        val now = System.currentTimeMillis().toString()
        val studentDto = StudentDto(
            id = student.id,
            schoolId = testSchoolId.toString(),
            firstName = "Updated",
            lastName = "Student",
            version = student.version,
            createdAt = now,
            updatedAt = now
        )

        val pushRequest = PushChangesRequest(
            changes = listOf(
                PushChangeRequest(
                    entityType = "STUDENT",
                    entityId = student.id,
                    operation = "UPDATE",
                    version = student.version,
                    data = json.encodeToString(studentDto)
                )
            )
        )

        val response = syncService.pushChanges(testSchoolId!!, testUserId!!, pushRequest)

        assertEquals(1, response.successCount)
        assertEquals(0, response.failureCount)
        assertTrue(response.results[0].success)

        // Verify student was updated
        val savedStudent = studentService.getStudent(UUID.fromString(student.id), testSchoolId!!)
        assertEquals("Updated", savedStudent?.firstName)
        assertEquals(2L, savedStudent?.version) // Version should be incremented
    }

    @Test
    fun `test push changes with multiple operations processes all`() {
        // Create two students
        val student1 = studentService.createStudent(
            schoolId = testSchoolId!!,
            firstName = "Student",
            lastName = "One",
            userId = testUserId!!
        )

        val student2Id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis().toString()
        val student2Dto = StudentDto(
            id = student2Id,
            schoolId = testSchoolId.toString(),
            firstName = "Student",
            lastName = "Two",
            version = 1L,
            createdAt = now,
            updatedAt = now
        )

        // Push: DELETE first student, CREATE second student
        val pushRequest = PushChangesRequest(
            changes = listOf(
                PushChangeRequest(
                    entityType = "STUDENT",
                    entityId = student1.id,
                    operation = "DELETE",
                    version = 1L,
                    data = null
                ),
                PushChangeRequest(
                    entityType = "STUDENT",
                    entityId = student2Id,
                    operation = "CREATE",
                    version = 1L,
                    data = json.encodeToString(student2Dto)
                )
            )
        )

        val response = syncService.pushChanges(testSchoolId!!, testUserId!!, pushRequest)

        assertEquals(2, response.successCount)
        assertEquals(0, response.failureCount)

        // Verify first student is deleted
        val deletedStudent = studentService.getStudent(UUID.fromString(student1.id), testSchoolId!!)
        assertNull(deletedStudent)

        // Verify second student was created
        val createdStudent = studentService.getStudentsBySchool(testSchoolId!!)
            .find { it.firstName == "Student" && it.lastName == "Two" }
        assertNotNull(createdStudent)
    }

    @Test
    fun `test pull changes respects school isolation`() {
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
            firstName = "TestSchool",
            lastName = "Student",
            userId = testUserId!!
        )

        // Create student in other school
        studentService.createStudent(
            schoolId = otherSchoolId,
            firstName = "OtherSchool",
            lastName = "Student",
            userId = testUserId!!
        )

        // Pull changes for test school
        val response = syncService.getChangesSince(testSchoolId!!, sinceLogId = 0L)

        // Should only get 1 change (from test school)
        assertEquals(1, response.changes.size)
        val studentData = json.decodeFromString<StudentDto>(response.changes[0].data!!)
        assertEquals("TestSchool", studentData.firstName)

        // Cleanup
        transaction {
            SyncLog.deleteWhere { SyncLog.schoolId eq otherSchoolId }
            Students.deleteWhere { Students.schoolId eq otherSchoolId }
            Schools.deleteWhere { Schools.id eq otherSchoolId }
        }
    }

    @Test
    fun `test pull changes pagination with hasMore flag`() {
        // Create more than MAX_CHANGES_PER_REQUEST (100) students
        repeat(105) { index ->
            studentService.createStudent(
                schoolId = testSchoolId!!,
                firstName = "Student",
                lastName = "Number$index",
                userId = testUserId!!
            )
        }

        // First pull should get 100 changes
        val firstPage = syncService.getChangesSince(testSchoolId!!, sinceLogId = 0L)

        assertEquals(100, firstPage.changes.size)
        assertTrue(firstPage.hasMore, "Should indicate more changes available")

        // Second pull should get remaining 5 changes
        val secondPage = syncService.getChangesSince(testSchoolId!!, sinceLogId = firstPage.lastSyncId)

        assertEquals(5, secondPage.changes.size)
        assertFalse(secondPage.hasMore, "Should indicate no more changes")
    }
}
