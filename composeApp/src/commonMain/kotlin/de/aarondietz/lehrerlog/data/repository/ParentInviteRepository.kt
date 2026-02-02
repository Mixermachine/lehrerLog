package de.aarondietz.lehrerlog.data.repository

import de.aarondietz.lehrerlog.auth.TokenStorage
import de.aarondietz.lehrerlog.data.ParentInviteCreateRequest
import de.aarondietz.lehrerlog.data.ParentInviteCreateResponse
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

class ParentInviteRepository(
    private val httpClient: HttpClient,
    private val tokenStorage: TokenStorage,
    private val baseUrl: String
) {
    suspend fun createInvite(studentId: String): Result<ParentInviteCreateResponse> {
        return try {
            val response = httpClient.post("$baseUrl/api/parent-invites") {
                contentType(ContentType.Application.Json)
                setBody(ParentInviteCreateRequest(studentId = studentId))
                tokenStorage.getAccessToken()?.let { header("Authorization", "Bearer $it") }
            }.body<ParentInviteCreateResponse>()
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
