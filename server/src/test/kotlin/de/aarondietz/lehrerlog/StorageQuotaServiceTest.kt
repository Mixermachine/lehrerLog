package de.aarondietz.lehrerlog

import de.aarondietz.lehrerlog.db.DatabaseFactory
import de.aarondietz.lehrerlog.db.tables.Schools
import de.aarondietz.lehrerlog.db.tables.StorageOwnerType
import de.aarondietz.lehrerlog.db.tables.StoragePlans
import de.aarondietz.lehrerlog.db.tables.StorageSubscriptions
import de.aarondietz.lehrerlog.db.tables.StorageUsage
import de.aarondietz.lehrerlog.db.tables.UserRole
import de.aarondietz.lehrerlog.db.tables.Users
import de.aarondietz.lehrerlog.services.StorageService
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class StorageQuotaServiceTest {

    private val storageService = StorageService()
    private var schoolId: UUID? = null
    private var userId: UUID? = null
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
            val schoolEntityId = EntityID(schoolId!!, Schools)
            userId = Users.insertAndGetId {
                it[email] = "$suffix.user@example.com"
                it[passwordHash] = "test"
                it[firstName] = "Test"
                it[lastName] = "User"
                it[role] = UserRole.TEACHER
                it[Users.schoolId] = schoolEntityId
                it[isActive] = true
            }.value

            val defaultPlanId = UUID.fromString("00000000-0000-0000-0000-000000000001")
            StoragePlans.insertIgnore {
                it[id] = defaultPlanId
                it[name] = "Default"
                it[maxTotalBytes] = 100L * 1024L * 1024L
                it[maxFileBytes] = 5L * 1024L * 1024L
            }

            StorageSubscriptions.insert {
                it[id] = schoolId!!
                it[ownerType] = StorageOwnerType.SCHOOL.name
                it[ownerId] = schoolId!!
                it[planId] = defaultPlanId
                it[active] = true
            }

            StorageUsage.insert {
                it[ownerType] = StorageOwnerType.SCHOOL.name
                it[ownerId] = schoolId!!
                it[usedTotalBytes] = 1024L
            }
        }
    }

    @AfterTest
    fun teardown() {
        transaction {
            val schoolIdValue = schoolId
            if (schoolIdValue != null) {
                StorageUsage.deleteWhere {
                    (StorageUsage.ownerType eq StorageOwnerType.SCHOOL.name) and (StorageUsage.ownerId eq schoolIdValue)
                }
                StorageSubscriptions.deleteWhere { StorageSubscriptions.id eq schoolIdValue }
                Schools.deleteWhere { Schools.id eq schoolIdValue }
            }
            val userIdValue = userId
            if (userIdValue != null) {
                Users.deleteWhere { Users.id eq userIdValue }
            }
        }
    }

    @Test
    fun `quota resolves default plan and usage`() {
        val quota = storageService.getQuota(userId!!, schoolId!!)
        assertNotNull(quota.planId)
        assertEquals(100L * 1024L * 1024L, quota.maxTotalBytes)
        assertEquals(5L * 1024L * 1024L, quota.maxFileBytes)
        assertEquals(1024L, quota.usedTotalBytes)
        assertEquals(quota.maxTotalBytes - quota.usedTotalBytes, quota.remainingBytes)
    }

    @Test
    fun `reserveBytes increments usage`() {
        val quota = storageService.reserveBytes(userId!!, schoolId!!, 2048L)
        assertEquals(1024L + 2048L, quota.usedTotalBytes)

        val updated = storageService.getQuota(userId!!, schoolId!!)
        assertEquals(1024L + 2048L, updated.usedTotalBytes)
    }

    @Test
    fun `reserveBytes rejects oversized file`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            storageService.reserveBytes(userId!!, schoolId!!, 5L * 1024L * 1024L + 1L)
        }
        assertEquals("FILE_TOO_LARGE", exception.message)
    }

    @Test
    fun `reserveBytes rejects when quota exceeded`() {
        transaction {
            StorageUsage.update({
                (StorageUsage.ownerType eq StorageOwnerType.SCHOOL.name) and
                    (StorageUsage.ownerId eq schoolId!!)
            }) {
                it[StorageUsage.usedTotalBytes] = 100L * 1024L * 1024L - 512L
            }
        }

        val exception = assertFailsWith<IllegalArgumentException> {
            storageService.reserveBytes(userId!!, schoolId!!, 1024L)
        }
        assertEquals("QUOTA_EXCEEDED", exception.message)
    }
}
