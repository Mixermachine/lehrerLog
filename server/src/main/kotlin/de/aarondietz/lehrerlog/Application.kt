package de.aarondietz.lehrerlog

import de.aarondietz.lehrerlog.auth.*
import de.aarondietz.lehrerlog.db.DatabaseFactory
import de.aarondietz.lehrerlog.db.tables.UserRole
import de.aarondietz.lehrerlog.routes.authRoute
import de.aarondietz.lehrerlog.routes.schoolClassRoute
import de.aarondietz.lehrerlog.routes.schoolRoute
import de.aarondietz.lehrerlog.routes.studentRoute
import de.aarondietz.lehrerlog.routes.syncRoute
import de.aarondietz.lehrerlog.routes.userRoute
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
import kotlinx.serialization.json.Json
import java.util.*

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
    val authService = AuthService(passwordService, tokenService)

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
        authRoute(authService)
        schoolClassRoute()
        schoolRoute()
        studentRoute()
        syncRoute()
        userRoute()
    }
}