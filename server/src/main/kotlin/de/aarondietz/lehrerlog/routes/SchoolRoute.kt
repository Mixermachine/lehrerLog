package de.aarondietz.lehrerlog.routes

import de.aarondietz.lehrerlog.data.SchoolSearchResultDto
import de.aarondietz.lehrerlog.schools.SchoolCatalogService
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

fun Route.schoolRoute(schoolCatalogService: SchoolCatalogService) {
    route("/schools") {
        get("/search") {
            val query = call.request.queryParameters["query"]?.trim().orEmpty()
            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 50) ?: 20

            if (query.isBlank()) {
                call.respond(HttpStatusCode.OK, emptyList<SchoolSearchResultDto>())
                return@get
            }

            val results = schoolCatalogService.search(query, limit)
            call.respond(results)
        }
    }
}
