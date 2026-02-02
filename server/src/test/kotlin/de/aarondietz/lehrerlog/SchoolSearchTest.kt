package de.aarondietz.lehrerlog

import de.aarondietz.lehrerlog.data.SchoolSearchResultDto
import de.aarondietz.lehrerlog.routes.schoolRoute
import de.aarondietz.lehrerlog.schools.SchoolCatalogEntry
import de.aarondietz.lehrerlog.schools.SchoolCatalogService
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.seconds

class SchoolSearchTest {

    private fun createCatalogFile(entries: List<SchoolCatalogEntry>): Path {
        val tempDir = Files.createTempDirectory("school-catalog-test")
        val path = tempDir.resolve("schools.json")
        val json = Json { prettyPrint = true }
        Files.writeString(path, json.encodeToString(entries))
        return path
    }

    @Test
    fun `search returns matching schools`() {
        val catalogPath = createCatalogFile(
            listOf(
                SchoolCatalogEntry(
                    code = "OSM-node-1",
                    name = "Gymnasium Musterstadt",
                    city = "Berlin",
                    postcode = "10115",
                    state = "Berlin"
                ),
                SchoolCatalogEntry(
                    code = "OSM-node-2",
                    name = "Grundschule Beispiel",
                    city = "Hamburg",
                    postcode = "20095",
                    state = "Hamburg"
                )
            )
        )
        val service = SchoolCatalogService(catalogPath, "http://example.invalid")
        service.initialize()

        val results = service.search("muster", 10)

        assertEquals(1, results.size)
        assertEquals("Gymnasium Musterstadt", results[0].name)
        assertNotNull(results[0].code)
    }

    @Test
    fun `school search route returns limited results`() = testApplication {
        val catalogPath = createCatalogFile(
            listOf(
                SchoolCatalogEntry(code = "OSM-node-1", name = "Schule Nord", city = "Berlin"),
                SchoolCatalogEntry(code = "OSM-node-2", name = "Schule Sued", city = "Berlin")
            )
        )
        val service = SchoolCatalogService(catalogPath, "http://example.invalid")
        service.initialize()

        application {
            install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) {
                json()
            }
            install(RateLimit) {
                register(RateLimitName("public")) {
                    rateLimiter(limit = 100, refillPeriod = 60.seconds)
                }
            }
            routing {
                schoolRoute(service)
            }
        }

        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        val response = client.get("/schools/search?query=schule&limit=1")

        assertEquals(HttpStatusCode.OK, response.status)
        val results = response.body<List<SchoolSearchResultDto>>()
        assertEquals(1, results.size)
    }
}
