package de.aarondietz.lehrerlog.services

import de.aarondietz.lehrerlog.auth.*
import de.aarondietz.lehrerlog.data.*
import de.aarondietz.lehrerlog.db.tables.*
import de.aarondietz.lehrerlog.db.tables.SchoolClasses.archivedAt
import de.aarondietz.lehrerlog.db.tables.UserRole
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

class ParentService(
    private val passwordService: PasswordService = PasswordService(),
    private val tokenService: TokenService = TokenService()
) {

    fun createInvite(
        teacherId: UUID,
        schoolId: UUID,
        request: ParentInviteCreateRequest
    ): ParentInviteCreateResponse = transaction {
        val studentId = UUID.fromString(request.studentId)
        val studentRow = Students.selectAll()
            .where {
                (Students.id eq studentId) and
                        (Students.schoolId eq schoolId) and
                        Students.deletedAt.isNull()
            }
            .singleOrNull() ?: throw IllegalArgumentException("Student not found")

        ParentInvites.update({
            (ParentInvites.studentId eq studentId) and
                    (ParentInvites.status eq ParentInviteStatus.ACTIVE.name)
        }) {
            it[ParentInvites.status] = ParentInviteStatus.REVOKED.name
        }

        val code = generateInviteCode()
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val expiresAt = request.expiresAt?.let { OffsetDateTime.parse(it) } ?: now.plusDays(7)

        val inviteId = ParentInvites.insertAndGetId {
            it[ParentInvites.studentId] = studentRow[Students.id]
            it[ParentInvites.codeHash] = hashInviteCode(code)
            it[ParentInvites.expiresAt] = expiresAt
            it[ParentInvites.createdBy] = teacherId
            it[ParentInvites.usedBy] = null
            it[ParentInvites.usedAt] = null
            it[ParentInvites.status] = ParentInviteStatus.ACTIVE.name
            it[ParentInvites.createdAt] = now
        }.value

        ParentInviteCreateResponse(
            invite = ParentInviteDto(
                id = inviteId.toString(),
                studentId = studentId.toString(),
                status = ParentInviteStatus.ACTIVE,
                expiresAt = expiresAt.toString(),
                createdAt = now.toString()
            ),
            code = code
        )
    }

    fun redeemInvite(request: ParentInviteRedeemRequest, deviceInfo: String?): Pair<AuthTokens, UserInfo> =
        transaction {
            if (request.code.isBlank()) {
                throw AuthException("Invite code is required")
            }
            if (request.password.length < 8) {
                throw AuthException("Password must be at least 8 characters")
            }

            val hash = hashInviteCode(request.code)
            val now = OffsetDateTime.now(ZoneOffset.UTC)
            val inviteRow = ParentInvites.selectAll()
                .where {
                    (ParentInvites.codeHash eq hash) and
                            (ParentInvites.status eq ParentInviteStatus.ACTIVE.name)
                }
                .firstOrNull() ?: throw AuthException("Invite code is invalid or expired")

            if (inviteRow[ParentInvites.expiresAt].isBefore(now)) {
                ParentInvites.update({ ParentInvites.id eq inviteRow[ParentInvites.id] }) {
                    it[ParentInvites.status] = ParentInviteStatus.EXPIRED.name
                }
                throw AuthException("Invite code is invalid or expired")
            }

            val studentId = inviteRow[ParentInvites.studentId].value
            val userRow = Users.selectAll()
                .where { Users.email eq request.email }
                .firstOrNull()

            val userInfo = if (userRow == null) {
                val parentId = Users.insertAndGetId {
                    it[email] = request.email
                    it[passwordHash] = passwordService.hashPassword(request.password)
                    it[firstName] = request.firstName
                    it[lastName] = request.lastName
                    it[role] = UserRole.PARENT
                    it[schoolId] = null
                    it[isActive] = true
                }.value
                UserInfo(
                    id = parentId,
                    email = request.email,
                    firstName = request.firstName,
                    lastName = request.lastName,
                    role = UserRole.PARENT,
                    schoolId = null
                )
            } else {
                if (userRow[Users.role] != UserRole.PARENT) {
                    throw AuthException("Account is not a parent account")
                }
                val storedHash = userRow[Users.passwordHash]
                if (!passwordService.verifyPassword(request.password, storedHash)) {
                    throw AuthException("Invalid credentials")
                }
                UserInfo(
                    id = userRow[Users.id].value,
                    email = userRow[Users.email],
                    firstName = userRow[Users.firstName],
                    lastName = userRow[Users.lastName],
                    role = userRow[Users.role],
                    schoolId = userRow[Users.schoolId]?.value
                )
            }

            val linkExists = ParentStudentLinks.selectAll()
                .where {
                    (ParentStudentLinks.parentUserId eq userInfo.id) and
                            (ParentStudentLinks.studentId eq studentId) and
                            (ParentStudentLinks.status eq ParentLinkStatus.ACTIVE.name)
                }
                .any()
            if (!linkExists) {
                ParentStudentLinks.insert {
                    it[ParentStudentLinks.parentUserId] = userInfo.id
                    it[ParentStudentLinks.studentId] = studentId
                    it[ParentStudentLinks.status] = ParentLinkStatus.ACTIVE.name
                    it[createdBy] = inviteRow[ParentInvites.createdBy].value
                    it[createdAt] = now
                    it[revokedAt] = null
                }
            }

            ParentInvites.update({ ParentInvites.id eq inviteRow[ParentInvites.id] }) {
                it[ParentInvites.status] = ParentInviteStatus.USED.name
                it[ParentInvites.usedBy] = userInfo.id
                it[ParentInvites.usedAt] = now
            }

            val tokens = generateAndStoreTokens(userInfo, deviceInfo)
            tokens to userInfo
        }

    fun listLinks(studentId: UUID, schoolId: UUID): List<ParentLinkDto> = transaction {
        val studentRow = Students.selectAll()
            .where {
                (Students.id eq studentId) and
                        (Students.schoolId eq schoolId) and
                        Students.deletedAt.isNull()
            }
            .singleOrNull() ?: throw IllegalArgumentException("Student not found")

        ParentStudentLinks.selectAll()
            .where { ParentStudentLinks.studentId eq studentRow[Students.id] }
            .map { row ->
                ParentLinkDto(
                    id = row[ParentStudentLinks.id].value.toString(),
                    parentUserId = row[ParentStudentLinks.parentUserId].value.toString(),
                    studentId = row[ParentStudentLinks.studentId].value.toString(),
                    status = ParentLinkStatus.valueOf(row[ParentStudentLinks.status]),
                    createdAt = row[ParentStudentLinks.createdAt].toString(),
                    revokedAt = row[ParentStudentLinks.revokedAt]?.toString()
                )
            }
    }

    fun revokeLink(linkId: UUID, schoolId: UUID, revokedBy: UUID): Boolean = transaction {
        val linkRow = ParentStudentLinks.selectAll()
            .where { ParentStudentLinks.id eq linkId }
            .singleOrNull() ?: return@transaction false

        val studentId = linkRow[ParentStudentLinks.studentId].value
        val studentRow = Students.selectAll()
            .where {
                (Students.id eq studentId) and
                        (Students.schoolId eq schoolId) and
                        Students.deletedAt.isNull()
            }
            .singleOrNull() ?: return@transaction false

        ParentStudentLinks.update({ ParentStudentLinks.id eq linkId }) {
            it[status] = ParentLinkStatus.REVOKED.name
            it[revokedAt] = OffsetDateTime.now(ZoneOffset.UTC)
        }

        studentRow[Students.id].value == studentId
    }

    fun listParentStudents(parentUserId: UUID): List<StudentDto> = transaction {
        val studentIds = ParentStudentLinks.selectAll()
            .where {
                (ParentStudentLinks.parentUserId eq parentUserId) and
                        (ParentStudentLinks.status eq ParentLinkStatus.ACTIVE.name)
            }
            .map { it[ParentStudentLinks.studentId].value }

        if (studentIds.isEmpty()) {
            return@transaction emptyList()
        }

        val studentRows = Students.selectAll()
            .where { (Students.id inList studentIds) and Students.deletedAt.isNull() }
            .toList()

        val classIdsByStudent = loadClassIdsByStudent(studentIds)
        studentRows.map { row ->
            val classIds = classIdsByStudent[row[Students.id].value].orEmpty()
            StudentDto(
                id = row[Students.id].value.toString(),
                schoolId = row[Students.schoolId].value.toString(),
                firstName = row[Students.firstName],
                lastName = row[Students.lastName],
                classIds = classIds,
                version = row[Students.version],
                createdAt = row[Students.createdAt].toString(),
                updatedAt = row[Students.updatedAt].toString()
            )
        }
    }

    fun listParentAssignments(parentUserId: UUID, studentId: UUID): List<TaskDto> {
        val schoolId = resolveParentStudentSchool(parentUserId, studentId)
            ?: throw IllegalArgumentException("Student link not found")
        return TaskService().getTasksByStudent(schoolId, studentId)
    }

    fun listParentSubmissions(parentUserId: UUID, studentId: UUID): List<TaskSubmissionDto> {
        val schoolId = resolveParentStudentSchool(parentUserId, studentId)
            ?: throw IllegalArgumentException("Student link not found")
        return TaskSubmissionService().listSubmissionsByStudent(studentId, schoolId)
    }

    private fun resolveParentStudentSchool(parentUserId: UUID, studentId: UUID): UUID? = transaction {
        ParentStudentLinks
            .innerJoin(Students)
            .select(Students.schoolId)
            .where {
                (ParentStudentLinks.parentUserId eq parentUserId) and
                        (ParentStudentLinks.studentId eq studentId) and
                        (ParentStudentLinks.status eq ParentLinkStatus.ACTIVE.name) and
                        Students.deletedAt.isNull()
            }
            .firstOrNull()
            ?.get(Students.schoolId)
            ?.value
    }

    private fun loadClassIdsByStudent(studentIds: List<UUID>): Map<UUID, List<String>> {
        if (studentIds.isEmpty()) return emptyMap()
        val result = mutableMapOf<UUID, MutableList<String>>()
        StudentClasses
            .innerJoin(SchoolClasses)
            .select(StudentClasses.studentId, StudentClasses.schoolClassId)
            .where {
                (StudentClasses.studentId inList studentIds) and
                        StudentClasses.validTill.isNull() and
                        archivedAt.isNull()
            }
            .forEach { row ->
                val studentId = row[StudentClasses.studentId].value
                val classId = row[StudentClasses.schoolClassId].value.toString()
                result.getOrPut(studentId) { mutableListOf() }.add(classId)
            }

        return result.mapValues { it.value.distinct() }
    }

    private fun generateAndStoreTokens(user: UserInfo, deviceInfo: String?): AuthTokens {
        val accessToken = tokenService.generateAccessToken(
            userId = user.id,
            email = user.email,
            role = user.role,
            schoolId = user.schoolId
        )

        val refreshToken = tokenService.generateRefreshToken()
        val refreshTokenHash = tokenService.hashRefreshToken(refreshToken)

        val expiresAt = OffsetDateTime.now(ZoneOffset.UTC)
            .plusSeconds(JwtConfig.REFRESH_TOKEN_VALIDITY_MS / 1000)

        RefreshTokens.insert {
            it[RefreshTokens.userId] = user.id
            it[RefreshTokens.tokenHash] = refreshTokenHash
            it[RefreshTokens.deviceInfo] = deviceInfo
            it[RefreshTokens.expiresAt] = expiresAt
        }

        return AuthTokens(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresIn = JwtConfig.ACCESS_TOKEN_VALIDITY_MS / 1000
        )
    }

    private fun generateInviteCode(): String {
        val bytes = ByteArray(10)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02X".format(it) }
    }

    private fun hashInviteCode(code: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(code.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
