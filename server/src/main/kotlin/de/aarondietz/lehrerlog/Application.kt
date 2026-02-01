package de.aarondietz.lehrerlog

import de.aarondietz.lehrerlog.auth.*
import de.aarondietz.lehrerlog.db.DatabaseFactory
import de.aarondietz.lehrerlog.db.tables.*
import de.aarondietz.lehrerlog.db.tables.UserRole
import de.aarondietz.lehrerlog.routes.*
import de.aarondietz.lehrerlog.schools.SchoolCatalogService
import de.aarondietz.lehrerlog.util.EnvironmentValidator
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import java.time.Duration
import java.util.*

fun main() {
    embeddedServer(Netty, port = SERVER_PORT, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    // Validate required environment variables
    EnvironmentValidator.validateRequiredEnvVars()

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

    install(CORS) {
        allowHost("app.lehrerlog.de", schemes = listOf("https"))
        allowHost("app.qa.lehrerlog.de", schemes = listOf("https"))

        allowHost("localhost:8080", schemes = listOf("http"))
        allowHost("localhost:8081", schemes = listOf("http"))
        allowHost("127.0.0.1:8080", schemes = listOf("http"))
        allowHost("127.0.0.1:8081", schemes = listOf("http"))

        allowCredentials = true
        allowNonSimpleContentTypes = true

        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.Accept)

        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Options)

        maxAgeInSeconds = 3600
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
            val objectStorageUrl = System.getenv("OBJECT_STORAGE_HEALTH_URL")?.trim().orEmpty()
            val objectStorageToken = System.getenv("OBJECT_STORAGE_HEALTH_TOKEN")?.trim().orEmpty()
            val dbHealthy = try {
                transaction {
                    exec("SELECT 1") { it.next() }
                }
                true
            } catch (e: Exception) {
                environment.log.error("Database health check failed", e)
                false
            }

            val objectStorageStatus = if (objectStorageUrl.isBlank()) {
                "unknown"
            } else {
                val storageHealthy = checkObjectStorageHealth(environment, objectStorageUrl, objectStorageToken)
                if (storageHealthy) "healthy" else "unhealthy"
            }

            val overallHealthy = dbHealthy && (objectStorageUrl.isBlank() || objectStorageStatus == "healthy")
            if (overallHealthy) {
                call.respond(HealthResponse(status = "ok", database = "connected", objectStorage = objectStorageStatus))
            } else {
                val dbStatus = if (dbHealthy) "connected" else "disconnected"
                call.respond(
                    HttpStatusCode.ServiceUnavailable,
                    HealthResponse(status = "unhealthy", database = dbStatus, objectStorage = objectStorageStatus)
                )
            }
        }
        authRoute(authService)
        schoolClassRoute()
        schoolRoute(schoolCatalogService)
        studentRoute()
        taskRoute()
        fileRoute()
        storageRoute()
        latePolicyRoute()
        parentRoute()
        syncRoute()
        userRoute()
    }
}

@Serializable
data class HealthResponse(
    val status: String,
    val database: String,
    val objectStorage: String
)

private suspend fun checkObjectStorageHealth(
    environment: ApplicationEnvironment,
    url: String,
    token: String
): Boolean {
    val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(2))
        .build()
    val requestBuilder = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .timeout(Duration.ofSeconds(3))
        .GET()
    if (token.isNotBlank()) {
        requestBuilder.header(HttpHeaders.Authorization, "Bearer $token")
    } else {
        environment.log.warn("Object storage health token is not set; request may be rejected.")
    }
    val request = requestBuilder.build()
    return try {
        val response = withContext(Dispatchers.IO) {
            client.send(request, HttpResponse.BodyHandlers.ofString())
        }
        val statusCode = response.statusCode()
        val body = response.body()?.trim().orEmpty()
        if (body.isNotBlank()) {
            val preview = if (body.length > 2000) body.take(2000) + "..." else body
            if (statusCode == 200) {
                environment.log.info("Object storage health details: $preview")
            } else {
                environment.log.warn("Object storage health details (status=$statusCode): $preview")
            }
        }
        statusCode == 200
    } catch (e: Exception) {
        environment.log.error("Object storage health check failed", e)
        false
    }
}

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

    val firstName = System.getenv("SEED_TEST_USER_FIRST_NAME")?.trim().orEmpty().ifBlank { "QA" }
    val lastName = System.getenv("SEED_TEST_USER_LAST_NAME")?.trim().orEmpty().ifBlank { "Verifier" }
    val schoolCode = System.getenv("SEED_TEST_SCHOOL_CODE")?.trim().orEmpty().ifBlank { "QA-TEST" }
    val schoolName = System.getenv("SEED_TEST_SCHOOL_NAME")?.trim().orEmpty().ifBlank { "QA Test School" }

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

            ensureSchoolStorageDefaults(schoolId)

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
