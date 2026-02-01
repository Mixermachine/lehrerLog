package de.aarondietz.lehrerlog.data.repository

import de.aarondietz.lehrerlog.auth.TokenStorage
import de.aarondietz.lehrerlog.data.LatePeriodDto
import de.aarondietz.lehrerlog.data.LatePeriodSummaryDto
import de.aarondietz.lehrerlog.data.LateStudentStatsDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header

class LateStatsRepository(
    private val httpClient: HttpClient,
    private val tokenStorage: TokenStorage,
    private val baseUrl: String
) {
    suspend fun getPeriods(): Result<List<LatePeriodDto>> {
        return try {
            val periods = httpClient.get("$baseUrl/api/late-periods") {
                tokenStorage.getAccessToken()?.let { header("Authorization", "Bearer $it") }
            }.body<List<LatePeriodDto>>()
            Result.success(periods)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPeriodSummaries(): Result<List<LatePeriodSummaryDto>> {
        return try {
            val summaries = httpClient.get("$baseUrl/api/late-stats/periods") {
                tokenStorage.getAccessToken()?.let { header("Authorization", "Bearer $it") }
            }.body<List<LatePeriodSummaryDto>>()
            Result.success(summaries)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getStatsForPeriod(periodId: String): Result<List<LateStudentStatsDto>> {
        return try {
            val stats = httpClient.get("$baseUrl/api/late-stats/students") {
                tokenStorage.getAccessToken()?.let { header("Authorization", "Bearer $it") }
                url { parameters.append("periodId", periodId) }
            }.body<List<LateStudentStatsDto>>()
            Result.success(stats)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
