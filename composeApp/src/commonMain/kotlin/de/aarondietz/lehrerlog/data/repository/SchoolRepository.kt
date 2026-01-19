package de.aarondietz.lehrerlog.data.repository

import de.aarondietz.lehrerlog.ServerConfig
import de.aarondietz.lehrerlog.data.SchoolSearchResultDto
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*

class SchoolRepository(
    private val httpClient: HttpClient,
    private val baseUrl: String = ServerConfig.SERVER_URL
) {
    suspend fun searchSchools(query: String, limit: Int = 20): Result<List<SchoolSearchResultDto>> {
        return try {
            val results = httpClient.get("$baseUrl/schools/search") {
                parameter("query", query)
                parameter("limit", limit)
            }.body<List<SchoolSearchResultDto>>()
            Result.success(results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
