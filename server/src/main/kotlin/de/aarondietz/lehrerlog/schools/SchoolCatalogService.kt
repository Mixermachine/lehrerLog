package de.aarondietz.lehrerlog.schools

import de.aarondietz.lehrerlog.data.SchoolSearchResultDto
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.text.Normalizer

class SchoolCatalogService(
    private val storagePath: Path,
    private val downloadEndpoint: String,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    private val httpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()
    @Volatile
    private var catalog: List<SchoolCatalogEntry>? = null

    fun ensureLoaded(): List<SchoolCatalogEntry> {
        val existing = catalog
        if (existing != null) {
            return existing
        }

        synchronized(this) {
            val doubleCheck = catalog
            if (doubleCheck != null) {
                return doubleCheck
            }

            if (!Files.exists(storagePath)) {
                downloadAndStoreCatalog()
            }

            val content = Files.readString(storagePath)
            val loaded = json.decodeFromString<List<SchoolCatalogEntry>>(content)
            catalog = loaded
            return loaded
        }
    }

    fun search(query: String, limit: Int): List<SchoolSearchResultDto> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            return emptyList()
        }

        val normalizedQuery = normalize(trimmed)
        val results = ensureLoaded()
            .asSequence()
            .filter { entry ->
                val name = normalize(entry.name)
                val city = normalize(entry.city ?: "")
                name.contains(normalizedQuery) || city.contains(normalizedQuery)
            }
            .take(limit)
            .map { it.toSearchResult() }
            .toList()

        return results
    }

    fun findByCode(code: String): SchoolCatalogEntry? {
        return ensureLoaded().firstOrNull { it.code == code }
    }

    private fun downloadAndStoreCatalog() {
        storagePath.parent?.let { Files.createDirectories(it) }

        val query = """
            [out:json][timeout:180];
            area["ISO3166-1"="DE"]->.germany;
            nwr["amenity"="school"](area.germany);
            out center;
        """.trimIndent()

        val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8)
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$downloadEndpoint?data=$encodedQuery"))
            .GET()
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) {
            throw IllegalStateException("Failed to download school catalog: HTTP ${response.statusCode()}")
        }

        val overpass = json.decodeFromString<OverpassResponse>(response.body())
        val catalogEntries = overpass.elements.mapNotNull { element ->
            val name = element.tags["name"]
                ?: element.tags["official_name"]
                ?: element.tags["short_name"]
                ?: return@mapNotNull null

            val lat = element.lat ?: element.center?.lat
            val lon = element.lon ?: element.center?.lon

            SchoolCatalogEntry(
                code = "OSM-${element.type}-${element.id}",
                name = name,
                city = element.tags["addr:city"],
                postcode = element.tags["addr:postcode"],
                state = element.tags["addr:state"],
                latitude = lat,
                longitude = lon
            )
        }

        Files.writeString(storagePath, json.encodeToString(catalogEntries))
        catalog = catalogEntries
    }

    private fun normalize(value: String): String {
        val normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
        return normalized.replace(Regex("\\p{M}+"), "").lowercase()
    }
}

@Serializable
data class SchoolCatalogEntry(
    val code: String,
    val name: String,
    val city: String? = null,
    val postcode: String? = null,
    val state: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null
) {
    fun toSearchResult(): SchoolSearchResultDto {
        return SchoolSearchResultDto(
            code = code,
            name = name,
            city = city,
            postcode = postcode,
            state = state,
            latitude = latitude,
            longitude = longitude
        )
    }
}

@Serializable
data class OverpassResponse(
    val elements: List<OverpassElement> = emptyList()
)

@Serializable
data class OverpassElement(
    val type: String,
    val id: Long,
    val lat: Double? = null,
    val lon: Double? = null,
    val center: OverpassCenter? = null,
    val tags: Map<String, String> = emptyMap()
)

@Serializable
data class OverpassCenter(
    val lat: Double,
    val lon: Double
)
