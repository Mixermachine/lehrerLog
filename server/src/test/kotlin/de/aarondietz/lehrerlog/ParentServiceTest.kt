package de.aarondietz.lehrerlog

import de.aarondietz.lehrerlog.data.ParentInviteCreateRequest
import de.aarondietz.lehrerlog.data.ParentInviteRedeemRequest
import de.aarondietz.lehrerlog.data.ParentLinkStatus
import de.aarondietz.lehrerlog.db.DatabaseFactory
import de.aarondietz.lehrerlog.db.tables.*
import de.aarondietz.lehrerlog.services.ParentService
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*
import kotlin.test.*

class ParentServiceTest {

    private val parentService = ParentService()
    private var schoolId: UUID? = null
    private var teacherId: UUID? = null
    private var studentId: UUID? = null
    private var parentUserId: UUID? = null
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
                ParentStudentLinks.deleteWhere {
                    (ParentStudentLinks.studentId eq studentIdValue) and (ParentStudentLinks.createdBy eq teacherIdValue)
                }
                ParentInvites.deleteWhere {
                    (ParentInvites.studentId eq studentIdValue) and (ParentInvites.createdBy eq teacherIdValue)
                }
            }
            parentUserId?.let { id ->
                ParentStudentLinks.deleteWhere { ParentStudentLinks.parentUserId eq id }
                Users.deleteWhere { Users.id eq id }
            }
            studentIdValue?.let { id -> Students.deleteWhere { Students.id eq id } }
            teacherIdValue?.let { id -> Users.deleteWhere { Users.id eq id } }
            schoolId?.let { id -> Schools.deleteWhere { Schools.id eq id } }
        }
    }

    @Test
    fun `create invite and redeem creates parent link`() {
        val response = parentService.createInvite(
            teacherId = teacherId!!,
            schoolId = schoolId!!,
            request = ParentInviteCreateRequest(studentId = studentId!!.toString())
        )
        assertNotNull(response.code)

        val (tokens, user) = parentService.redeemInvite(
            ParentInviteRedeemRequest(
                code = response.code,
                email = "parent.${studentId}@example.com",
                password = "password123",
                firstName = "Parent",
                lastName = "One"
            ),
            deviceInfo = null
        )
        parentUserId = user.id
        assertNotNull(tokens.accessToken)
        assertEquals(UserRole.PARENT, user.role)

        val links = parentService.listLinks(studentId!!, schoolId!!)
        assertEquals(1, links.size)
        assertEquals(ParentLinkStatus.ACTIVE, links.first().status)
    }
}
