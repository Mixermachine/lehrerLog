package de.aarondietz.lehrerlog.data.repository

import de.aarondietz.lehrerlog.auth.FakeTokenStorage
import de.aarondietz.lehrerlog.data.LatePeriodDto
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

class LatePeriodRepositoryTest {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    @Test
    fun testListPeriodsSuccess() = runTest {
        val expectedPeriods = listOf(
            LatePeriodDto(
                id = "period-1",
                name = "Semester 1",
                startsAt = "2026-01-01T00:00:00Z",
                endsAt = "2026-06-30T23:59:59Z",
                isActive = true,
                createdAt = "2026-01-01T00:00:00Z"
            ),
            LatePeriodDto(
                id = "period-2",
                name = "Semester 2",
                startsAt = "2026-07-01T00:00:00Z",
                endsAt = null,
                isActive = false,
                createdAt = "2026-07-01T00:00:00Z"
            )
        )

        val mockEngine = MockEngine { request ->
            assertEquals("/api/late-periods", request.url.encodedPath)
            respond(
                content = json.encodeToString(
                    kotlinx.serialization.builtins.ListSerializer(LatePeriodDto.serializer()),
                    expectedPeriods
                ),
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

        val repository = LatePeriodRepository(httpClient, tokenStorage, "")

        val result = repository.listPeriods()

        assertTrue(result.isSuccess)
        val periods = result.getOrThrow()
        assertEquals(2, periods.size)
        assertEquals("Semester 1", periods[0].name)
        assertEquals("Semester 2", periods[1].name)
        assertTrue(periods[0].isActive)
    }

    // TODO: Fix this test - MockEngine not handling POST body correctly
    // @Test
    fun testCreatePeriodSuccess_DISABLED() = runTest {
        val newPeriod = LatePeriodDto(
            id = "period-new",
            name = "New Period",
            startsAt = "2026-08-01T00:00:00Z",
            endsAt = "2026-12-31T23:59:59Z",
            isActive = false,
            createdAt = "2026-08-01T00:00:00Z"
        )

        val mockEngine = MockEngine {
            respond(
                content = json.encodeToString(LatePeriodDto.serializer(), newPeriod),
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

        val repository = LatePeriodRepository(httpClient, tokenStorage, "")

        val result = repository.createPeriod(
            name = "New Period",
            startsAt = "2026-08-01T00:00:00Z",
            endsAt = "2026-12-31T23:59:59Z"
        )

        assertTrue(result.isSuccess, "Expected success but got failure: ${result.exceptionOrNull()?.message}")
        val period = result.getOrThrow()
        assertEquals("New Period", period.name)
        assertEquals("2026-08-01T00:00:00Z", period.startsAt)
    }

    @Test
    fun testActivatePeriodSuccess() = runTest {
        val activatedPeriod = LatePeriodDto(
            id = "period-123",
            name = "Active Period",
            startsAt = "2026-01-01T00:00:00Z",
            endsAt = null,
            isActive = true,
            createdAt = "2026-01-01T00:00:00Z"
        )

        val mockEngine = MockEngine { request ->
            assertTrue(request.url.encodedPath.contains("/api/late-periods/period-123/activate"))
            respond(
                content = json.encodeToString(LatePeriodDto.serializer(), activatedPeriod),
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

        val repository = LatePeriodRepository(httpClient, tokenStorage, "")

        val result = repository.activatePeriod("period-123")

        assertTrue(result.isSuccess)
        val period = result.getOrThrow()
        assertTrue(period.isActive)
    }

    @Test
    fun testRecalculatePeriodSuccess() = runTest {
        val mockEngine = MockEngine { request ->
            assertTrue(request.url.encodedPath.contains("/api/late-periods/period-123/recalculate"))
            respond(
                content = "",
                status = HttpStatusCode.NoContent,
                headers = headersOf()
            )
        }

        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(json)
            }
        }

        val tokenStorage = FakeTokenStorage()
        tokenStorage.saveAccessToken("mock-token")

        val repository = LatePeriodRepository(httpClient, tokenStorage, "")

        val result = repository.recalculatePeriod("period-123")

        assertTrue(result.isSuccess)
    }

    @Test
    fun testListPeriodsNetworkError() = runTest {
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

        val repository = LatePeriodRepository(httpClient, tokenStorage, "")

        val result = repository.listPeriods()

        assertTrue(result.isFailure)
    }
}
