package de.aarondietz.lehrerlog.data.repository

import de.aarondietz.lehrerlog.auth.FakeTokenStorage
import de.aarondietz.lehrerlog.data.StorageOwnerType
import de.aarondietz.lehrerlog.data.StorageQuotaDto
import de.aarondietz.lehrerlog.data.StorageUsageDto
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StorageRepositoryTest {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    @Test
    fun testGetQuotaSuccess() = runTest {
        val expectedQuota = StorageQuotaDto(
            ownerType = StorageOwnerType.SCHOOL,
            ownerId = "school-123",
            planId = "plan-456",
            planName = "Basic Plan",
            maxTotalBytes = 104857600, // 100MB
            maxFileBytes = 5242880, // 5MB
            usedTotalBytes = 52428800, // 50MB
            remainingBytes = 52428800 // 50MB
        )

        val mockEngine = MockEngine { request ->
            assertEquals("/api/storage/quota", request.url.encodedPath)
            respond(
                content = json.encodeToString(StorageQuotaDto.serializer(), expectedQuota),
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", "application/json")
            )
        }

        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(json)
            }
        }

        val tokenStorage = FakeTokenStorage()
        tokenStorage.saveAccessToken("mock-token")

        val repository = StorageRepository(httpClient, tokenStorage, "")

        val result = repository.getQuota()

        assertTrue(result.isSuccess)
        val quota = result.getOrThrow()
        assertEquals(expectedQuota.ownerType, quota.ownerType)
        assertEquals(expectedQuota.maxTotalBytes, quota.maxTotalBytes)
        assertEquals(expectedQuota.usedTotalBytes, quota.usedTotalBytes)
        assertEquals(expectedQuota.remainingBytes, quota.remainingBytes)
    }

    @Test
    fun testGetQuotaNotFound() = runTest {
        val mockEngine = MockEngine { request ->
            respond(
                content = """{"error":"Storage subscription not found"}""",
                status = HttpStatusCode.NotFound,
                headers = headersOf("Content-Type", "application/json")
            )
        }

        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(json)
            }
        }

        val tokenStorage = FakeTokenStorage()
        tokenStorage.saveAccessToken("mock-token")

        val repository = StorageRepository(httpClient, tokenStorage, "")

        val result = repository.getQuota()

        assertTrue(result.isFailure)
    }

    @Test
    fun testGetUsageSuccess() = runTest {
        val expectedUsage = StorageUsageDto(
            ownerType = StorageOwnerType.TEACHER,
            ownerId = "teacher-789",
            usedTotalBytes = 26214400, // 25MB
            updatedAt = "2026-01-15T10:30:00Z"
        )

        val mockEngine = MockEngine { request ->
            assertEquals("/api/storage/usage", request.url.encodedPath)
            respond(
                content = json.encodeToString(StorageUsageDto.serializer(), expectedUsage),
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", "application/json")
            )
        }

        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(json)
            }
        }

        val tokenStorage = FakeTokenStorage()
        tokenStorage.saveAccessToken("mock-token")

        val repository = StorageRepository(httpClient, tokenStorage, "")

        val result = repository.getUsage()

        assertTrue(result.isSuccess)
        val usage = result.getOrThrow()
        assertEquals(expectedUsage.ownerType, usage.ownerType)
        assertEquals(expectedUsage.ownerId, usage.ownerId)
        assertEquals(expectedUsage.usedTotalBytes, usage.usedTotalBytes)
    }

    @Test
    fun testGetUsageNetworkError() = runTest {
        val mockEngine = MockEngine {
            error("Network error simulated")
        }

        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(json)
            }
        }

        val tokenStorage = FakeTokenStorage()
        tokenStorage.saveAccessToken("mock-token")

        val repository = StorageRepository(httpClient, tokenStorage, "")

        val result = repository.getUsage()

        assertTrue(result.isFailure)
    }
}
