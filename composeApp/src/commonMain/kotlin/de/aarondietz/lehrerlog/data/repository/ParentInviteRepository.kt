package de.aarondietz.lehrerlog.data.repository

import de.aarondietz.lehrerlog.auth.TokenStorage
import de.aarondietz.lehrerlog.data.ParentInviteCreateRequest
import de.aarondietz.lehrerlog.data.ParentInviteCreateResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.header

class ParentInviteRepository(
    private val httpClient: HttpClient,
    private val tokenStorage: TokenStorage,
    private val baseUrl: String
) {
    suspend fun createInvite(studentId: String): Result<ParentInviteCreateResponse> {
        return try {
            val response = httpClient.post("$baseUrl/api/parent-invites") {
                setBody(ParentInviteCreateRequest(studentId = studentId))
                tokenStorage.getAccessToken()?.let { header("Authorization", "Bearer $it") }
            }.body<ParentInviteCreateResponse>()
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
