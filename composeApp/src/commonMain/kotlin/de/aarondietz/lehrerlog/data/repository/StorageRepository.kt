package de.aarondietz.lehrerlog.data.repository

import de.aarondietz.lehrerlog.auth.TokenStorage
import de.aarondietz.lehrerlog.data.StorageQuotaDto
import de.aarondietz.lehrerlog.data.StorageUsageDto
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*

class StorageRepository(
    private val httpClient: HttpClient,
    private val tokenStorage: TokenStorage,
    private val baseUrl: String
) {
    suspend fun getQuota(): Result<StorageQuotaDto> {
        return try {
            val quota = httpClient.get("$baseUrl/api/storage/quota") {
                tokenStorage.getAccessToken()?.let { header("Authorization", "Bearer $it") }
            }.body<StorageQuotaDto>()
            Result.success(quota)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUsage(): Result<StorageUsageDto> {
        return try {
            val usage = httpClient.get("$baseUrl/api/storage/usage") {
                tokenStorage.getAccessToken()?.let { header("Authorization", "Bearer $it") }
            }.body<StorageUsageDto>()
            Result.success(usage)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
