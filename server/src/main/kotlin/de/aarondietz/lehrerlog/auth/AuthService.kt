package de.aarondietz.lehrerlog.auth

import de.aarondietz.lehrerlog.db.tables.RefreshTokens
import de.aarondietz.lehrerlog.db.tables.Schools
import de.aarondietz.lehrerlog.db.tables.StorageOwnerType
import de.aarondietz.lehrerlog.db.tables.StoragePlans
import de.aarondietz.lehrerlog.db.tables.StorageSubscriptions
import de.aarondietz.lehrerlog.db.tables.StorageUsage
import de.aarondietz.lehrerlog.db.tables.UserRole
import de.aarondietz.lehrerlog.db.tables.Users
import de.aarondietz.lehrerlog.schools.SchoolCatalogService
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

data class AuthTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long
)

data class UserInfo(
    val id: UUID,
    val email: String,
    val firstName: String,
    val lastName: String,
    val role: UserRole,
    val schoolId: UUID?
)

data class RegisterRequest(
    val email: String,
    val password: String,
    val firstName: String,
    val lastName: String,
    val schoolCode: String? = null
)

data class LoginRequest(
    val email: String,
    val password: String
)

class AuthException(message: String) : Exception(message)

class AuthService(
    private val passwordService: PasswordService,
    private val tokenService: TokenService,
    private val schoolCatalogService: SchoolCatalogService
) {

    fun register(request: RegisterRequest, deviceInfo: String? = null): Pair<AuthTokens, UserInfo> = transaction {
        // Check if email already exists
        val existingUser = Users.selectAll().where { Users.email eq request.email }.firstOrNull()
        if (existingUser != null) {
            throw AuthException("Email already registered")
        }

        // Find school if code provided
        val schoolId = request.schoolCode?.let { code ->
            resolveSchoolId(code)
        }

        // Create user
        val userId = Users.insertAndGetId {
            it[Users.email] = request.email
            it[Users.passwordHash] = passwordService.hashPassword(request.password)
            it[Users.firstName] = request.firstName
            it[Users.lastName] = request.lastName
            it[Users.role] = UserRole.TEACHER
            it[Users.schoolId] = schoolId
        }.value

        val user = UserInfo(
            id = userId,
            email = request.email,
            firstName = request.firstName,
            lastName = request.lastName,
            role = UserRole.TEACHER,
            schoolId = schoolId
        )

        val tokens = generateAndStoreTokens(user, deviceInfo)
        tokens to user
    }

    fun login(request: LoginRequest, deviceInfo: String? = null): Pair<AuthTokens, UserInfo> = transaction {
        val userRow = Users.selectAll()
            .where { (Users.email eq request.email) and (Users.isActive eq true) }
            .firstOrNull() ?: throw AuthException("Invalid credentials")

        val storedHash = userRow[Users.passwordHash]
        if (!passwordService.verifyPassword(request.password, storedHash)) {
            throw AuthException("Invalid credentials")
        }

        val user = UserInfo(
            id = userRow[Users.id].value,
            email = userRow[Users.email],
            firstName = userRow[Users.firstName],
            lastName = userRow[Users.lastName],
            role = userRow[Users.role],
            schoolId = userRow[Users.schoolId]?.value
        )

        val tokens = generateAndStoreTokens(user, deviceInfo)
        tokens to user
    }

    fun refresh(refreshToken: String, deviceInfo: String? = null): AuthTokens = transaction {
        // Find valid refresh token
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val tokenRow = RefreshTokens.selectAll()
            .where {
                (RefreshTokens.revokedAt.isNull()) and
                (RefreshTokens.expiresAt greater now)
            }
            .toList()
            .find { row ->
                runCatching {
                    tokenService.verifyRefreshToken(refreshToken, row[RefreshTokens.tokenHash])
                }.getOrDefault(false)
            }
            ?: throw AuthException("Invalid or expired refresh token")

        val userId = tokenRow[RefreshTokens.userId].value

        // Get user
        val userRow = Users.selectAll()
            .where { (Users.id eq userId) and (Users.isActive eq true) }
            .firstOrNull() ?: throw AuthException("User not found or inactive")

        // Revoke old token
        RefreshTokens.update({ RefreshTokens.id eq tokenRow[RefreshTokens.id] }) {
            it[RefreshTokens.revokedAt] = now
        }

        val user = UserInfo(
            id = userRow[Users.id].value,
            email = userRow[Users.email],
            firstName = userRow[Users.firstName],
            lastName = userRow[Users.lastName],
            role = userRow[Users.role],
            schoolId = userRow[Users.schoolId]?.value
        )

        generateAndStoreTokens(user, deviceInfo)
    }

    fun logout(refreshToken: String): Boolean = transaction {
        val tokenRow = RefreshTokens.selectAll()
            .where { RefreshTokens.revokedAt.isNull() }
            .toList()
            .find { row -> tokenService.verifyRefreshToken(refreshToken, row[RefreshTokens.tokenHash]) }
            ?: return@transaction false

        RefreshTokens.update({ RefreshTokens.id eq tokenRow[RefreshTokens.id] }) {
            it[RefreshTokens.revokedAt] = OffsetDateTime.now(ZoneOffset.UTC)
        }
        true
    }

    fun logoutAll(userId: UUID): Int = transaction {
        RefreshTokens.update({ (RefreshTokens.userId eq userId) and RefreshTokens.revokedAt.isNull() }) {
            it[RefreshTokens.revokedAt] = OffsetDateTime.now(ZoneOffset.UTC)
        }
    }

    fun joinSchool(userId: UUID, schoolCode: String, deviceInfo: String? = null): Pair<AuthTokens, UserInfo> = transaction {
        val schoolId = resolveSchoolId(schoolCode)

        val userRow = Users.selectAll()
            .where { (Users.id eq userId) and (Users.isActive eq true) }
            .firstOrNull() ?: throw AuthException("User not found or inactive")

        val existingSchoolId = userRow[Users.schoolId]?.value
        if (existingSchoolId != null && existingSchoolId != schoolId) {
            throw AuthException("User already associated with a different school")
        }

        if (existingSchoolId == null) {
            Users.update({ Users.id eq userId }) {
                it[Users.schoolId] = schoolId
            }
        }

        val user = UserInfo(
            id = userRow[Users.id].value,
            email = userRow[Users.email],
            firstName = userRow[Users.firstName],
            lastName = userRow[Users.lastName],
            role = userRow[Users.role],
            schoolId = schoolId
        )

        val tokens = generateAndStoreTokens(user, deviceInfo)
        tokens to user
    }

    fun getUserById(userId: UUID): UserInfo? = transaction {
        Users.selectAll()
            .where { (Users.id eq userId) and (Users.isActive eq true) }
            .firstOrNull()
            ?.let { row ->
                UserInfo(
                    id = row[Users.id].value,
                    email = row[Users.email],
                    firstName = row[Users.firstName],
                    lastName = row[Users.lastName],
                    role = row[Users.role],
                    schoolId = row[Users.schoolId]?.value
                )
            }
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

    private fun resolveSchoolId(schoolCode: String): UUID {
        val existingSchool = Schools.selectAll()
            .where { Schools.code eq schoolCode }
            .firstOrNull()

        if (existingSchool != null) {
            return existingSchool[Schools.id].value
        }

        val catalogEntry = schoolCatalogService.findByCode(schoolCode)
            ?: throw AuthException("Invalid school code")

        val schoolId = Schools.insertAndGetId {
            it[Schools.name] = catalogEntry.name
            it[Schools.code] = catalogEntry.code
        }.value

        ensureSchoolStorageDefaults(schoolId)
        return schoolId
    }

    private fun ensureSchoolStorageDefaults(schoolId: UUID) {
        val defaultPlanId = UUID.fromString("00000000-0000-0000-0000-000000000001")

        StoragePlans.insertIgnore {
            it[id] = defaultPlanId
            it[name] = "Default"
            it[maxTotalBytes] = 100L * 1024L * 1024L
            it[maxFileBytes] = 5L * 1024L * 1024L
        }

        StorageSubscriptions.insertIgnore {
            it[id] = schoolId
            it[ownerType] = StorageOwnerType.SCHOOL.name
            it[ownerId] = schoolId
            it[planId] = defaultPlanId
            it[active] = true
        }

        StorageUsage.insertIgnore {
            it[ownerType] = StorageOwnerType.SCHOOL.name
            it[ownerId] = schoolId
            it[usedTotalBytes] = 0
        }
    }
}
