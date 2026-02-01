package de.aarondietz.lehrerlog.data.repository

import de.aarondietz.lehrerlog.auth.TokenStorage
import de.aarondietz.lehrerlog.data.PunishmentRecordDto
import de.aarondietz.lehrerlog.data.ResolvePunishmentRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody

class PunishmentRepository(
    private val httpClient: HttpClient,
    private val tokenStorage: TokenStorage,
    private val baseUrl: String
) {
    suspend fun resolvePunishment(studentId: String, periodId: String, note: String? = null): Result<PunishmentRecordDto> {
        return try {
            val record = httpClient.post("$baseUrl/api/punishments/resolve") {
                tokenStorage.getAccessToken()?.let { header("Authorization", "Bearer $it") }
                setBody(ResolvePunishmentRequest(studentId = studentId, periodId = periodId, note = note))
            }.body<PunishmentRecordDto>()
            Result.success(record)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}