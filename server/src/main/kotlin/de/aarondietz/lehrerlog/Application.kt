package de.aarondietz.lehrerlog

import de.aarondietz.lehrerlog.auth.*
import de.aarondietz.lehrerlog.db.DatabaseFactory
import de.aarondietz.lehrerlog.db.tables.UserRole
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
import org.jetbrains.exposed.sql.transactions.transaction
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
    val catalogPath = System.getenv("SCHOOL_CATALOG_PATH") ?: "data/schools.json"
    val catalogEndpoint = System.getenv("SCHOOL_CATALOG_ENDPOINT")
        ?: "https://overpass-api.de/api/interpreter"
    val schoolCatalogService = SchoolCatalogService(Path.of(catalogPath), catalogEndpoint)
    val authService = AuthService(passwordService, tokenService, schoolCatalogService)

    try {
        schoolCatalogService.initialize()
    } catch (e: Exception) {
        environment.log.error("Failed to initialize school catalog; server will not start.", e)
        throw e
    }

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
