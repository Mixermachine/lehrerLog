package de.aarondietz.lehrerlog

import de.aarondietz.lehrerlog.auth.*
import de.aarondietz.lehrerlog.db.DatabaseFactory
import de.aarondietz.lehrerlog.db.tables.Schools
import de.aarondietz.lehrerlog.db.tables.UserRole
import de.aarondietz.lehrerlog.db.tables.Users
import de.aarondietz.lehrerlog.routes.authRoute
import de.aarondietz.lehrerlog.routes.schoolClassRoute
import de.aarondietz.lehrerlog.routes.schoolRoute
import de.aarondietz.lehrerlog.routes.studentRoute
import de.aarondietz.lehrerlog.routes.syncRoute
import de.aarondietz.lehrerlog.routes.taskRoute
import de.aarondietz.lehrerlog.routes.userRoute
import de.aarondietz.lehrerlog.schools.SchoolCatalogService
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.Base64
import java.util.*
import java.nio.file.Path

fun main() {
    embeddedServer(Netty, port = SERVER_PORT, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    // Initialize database
    DatabaseFactory.init()

    // Services
    val passwordService = PasswordService()
    val tokenService = TokenService()
    val catalogPath = System.getenv("SCHOOL_CATALOG_PATH")
        ?: System.getProperty("SCHOOL_CATALOG_PATH")
        ?: "data/schools.json"
    val catalogEndpoint = System.getenv("SCHOOL_CATALOG_ENDPOINT")
        ?: System.getProperty("SCHOOL_CATALOG_ENDPOINT")
        ?: "https://overpass-api.de/api/interpreter"
    val schoolCatalogService = SchoolCatalogService(Path.of(catalogPath), catalogEndpoint)
    val authService = AuthService(passwordService, tokenService, schoolCatalogService)

    try {
        schoolCatalogService.initialize()
    } catch (e: Exception) {
        environment.log.error("Failed to initialize school catalog; server will not start.", e)
        throw e
    }

    seedTestUserIfConfigured(passwordService)

    // Install plugins
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }

    install(Authentication) {
        jwt("jwt") {
            realm = JwtConfig.REALM
            verifier(tokenService.getJwtVerifier())
            validate { credential ->
                val userId = credential.payload.subject
                val email = credential.payload.getClaim("email").asString()
                val role = credential.payload.getClaim("role").asString()
                val schoolId = credential.payload.getClaim("schoolId").asString()

                if (userId != null && email != null && role != null) {
                    UserPrincipal(
                        id = UUID.fromString(userId),
                        email = email,
                        role = UserRole.valueOf(role),
                        schoolId = schoolId?.let { UUID.fromString(it) }
                    )
                } else {
                    null
                }
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Token is invalid or expired"))
            }
        }
    }

    routing {
        get("/health") {
            val dbHealthy = try {
                transaction {
                    exec("SELECT 1") { it.next() }
                }
                true
            } catch (e: Exception) {
                environment.log.error("Database health check failed", e)
                false
            }

            if (dbHealthy) {
                call.respond(HealthResponse(status = "ok", database = "connected"))
            } else {
                call.respond(
                    HttpStatusCode.ServiceUnavailable,
                    HealthResponse(status = "unhealthy", database = "disconnected")
                )
            }
        }
        authRoute(authService)
        schoolClassRoute()
        schoolRoute(schoolCatalogService)
        studentRoute()
        taskRoute()
        syncRoute()
        userRoute()
    }
}

@Serializable
data class HealthResponse(
    val status: String,
    val database: String
)

private fun Application.seedTestUserIfConfigured(passwordService: PasswordService) {
    val email = System.getenv("SEED_TEST_USER_EMAIL")?.trim().orEmpty()
    val password = resolveSeedPassword()
    val passwordValue = password.orEmpty()
    if (email.isBlank() && passwordValue.isBlank()) {
        return
    }

    val hasEncoded = !System.getenv("SEED_TEST_USER_PASSWORD_B64").isNullOrBlank()
    if (password == null && hasEncoded) {
        environment.log.warn("Seed test user skipped: SEED_TEST_USER_PASSWORD_B64 is invalid base64.")
        return
    }

    if (email.isBlank() || passwordValue.isBlank()) {
        environment.log.warn("Seed test user skipped: SEED_TEST_USER_EMAIL and password must both be set.")
        return
    }

    val firstName = System.getenv("SEED_TEST_USER_FIRST_NAME")?.trim().orEmpty().ifBlank { "Staging" }
    val lastName = System.getenv("SEED_TEST_USER_LAST_NAME")?.trim().orEmpty().ifBlank { "Verifier" }
    val schoolCode = System.getenv("SEED_TEST_SCHOOL_CODE")?.trim().orEmpty().ifBlank { "STAGING-TEST" }
    val schoolName = System.getenv("SEED_TEST_SCHOOL_NAME")?.trim().orEmpty().ifBlank { "Staging Test School" }

    try {
        transaction {
            val existingUser = Users.selectAll()
                .where { Users.email eq email }
                .firstOrNull()
            if (existingUser != null) {
                return@transaction
            }

            var schoolId = Schools.selectAll()
                .where { Schools.code eq schoolCode }
                .firstOrNull()
                ?.get(Schools.id)
                ?.value

            if (schoolId == null) {
                Schools.insertIgnore {
                    it[Schools.code] = schoolCode
                    it[Schools.name] = schoolName
                }
                schoolId = Schools.selectAll()
                    .where { Schools.code eq schoolCode }
                    .firstOrNull()
                    ?.get(Schools.id)
                    ?.value
            }

            if (schoolId == null) {
                environment.log.warn("Seed test user skipped: school '$schoolCode' could not be created.")
                return@transaction
            }

            Users.insertIgnore {
                it[Users.email] = email
                it[Users.passwordHash] = passwordService.hashPassword(passwordValue)
                it[Users.firstName] = firstName
                it[Users.lastName] = lastName
                it[Users.role] = UserRole.TEACHER
                it[Users.schoolId] = schoolId
            }
        }
        environment.log.info("Seed test user ensured for $email (school=$schoolCode).")
    } catch (e: Exception) {
        environment.log.error("Seed test user failed for $email.", e)
    }
}

private fun resolveSeedPassword(): String? {
    val encoded = System.getenv("SEED_TEST_USER_PASSWORD_B64")?.trim().orEmpty()
    if (encoded.isNotBlank()) {
        return try {
            String(Base64.getDecoder().decode(encoded)).trim()
        } catch (e: IllegalArgumentException) {
            null
        }
    }
    return System.getenv("SEED_TEST_USER_PASSWORD")?.trim()
}
