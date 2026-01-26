package de.aarondietz.lehrerlog

import de.aarondietz.lehrerlog.auth.TokenService
import de.aarondietz.lehrerlog.db.DatabaseFactory
import de.aarondietz.lehrerlog.db.tables.Schools
import de.aarondietz.lehrerlog.db.tables.StorageOwnerType
import de.aarondietz.lehrerlog.db.tables.StoragePlans
import de.aarondietz.lehrerlog.db.tables.StorageSubscriptions
import de.aarondietz.lehrerlog.db.tables.StorageUsage
import de.aarondietz.lehrerlog.db.tables.UserRole
import de.aarondietz.lehrerlog.db.tables.Users
import de.aarondietz.lehrerlog.data.StorageQuotaDto
import de.aarondietz.lehrerlog.data.StorageUsageDto
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class StorageRouteEndToEndTest {

    private val tokenService = TokenService()
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
            val schoolIdValue = schoolId!!
            userId = Users.insertAndGetId {
                it[email] = "$suffix.user@example.com"
                it[passwordHash] = "test"
                it[firstName] = "Test"
                it[lastName] = "User"
                it[role] = UserRole.TEACHER
                it[Users.schoolId] = schoolIdValue
                it[isActive] = true
            }.value

            val defaultPlanId = UUID.fromString("00000000-0000-0000-0000-000000000001")
            StoragePlans.insertIgnore {
                it[id] = defaultPlanId
                it[name] = "Default"
                it[maxTotalBytes] = 100L * 1024L * 1024L
                it[maxFileBytes] = 5L * 1024L * 1024L
            }
            StorageSubscriptions.insertIgnore {
                it[id] = schoolIdValue
                it[ownerType] = StorageOwnerType.SCHOOL.name
                it[ownerId] = schoolIdValue
                it[planId] = defaultPlanId
                it[active] = true
            }
            StorageUsage.insertIgnore {
                it[ownerType] = StorageOwnerType.SCHOOL.name
                it[ownerId] = schoolIdValue
                it[usedTotalBytes] = 1024L
            }
        }
    }

    @AfterTest
    fun teardown() {
        transaction {
            schoolId?.let { id ->
                StorageUsage.deleteWhere { (StorageUsage.ownerType eq StorageOwnerType.SCHOOL.name) and (StorageUsage.ownerId eq id) }
                StorageSubscriptions.deleteWhere { StorageSubscriptions.id eq id }
                Schools.deleteWhere { Schools.id eq id }
            }
            userId?.let { id -> Users.deleteWhere { Users.id eq id } }
        }
    }

    @Test
    fun `quota and usage endpoints return data`() = testApplication {
        application { module() }

        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        val token = tokenService.generateAccessToken(
            userId = userId!!,
            email = "test@example.com",
            role = UserRole.TEACHER,
            schoolId = schoolId
        )

        val quotaResponse = client.get("/api/storage/quota") {
            header("Authorization", "Bearer $token")
        }
        assertEquals(200, quotaResponse.status.value)
        val quota = quotaResponse.body<StorageQuotaDto>()
        assertEquals(100L * 1024L * 1024L, quota.maxTotalBytes)

        val usageResponse = client.get("/api/storage/usage") {
            header("Authorization", "Bearer $token")
        }
        assertEquals(200, usageResponse.status.value)
        val usage = usageResponse.body<StorageUsageDto>()
        assertEquals(1024L, usage.usedTotalBytes)
    }
}
