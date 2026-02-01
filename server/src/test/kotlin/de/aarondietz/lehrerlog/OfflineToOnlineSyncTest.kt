package de.aarondietz.lehrerlog

import de.aarondietz.lehrerlog.data.SchoolClassDto
import de.aarondietz.lehrerlog.data.StudentDto
import de.aarondietz.lehrerlog.db.DatabaseFactory
import de.aarondietz.lehrerlog.db.tables.*
import de.aarondietz.lehrerlog.services.SchoolClassService
import de.aarondietz.lehrerlog.services.StudentService
import de.aarondietz.lehrerlog.services.SyncService
import de.aarondietz.lehrerlog.sync.PushChangeRequest
import de.aarondietz.lehrerlog.sync.PushChangesRequest
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*
import kotlin.test.*

/**
 * Critical end-to-end test simulating the complete offline-to-online sync workflow:
 * 1. Client creates data "offline" (not immediately sent to server)
 * 2. Client pushes changes when "online"
 * 3. Server processes and creates sync logs
 * 4. Other clients can pull those changes
 * 5. Conflict resolution works correctly
 */
class OfflineToOnlineSyncTest {

    companion object {
        private var testSchoolId: UUID? = null
        private var testUserId: UUID? = null
        private var isInitialized = false

        private val TEST_PREFIX = TestPrefixGenerator.next()
        private val TEST_PREFIX_UPPER = TEST_PREFIX.uppercase()
    }

    private lateinit var syncService: SyncService
    private lateinit var studentService: StudentService
    private lateinit var schoolClassService: SchoolClassService
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
        schoolClassService = SchoolClassService()
        cleanupTestData()

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
    fun `test complete offline creation and sync workflow`() {
        // PHASE 1: Simulate offline creation (client-side)
        // Client creates student locally with UUID
        val offlineStudentId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis().toString()
        val offlineStudent = StudentDto(
            id = offlineStudentId,
            schoolId = testSchoolId.toString(),
            firstName = "Offline",
            lastName = "Student",
            version = 1L,
            createdAt = now,
            updatedAt = now
        )

        // PHASE 2: Client comes online and pushes changes
        val pushRequest = PushChangesRequest(
            changes = listOf(
                PushChangeRequest(
                    entityType = "STUDENT",
                    entityId = offlineStudentId,
                    operation = "CREATE",
                    version = 1L,
                    data = json.encodeToString(offlineStudent)
                )
            )
        )

        val pushResponse = syncService.pushChanges(testSchoolId!!, testUserId!!, pushRequest)

        // Verify push succeeded
        assertEquals(1, pushResponse.successCount)
        assertEquals(0, pushResponse.failureCount)

        // PHASE 3: Verify student exists on server
        val serverStudent = studentService.getStudentsBySchool(testSchoolId!!)
            .find { it.firstName == "Offline" }

        assertNotNull(serverStudent)
        assertEquals("Offline", serverStudent.firstName)
        assertEquals("Student", serverStudent.lastName)

        // PHASE 4: Verify sync log was created
        val syncLogs = transaction {
            SyncLog.selectAll()
                .where {
                    (SyncLog.schoolId eq testSchoolId!!) and
                            (SyncLog.entityType eq EntityType.STUDENT.name)
                }
                .toList()
        }

        assertEquals(1, syncLogs.size)
        assertEquals(SyncOperation.CREATE, syncLogs[0][SyncLog.operation])

        // PHASE 5: Another client pulls changes
        val pullResponse = syncService.getChangesSince(testSchoolId!!, sinceLogId = 0L)

        assertEquals(1, pullResponse.changes.size)
        assertEquals("CREATE", pullResponse.changes[0].operation)

        val pulledStudent = json.decodeFromString<StudentDto>(pullResponse.changes[0].data!!)
        assertEquals("Offline", pulledStudent.firstName)
    }

