package de.aarondietz.lehrerlog.data.repository

import de.aarondietz.lehrerlog.auth.TokenStorage
import de.aarondietz.lehrerlog.data.ParentLinkDto
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*

class ParentLinksRepository(
    private val httpClient: HttpClient,
    private val tokenStorage: TokenStorage,
    private val baseUrl: String
) {
    suspend fun listLinks(studentId: String): Result<List<ParentLinkDto>> {
        return try {
            val response = httpClient.get("$baseUrl/api/parent-links") {
                parameter("studentId", studentId)
                tokenStorage.getAccessToken()?.let { header("Authorization", "Bearer $it") }
            }.body<List<ParentLinkDto>>()
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun revokeLink(linkId: String): Result<Unit> {
        return try {
            httpClient.post("$baseUrl/api/parent-links/$linkId/revoke") {
                tokenStorage.getAccessToken()?.let { header("Authorization", "Bearer $it") }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
