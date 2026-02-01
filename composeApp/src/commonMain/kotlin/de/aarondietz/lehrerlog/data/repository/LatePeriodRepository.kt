package de.aarondietz.lehrerlog.data.repository

import de.aarondietz.lehrerlog.auth.TokenStorage
import de.aarondietz.lehrerlog.data.CreateLatePeriodRequest
import de.aarondietz.lehrerlog.data.LatePeriodDto
import de.aarondietz.lehrerlog.data.UpdateLatePeriodRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody

class LatePeriodRepository(
    private val httpClient: HttpClient,
    private val tokenStorage: TokenStorage,
    private val baseUrl: String
) {
    suspend fun listPeriods(): Result<List<LatePeriodDto>> {
        return try {
            val periods = httpClient.get("$baseUrl/api/late-periods") {
                tokenStorage.getAccessToken()?.let { header("Authorization", "Bearer $it") }
            }.body<List<LatePeriodDto>>()
            Result.success(periods)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createPeriod(name: String, startsAt: String, endsAt: String?): Result<LatePeriodDto> {
        return try {
            val period = httpClient.post("$baseUrl/api/late-periods") {
                setBody(CreateLatePeriodRequest(name, startsAt, endsAt))
                tokenStorage.getAccessToken()?.let { header("Authorization", "Bearer $it") }
            }.body<LatePeriodDto>()
            Result.success(period)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updatePeriod(
        periodId: String,
        name: String?,
        startsAt: String?,
        endsAt: String?
    ): Result<LatePeriodDto> {
        return try {
            val period = httpClient.put("$baseUrl/api/late-periods/$periodId") {
                setBody(UpdateLatePeriodRequest(name, startsAt, endsAt))
                tokenStorage.getAccessToken()?.let { header("Authorization", "Bearer $it") }
            }.body<LatePeriodDto>()
            Result.success(period)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun activatePeriod(periodId: String): Result<LatePeriodDto> {
        return try {
            val period = httpClient.post("$baseUrl/api/late-periods/$periodId/activate") {
                tokenStorage.getAccessToken()?.let { header("Authorization", "Bearer $it") }
            }.body<LatePeriodDto>()
            Result.success(period)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun recalculatePeriod(periodId: String): Result<Unit> {
        return try {
            httpClient.post("$baseUrl/api/late-periods/$periodId/recalculate") {
                tokenStorage.getAccessToken()?.let { header("Authorization", "Bearer $it") }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
