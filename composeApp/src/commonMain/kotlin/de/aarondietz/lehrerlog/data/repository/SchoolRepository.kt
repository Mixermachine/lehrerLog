package de.aarondietz.lehrerlog.data.repository

import de.aarondietz.lehrerlog.SERVER_URL
import de.aarondietz.lehrerlog.data.SchoolSearchResultDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

class SchoolRepository(
    private val httpClient: HttpClient,
    private val baseUrl: String = SERVER_URL
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
