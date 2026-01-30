package de.aarondietz.lehrerlog

import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals

class HealthRouteTest {

    @Serializable
    private data class HealthResponseDto(
        val status: String,
        val database: String,
        val objectStorage: String
    )

    @Test
    fun `health endpoint returns db and object storage status`() = testApplication {
        application { module() }

        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        val response = client.get("/health")
        assertEquals(200, response.status.value)
        val body = response.body<HealthResponseDto>()
        assertEquals("ok", body.status)
        assertEquals("connected", body.database)
        assertEquals("unknown", body.objectStorage)
    }
}