    @Test
    fun `test offline update with conflict resolution`() {
        // Create initial student on server
        val student = studentService.createStudent(
            schoolId = testSchoolId!!,
            firstName = "Original",
            lastName = "Name",
            classIds = emptyList(),
            userId = testUserId!!
        )

        // Simulate two clients with same initial data
        val client1LastSync = syncService.getChangesSince(testSchoolId!!, 0L).lastSyncId

        // SCENARIO: Both clients go offline and make changes

        // Client 1: Updates student (has correct version)
        val now1 = System.currentTimeMillis().toString()
        val client1Update = StudentDto(
            id = student.id,
            schoolId = testSchoolId.toString(),
            firstName = "Client1",
            lastName = "Update",
            version = student.version, // Version 1
            createdAt = now1,
            updatedAt = now1
        )

        // Client 2: Also updates student (also has version 1)
        val now2 = System.currentTimeMillis().toString()
        val client2Update = StudentDto(
            id = student.id,
            schoolId = testSchoolId.toString(),
            firstName = "Client2",
            lastName = "Update",
            version = student.version, // Also version 1
            createdAt = now2,
            updatedAt = now2
        )

        // Client 1 comes online first and pushes
        val client1Push = PushChangesRequest(
            changes = listOf(
                PushChangeRequest(
                    entityType = "STUDENT",
                    entityId = student.id,
                    operation = "UPDATE",
                    version = student.version,
                    data = json.encodeToString(client1Update)
                )
            )
        )

        val client1Response = syncService.pushChanges(testSchoolId!!, testUserId!!, client1Push)
        assertEquals(1, client1Response.successCount)

        // Verify server has Client1's update and version is now 2
        val afterClient1 = studentService.getStudent(UUID.fromString(student.id), testSchoolId!!)
        assertEquals("Client1", afterClient1?.firstName)
        assertEquals(2L, afterClient1?.version)

        // Client 2 comes online and tries to push (should get conflict)
        val client2Push = PushChangesRequest(
            changes = listOf(
                PushChangeRequest(
                    entityType = "STUDENT",
                    entityId = student.id,
                    operation = "UPDATE",
                    version = student.version, // Still version 1
                    data = json.encodeToString(client2Update)
                )
            )
        )

        val client2Response = syncService.pushChanges(testSchoolId!!, testUserId!!, client2Push)

        // Verify conflict was detected
        assertEquals(0, client2Response.successCount)
        assertEquals(1, client2Response.failureCount)
        assertTrue(client2Response.results[0].conflict)

        // Verify server still has Client1's data (Client2 was rejected)
        val finalState = studentService.getStudent(UUID.fromString(student.id), testSchoolId!!)
        assertEquals("Client1", finalState?.firstName)
        assertEquals(2L, finalState?.version)

        // Client 2 should now pull changes, see the conflict, and resolve
        val client2Pull = syncService.getChangesSince(testSchoolId!!, client1LastSync)
        assertEquals(1, client2Pull.changes.size)
        assertEquals("UPDATE", client2Pull.changes[0].operation)

        val serverVersion = json.decodeFromString<StudentDto>(client2Pull.changes[0].data!!)
        assertEquals("Client1", serverVersion.firstName)
        assertEquals(2L, serverVersion.version)
    }

    @Test
    fun `test offline delete syncs correctly`() {
        // Create student
        val student = studentService.createStudent(
            schoolId = testSchoolId!!,
            firstName = "ToDelete",
            lastName = "Offline",
            classIds = emptyList(),
            userId = testUserId!!
        )

        val initialSync = syncService.getChangesSince(testSchoolId!!, 0L)
        assertEquals(1, initialSync.changes.size) // CREATE log

        // Client deletes offline
        val deleteRequest = PushChangesRequest(
            changes = listOf(
                PushChangeRequest(
                    entityType = "STUDENT",
                    entityId = student.id,
                    operation = "DELETE",
                    version = 1L,
                    data = null
                )
            )
        )

        val deleteResponse = syncService.pushChanges(testSchoolId!!, testUserId!!, deleteRequest)
        assertEquals(1, deleteResponse.successCount)

        // Verify student is deleted on server
        val deleted = studentService.getStudent(UUID.fromString(student.id), testSchoolId!!)
        assertNull(deleted)

        // Verify DELETE sync log exists
        val syncLogs = transaction {
            SyncLog.selectAll()
                .where {
                    (SyncLog.schoolId eq testSchoolId!!) and
                            (SyncLog.entityId eq UUID.fromString(student.id))
                }
                .orderBy(SyncLog.id)
                .toList()
        }

        assertEquals(2, syncLogs.size) // CREATE + DELETE
        assertEquals(SyncOperation.CREATE, syncLogs[0][SyncLog.operation])
        assertEquals(SyncOperation.DELETE, syncLogs[1][SyncLog.operation])

        // Other client pulls changes
        val pullAfterDelete = syncService.getChangesSince(testSchoolId!!, initialSync.lastSyncId)
        assertEquals(1, pullAfterDelete.changes.size)
        assertEquals("DELETE", pullAfterDelete.changes[0].operation)
        assertNull(pullAfterDelete.changes[0].data) // DELETE has no data
    }

