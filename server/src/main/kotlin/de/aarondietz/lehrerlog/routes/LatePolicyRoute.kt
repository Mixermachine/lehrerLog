package de.aarondietz.lehrerlog.routes

import de.aarondietz.lehrerlog.auth.ErrorResponse
import de.aarondietz.lehrerlog.data.CreateLatePeriodRequest
import de.aarondietz.lehrerlog.data.ResolvePunishmentRequest
import de.aarondietz.lehrerlog.data.UpdateLatePeriodRequest
import de.aarondietz.lehrerlog.services.LatePolicyService
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.*

fun Route.latePolicyRoute(
    latePolicyService: LatePolicyService = LatePolicyService()
) {
    authenticate("jwt") {
        route("/api/late-periods") {
            get {
                val principal = call.getPrincipalOrRespond()
                val periods = latePolicyService.listPeriods(principal.id)
                call.respond(periods)
            }

            post {
                val principal = call.getPrincipalOrRespond()
                val request = call.receive<CreateLatePeriodRequest>()
                if (request.name.isBlank() || request.startsAt.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Name and startsAt are required"))
                    return@post
                }
                val period = latePolicyService.createPeriod(principal.id, request)
                call.respond(HttpStatusCode.Created, period)
            }

            put("/{id}") {
                val principal = call.getPrincipalOrRespond()
                val periodId = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?: run {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid period ID"))
                        return@put
                    }
                val request = call.receive<UpdateLatePeriodRequest>()
                val updated = latePolicyService.updatePeriod(principal.id, periodId, request)
                if (updated == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("Period not found"))
                } else {
                    call.respond(updated)
                }
            }

            post("/{id}/activate") {
                val principal = call.getPrincipalOrRespond()
                val periodId = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?: run {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid period ID"))
                        return@post
                    }
                val period = latePolicyService.activatePeriod(principal.id, periodId)
                call.respond(period)
            }

            post("/{id}/recalculate") {
                val principal = call.getPrincipalOrRespond()
                val periodId = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?: run {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid period ID"))
                        return@post
                    }
                latePolicyService.recalculatePeriod(principal.id, periodId)
                call.respond(HttpStatusCode.NoContent)
            }
        }

        route("/api/late-stats") {
            get("/periods") {
                val principal = call.getPrincipalOrRespond()
                val summaries = latePolicyService.getPeriodSummaries(principal.id)
                call.respond(summaries)
            }

            get("/students") {
                val principal = call.getPrincipalOrRespond()
                val periodId =
                    call.request.queryParameters["periodId"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                        ?: run {
                            call.respond(HttpStatusCode.BadRequest, ErrorResponse("periodId is required"))
                            return@get
                        }
                val stats = latePolicyService.getStatsForPeriod(principal.id, periodId)
                call.respond(stats)
            }
        }

        get("/api/students/{id}/late-stats") {
            val principal = call.getPrincipalOrRespond()
            val studentId = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: run {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid student ID"))
                    return@get
                }
            val periodId =
                call.request.queryParameters["periodId"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?: run {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("periodId is required"))
                        return@get
                    }
            val stats = latePolicyService.getStatsForStudent(principal.id, studentId, periodId)
            if (stats == null) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("Stats not found"))
            } else {
                call.respond(stats)
            }
        }

        route("/api/punishments") {
            get {
                val principal = call.getPrincipalOrRespond()
                val studentId =
                    call.request.queryParameters["studentId"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                        ?: run {
                            call.respond(HttpStatusCode.BadRequest, ErrorResponse("studentId is required"))
                            return@get
                        }
                val periodId =
                    call.request.queryParameters["periodId"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                        ?: run {
                            call.respond(HttpStatusCode.BadRequest, ErrorResponse("periodId is required"))
                            return@get
                        }
                val records = latePolicyService.getPunishments(principal.id, studentId, periodId)
                call.respond(records)
            }

            post("/resolve") {
                val principal = call.getPrincipalOrRespond()
                val request = call.receive<ResolvePunishmentRequest>()
                val record = latePolicyService.resolvePunishment(principal.id, principal.id, request)
                if (record == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("Punishment not found"))
                } else {
                    call.respond(record)
                }
            }
        }
    }
}
