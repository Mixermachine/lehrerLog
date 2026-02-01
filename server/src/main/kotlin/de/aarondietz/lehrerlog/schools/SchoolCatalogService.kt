package de.aarondietz.lehrerlog.schools

import de.aarondietz.lehrerlog.data.SchoolSearchResultDto
import kotlinx.serialization.Serializable
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

    fun initialize(): List<SchoolCatalogEntry> {
        synchronized(this) {
            val existing = catalog
            if (existing != null) {
                return existing
            }

            if (!Files.exists(storagePath)) {
                downloadAndStoreCatalog()
            }

            return loadFromDisk()
        }
    }

    fun ensureLoaded(): List<SchoolCatalogEntry> {
        return catalog
            ?: throw IllegalStateException("School catalog is not loaded. Call initialize() at startup.")
    }

    fun search(query: String, limit: Int): List<SchoolSearchResultDto> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            return emptyList()
        }

        val normalizedQuery = normalize(trimmed)
        val tokens = normalizedQuery
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
        if (tokens.isEmpty()) {
            return emptyList()
        }
        val results = ensureLoaded()
            .asSequence()
            .mapNotNull { entry ->
                scoreEntry(entry, normalizedQuery, tokens)?.let { score ->
                    entry to score
                }
            }
            .sortedWith(
                compareByDescending<Pair<SchoolCatalogEntry, Int>> { it.second }
                    .thenBy { it.first.name.length }
            )
            .take(limit)
            .map { (entry, _) -> entry.toSearchResult() }
            .toList()

        return results
    }

    fun findByCode(code: String): SchoolCatalogEntry? {
        return ensureLoaded().firstOrNull { it.code == code }
    }

    private fun loadFromDisk(): List<SchoolCatalogEntry> {
        if (!Files.exists(storagePath)) {
            throw IllegalStateException("School catalog file not found at $storagePath")
        }

        val content = Files.readString(storagePath)
        val loaded = json.decodeFromString<List<SchoolCatalogEntry>>(content)
        if (loaded.isEmpty()) {
            throw IllegalStateException("School catalog is empty at $storagePath")
        }

        catalog = loaded
        return loaded
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

    private fun scoreEntry(
        entry: SchoolCatalogEntry,
        normalizedQuery: String,
        tokens: List<String>
    ): Int? {
        val name = normalize(entry.name)
        val city = normalize(entry.city ?: "")
        val postcode = normalize(entry.postcode ?: "")
        val state = normalize(entry.state ?: "")

        val nameWords = name.split(Regex("\\s+")).filter { it.isNotBlank() }
        val cityWords = city.split(Regex("\\s+")).filter { it.isNotBlank() }
        val stateWords = state.split(Regex("\\s+")).filter { it.isNotBlank() }

        var score = 0
        for (token in tokens) {
            val tokenScore = tokenMatchScore(
                token = token,
                name = name,
                city = city,
                postcode = postcode,
                state = state,
                nameWords = nameWords,
                cityWords = cityWords,
                stateWords = stateWords
            )
            if (tokenScore == 0) {
                return null
            }
            score += tokenScore
        }

        when {
            name.startsWith(normalizedQuery) -> score += 7
            name.contains(normalizedQuery) -> score += 6
            city.startsWith(normalizedQuery) -> score += 3
            city.contains(normalizedQuery) -> score += 2
        }

        return score
    }

    private fun tokenMatchScore(
        token: String,
        name: String,
        city: String,
        postcode: String,
        state: String,
        nameWords: List<String>,
        cityWords: List<String>,
        stateWords: List<String>
    ): Int {
        if (nameWords.any { it == token }) return 5
        if (nameWords.any { it.startsWith(token) }) return 4
        if (name.contains(token)) return 3

        if (postcode == token) return 4
        if (postcode.startsWith(token)) return 2

        if (cityWords.any { it == token }) return 3
        if (cityWords.any { it.startsWith(token) }) return 2
        if (city.contains(token)) return 1

        if (stateWords.any { it == token }) return 1

        if (token.length >= 4) {
            if (nameWords.any { levenshteinDistanceAtMost(it, token, 1) }) return 1
            if (cityWords.any { levenshteinDistanceAtMost(it, token, 1) }) return 1
        }

        return 0
    }

    private fun levenshteinDistanceAtMost(a: String, b: String, maxDistance: Int): Boolean {
        val lengthDiff = kotlin.math.abs(a.length - b.length)
        if (lengthDiff > maxDistance) return false
        if (a == b) return true
        if (maxDistance == 0) return false

        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)

        for (i in 1..a.length) {
            curr[0] = i
            var rowMin = curr[0]
            val aChar = a[i - 1]
            for (j in 1..b.length) {
                val cost = if (aChar == b[j - 1]) 0 else 1
                val insert = curr[j - 1] + 1
                val delete = prev[j] + 1
                val replace = prev[j - 1] + cost
                val value = minOf(insert, delete, replace)
                curr[j] = value
                if (value < rowMin) {
                    rowMin = value
                }
            }

            if (rowMin > maxDistance) return false

            val temp = prev
            prev = curr
            curr = temp
        }

        return prev[b.length] <= maxDistance
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