    @Test
    fun `test batch offline operations sync in order`() {
        // Simulate multiple offline operations
        val student1Id = UUID.randomUUID().toString()
        val student2Id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis().toString()

        val batchRequest = PushChangesRequest(
            changes = listOf(
                // Create student 1
                PushChangeRequest(
                    entityType = "STUDENT",
                    entityId = student1Id,
                    operation = "CREATE",
                    version = 1L,
                    data = json.encodeToString(
                        StudentDto(student1Id, testSchoolId.toString(), "Student", "One", emptyList(), 1L, now, now)
                    )
                ),
                // Create student 2
                PushChangeRequest(
                    entityType = "STUDENT",
                    entityId = student2Id,
                    operation = "CREATE",
                    version = 1L,
                    data = json.encodeToString(
                        StudentDto(student2Id, testSchoolId.toString(), "Student", "Two", emptyList(), 1L, now, now)
                    )
                ),
                // Update student 1
                PushChangeRequest(
                    entityType = "STUDENT",
                    entityId = student1Id,
                    operation = "UPDATE",
                    version = 1L,
                    data = json.encodeToString(
                        StudentDto(student1Id, testSchoolId.toString(), "Updated", "One", emptyList(), 1L, now, now)
                    )
                )
            )
        )

        val response = syncService.pushChanges(testSchoolId!!, testUserId!!, batchRequest)

        // All operations should succeed
        assertEquals(3, response.successCount)
        assertEquals(0, response.failureCount)

        // Verify final state
        val students = studentService.getStudentsBySchool(testSchoolId!!)
        assertEquals(2, students.size)

        val updatedStudent1 = students.find { it.lastName == "One" }
        assertNotNull(updatedStudent1)
        assertEquals("Updated", updatedStudent1.firstName)
        assertEquals(2L, updatedStudent1.version) // Version incremented by update

        val student2 = students.find { it.lastName == "Two" }
        assertNotNull(student2)
        assertEquals(1L, student2.version) // Only created, not updated
    }

    @Test
    fun `test complete offline SchoolClass creation and sync workflow`() {
        // PHASE 1: Simulate offline creation (client-side)
        // Client creates school class locally with UUID
        val offlineClassId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis().toString()
        val offlineClass = SchoolClassDto(
            id = offlineClassId,
            schoolId = testSchoolId.toString(),
            name = "Offline Class 5A",
            alternativeName = "Test Class 5A",
            studentCount = 0,
            version = 1L,
            createdAt = now,
            updatedAt = now
        )

        // PHASE 2: Client comes online and pushes changes
        val pushRequest = PushChangesRequest(
            changes = listOf(
                PushChangeRequest(
                    entityType = "SCHOOL_CLASS",
                    entityId = offlineClassId,
                    operation = "CREATE",
                    version = 1L,
                    data = json.encodeToString(offlineClass)
                )
            )
        )

        val pushResponse = syncService.pushChanges(testSchoolId!!, testUserId!!, pushRequest)

        // Verify push succeeded
        assertEquals(1, pushResponse.successCount, "Push should succeed")
        assertEquals(0, pushResponse.failureCount, "No failures expected")

        // PHASE 3: Verify class exists on server
        val serverClass = schoolClassService.getClassesBySchool(testSchoolId!!)
            .find { it.name == "Offline Class 5A" }

        assertNotNull(serverClass, "Class should exist on server")
        assertEquals("Offline Class 5A", serverClass.name)
        assertEquals("Test Class 5A", serverClass.alternativeName)
        assertEquals(offlineClassId, serverClass.id, "Server should preserve client UUID")

        // PHASE 4: Verify sync log was created
        val syncLogs = transaction {
            SyncLog.selectAll()
                .where {
                    (SyncLog.schoolId eq testSchoolId!!) and
                            (SyncLog.entityType eq EntityType.SCHOOL_CLASS.name)
                }
                .toList()
        }

        assertEquals(1, syncLogs.size, "Should have one sync log entry")
        assertEquals(SyncOperation.CREATE, syncLogs[0][SyncLog.operation])

        // PHASE 5: Another client pulls changes
        val pullResponse = syncService.getChangesSince(testSchoolId!!, sinceLogId = 0L)

        assertEquals(1, pullResponse.changes.size, "Should have one change to pull")
        assertEquals("CREATE", pullResponse.changes[0].operation)

        val pulledClass = json.decodeFromString<SchoolClassDto>(pullResponse.changes[0].data!!)
        assertEquals("Offline Class 5A", pulledClass.name)
        assertEquals(offlineClassId, pulledClass.id, "Pulled data should have same UUID")
    }
}
