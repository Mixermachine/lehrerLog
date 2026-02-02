package de.aarondietz.lehrerlog.routes

import de.aarondietz.lehrerlog.data.SchoolSearchResultDto
import de.aarondietz.lehrerlog.schools.SchoolCatalogService
import io.ktor.http.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.schoolRoute(schoolCatalogService: SchoolCatalogService) {
    route("/schools") {
        rateLimit(RateLimitName("public")) {
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
}
