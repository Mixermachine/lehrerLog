package de.aarondietz.lehrerlog

import de.aarondietz.lehrerlog.db.DatabaseFactory
import de.aarondietz.lehrerlog.db.tables.*
import de.aarondietz.lehrerlog.services.ClassUpdateResult
import de.aarondietz.lehrerlog.services.SchoolClassService
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
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
 * End-to-end tests for SchoolClassService with sync logging.
 * Tests verify CRUD operations and automatic sync log entries.
 */
class SchoolClassServiceEndToEndTest {

    companion object {
        private var testSchoolId: UUID? = null
        private var testUserId: UUID? = null
        private var isInitialized = false

        private val TEST_PREFIX = "testing${(10000..99999).random()}"
        private val TEST_PREFIX_UPPER = TEST_PREFIX.uppercase()
    }

    private lateinit var schoolClassService: SchoolClassService

    @BeforeTest
    fun setup() {
        if (!isInitialized) {
            DatabaseFactory.init()
            isInitialized = true
            println("Test run using prefix: $TEST_PREFIX")
        }

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

                // Delete classes from test schools
                SchoolClasses.deleteWhere { SchoolClasses.schoolId inList testSchoolIds }
            }

            Users.deleteWhere { Users.email like emailPattern }
            Schools.deleteWhere { Schools.code like schoolCodePattern }
        }
    }

    @Test
    fun `test create class creates sync log entry`() {
        val schoolClass = schoolClassService.createClass(
            schoolId = testSchoolId!!,
            name = "5A",
            alternativeName = "Class 5A",
            userId = testUserId!!
        )

        assertNotNull(schoolClass.id)
        assertEquals("5A", schoolClass.name)
        assertEquals("Class 5A", schoolClass.alternativeName)
        assertEquals(1L, schoolClass.version)

        // Verify sync log entry was created
        val syncLogs = transaction {
            SyncLog.selectAll()
                .where {
                    (SyncLog.schoolId eq testSchoolId!!) and
                            (SyncLog.entityType eq EntityType.SCHOOL_CLASS.name) and
                            (SyncLog.entityId eq UUID.fromString(schoolClass.id))
                }
                .toList()
        }

        assertEquals(1, syncLogs.size, "Should have exactly one sync log entry")
        val log = syncLogs.first()
        assertEquals(SyncOperation.CREATE, log[SyncLog.operation])
        assertEquals(testUserId, log[SyncLog.userId].value)
    }

    @Test
    fun `test update class creates sync log entry and increments version`() {
        // Create a class
        val schoolClass = schoolClassService.createClass(
            schoolId = testSchoolId!!,
            name = "6B",
            alternativeName = "Class 6B",
            userId = testUserId!!
        )

        // Update the class
        val result = schoolClassService.updateClass(
            classId = UUID.fromString(schoolClass.id),
            schoolId = testSchoolId!!,
            name = "6B Advanced",
            alternativeName = "Class 6B - Advanced Studies",
            version = schoolClass.version,
            userId = testUserId!!
        )

        assertTrue(result is ClassUpdateResult.Success)
        val updated = (result as ClassUpdateResult.Success).data
        assertEquals("6B Advanced", updated.name)
        assertEquals("Class 6B - Advanced Studies", updated.alternativeName)
        assertEquals(2L, updated.version, "Version should increment")

        // Verify sync log entries (CREATE and UPDATE)
        val syncLogs = transaction {
            SyncLog.selectAll()
                .where {
                    (SyncLog.schoolId eq testSchoolId!!) and
                            (SyncLog.entityType eq EntityType.SCHOOL_CLASS.name) and
                            (SyncLog.entityId eq UUID.fromString(schoolClass.id))
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
        val schoolClass = schoolClassService.createClass(
            schoolId = testSchoolId!!,
            name = "7C",
            alternativeName = "Class 7C",
            userId = testUserId!!
        )

        // Try to update with wrong version
        val result = schoolClassService.updateClass(
            classId = UUID.fromString(schoolClass.id),
            schoolId = testSchoolId!!,
            name = "7C Updated",
            alternativeName = "Class 7C - Updated",
            version = 999L, // Wrong version
            userId = testUserId!!
        )

        assertTrue(result is ClassUpdateResult.VersionConflict)
    }

    @Test
    fun `test delete class creates sync log entry`() {
        val schoolClass = schoolClassService.createClass(
            schoolId = testSchoolId!!,
            name = "8D",
            alternativeName = "Class 8D",
            userId = testUserId!!
        )

        val deleted = schoolClassService.deleteClass(
            classId = UUID.fromString(schoolClass.id),
            schoolId = testSchoolId!!,
            userId = testUserId!!
        )

        assertTrue(deleted, "Class should be deleted")

        // Verify class is gone
        val found = schoolClassService.getClass(UUID.fromString(schoolClass.id), testSchoolId!!)
        assertNull(found, "Class should not be found after deletion")

        // Verify sync log entries (CREATE and DELETE)
        val syncLogs = transaction {
            SyncLog.selectAll()
                .where {
                    (SyncLog.schoolId eq testSchoolId!!) and
                            (SyncLog.entityType eq EntityType.SCHOOL_CLASS.name) and
                            (SyncLog.entityId eq UUID.fromString(schoolClass.id))
                }
                .orderBy(SyncLog.id)
                .toList()
        }

        assertEquals(2, syncLogs.size, "Should have CREATE and DELETE entries")
        assertEquals(SyncOperation.CREATE, syncLogs[0][SyncLog.operation])
        assertEquals(SyncOperation.DELETE, syncLogs[1][SyncLog.operation])
    }

    @Test
    fun `test school isolation - cannot access other school classes`() {
        // Create another school
        val otherSchoolId = transaction {
            Schools.insert {
                it[name] = "$TEST_PREFIX Other School"
                it[code] = "${TEST_PREFIX_UPPER}_OTHER"
            } get Schools.id
        }.value

        // Create class in other school
        val schoolClass = schoolClassService.createClass(
            schoolId = otherSchoolId,
            name = "9E",
            alternativeName = "Class 9E",
            userId = testUserId!!
        )

        // Try to get class using wrong school ID
        val found = schoolClassService.getClass(UUID.fromString(schoolClass.id), testSchoolId!!)
        assertNull(found, "Should not find class from different school")

        // Try to delete class using wrong school ID
        val deleted = schoolClassService.deleteClass(
            classId = UUID.fromString(schoolClass.id),
            schoolId = testSchoolId!!,
            userId = testUserId!!
        )
        assertFalse(deleted, "Should not delete class from different school")

        // Cleanup
        transaction {
            SchoolClasses.deleteWhere { SchoolClasses.schoolId eq otherSchoolId }
            Schools.deleteWhere { Schools.id eq otherSchoolId }
        }
    }

    @Test
    fun `test get classes by school returns all classes for that school`() {
        // Create multiple classes
        val class1 = schoolClassService.createClass(
            schoolId = testSchoolId!!,
            name = "10A",
            alternativeName = "Class 10A",
            userId = testUserId!!
        )

        val class2 = schoolClassService.createClass(
            schoolId = testSchoolId!!,
            name = "10B",
            alternativeName = "Class 10B",
            userId = testUserId!!
        )

        val classes = schoolClassService.getClassesBySchool(testSchoolId!!)

        assertTrue(classes.size >= 2, "Should have at least 2 classes")
        assertTrue(classes.any { it.id == class1.id })
        assertTrue(classes.any { it.id == class2.id })
    }
}
