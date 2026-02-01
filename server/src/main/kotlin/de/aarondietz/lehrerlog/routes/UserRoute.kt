package de.aarondietz.lehrerlog.routes

import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.userRoute() {
    route("/users") {
        get {
            call.respondText("Get all users")
        }

        get("/{id}") {
            val id = call.parameters["id"]
            call.respondText("Get user $id")
        }

        post {
            call.respondText("Create user")
        }
    }
}