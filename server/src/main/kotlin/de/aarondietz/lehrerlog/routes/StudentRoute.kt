package de.aarondietz.lehrerlog.routes

import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.studentRoute() {
    route("/students") {
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