package de.aarondietz.lehrerlog

import de.aarondietz.lehrerlog.routes.schoolClassRoute
import de.aarondietz.lehrerlog.routes.schoolRoute
import de.aarondietz.lehrerlog.routes.studentRoute
import de.aarondietz.lehrerlog.routes.userRoute
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun main() {

    embeddedServer(Netty, port = SERVER_PORT, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}



fun Application.module() {
    routing {
        schoolClassRoute()
        schoolRoute()
        studentRoute()
        userRoute()
    }
}