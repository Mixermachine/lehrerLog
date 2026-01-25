package de.aarondietz.lehrerlog

import de.aarondietz.lehrerlog.data.LateStatus
import de.aarondietz.lehrerlog.data.ResolvePunishmentRequest
import de.aarondietz.lehrerlog.db.DatabaseFactory
import de.aarondietz.lehrerlog.db.tables.Schools
import de.aarondietz.lehrerlog.db.tables.Students
import de.aarondietz.lehrerlog.db.tables.PunishmentRecords
import de.aarondietz.lehrerlog.db.tables.LatePeriods
import de.aarondietz.lehrerlog.db.tables.StudentLateStats
import de.aarondietz.lehrerlog.db.tables.TeacherLatePolicy
import de.aarondietz.lehrerlog.db.tables.Users
import de.aarondietz.lehrerlog.db.tables.UserRole
import de.aarondietz.lehrerlog.services.LatePolicyService
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.dao.id.EntityID
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class LatePolicyServiceTest {

    private val latePolicyService = LatePolicyService()
    private var schoolId: UUID? = null
    private var teacherId: UUID? = null
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
            val schoolEntityId = EntityID(schoolId!!, Schools)
            teacherId = Users.insertAndGetId {
                it[email] = "$suffix.teacher@example.com"
                it[passwordHash] = "test"
                it[firstName] = "Test"
                it[lastName] = "Teacher"
                it[role] = UserRole.TEACHER
                it[Users.schoolId] = schoolEntityId
                it[isActive] = true
            }.value
            val teacherEntityId = EntityID(teacherId!!, Users)
            studentId = Students.insertAndGetId {
                it[Students.schoolId] = schoolEntityId
                it[firstName] = "Student"
                it[lastName] = "One"
                it[createdBy] = teacherEntityId
            }.value
        }
    }

    @AfterTest
    fun teardown() {
        transaction {
            val studentIdValue = studentId
            val teacherIdValue = teacherId
            if (studentIdValue != null && teacherIdValue != null) {
                PunishmentRecords.deleteWhere {
                    (PunishmentRecords.teacherId eq teacherIdValue) and (PunishmentRecords.studentId eq studentIdValue)
                }
                StudentLateStats.deleteWhere {
                    (StudentLateStats.teacherId eq teacherIdValue) and (StudentLateStats.studentId eq studentIdValue)
                }
            }
            teacherIdValue?.let { id ->
                LatePeriods.deleteWhere { LatePeriods.teacherId eq id }
                TeacherLatePolicy.deleteWhere { TeacherLatePolicy.teacherId eq id }
            }
            studentIdValue?.let { id -> Students.deleteWhere { Students.id eq id } }
            teacherId?.let { id -> Users.deleteWhere { Users.id eq id } }
            schoolId?.let { id -> Schools.deleteWhere { Schools.id eq id } }
        }
    }

    @Test
    fun `late decisions update stats and punishment`() {
        val periodId = latePolicyService.applyLateDecision(
            teacherId = teacherId!!,
            studentId = studentId!!,
            lateStatus = LateStatus.LATE_PUNISH
        )
        latePolicyService.applyLateDecision(
            teacherId = teacherId!!,
            studentId = studentId!!,
            lateStatus = LateStatus.LATE_PUNISH
        )
        latePolicyService.applyLateDecision(
            teacherId = teacherId!!,
            studentId = studentId!!,
            lateStatus = LateStatus.LATE_PUNISH
        )

        val stats = latePolicyService.getStatsForStudent(teacherId!!, studentId!!, periodId)
        assertNotNull(stats)
        assertEquals(3, stats.totalMissed)
        assertEquals(3, stats.missedSincePunishment)
        assertEquals(true, stats.punishmentRequired)

        val resolved = latePolicyService.resolvePunishment(
            teacherId = teacherId!!,
            resolvedBy = teacherId!!,
            request = ResolvePunishmentRequest(
                studentId = studentId!!.toString(),
                periodId = periodId.toString(),
                note = "Handled"
            )
        )
        assertNotNull(resolved)

        val updatedStats = latePolicyService.getStatsForStudent(teacherId!!, studentId!!, periodId)
        assertNotNull(updatedStats)
        assertEquals(0, updatedStats.missedSincePunishment)
        assertEquals(false, updatedStats.punishmentRequired)
    }
}
