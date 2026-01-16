package de.aarondietz.lehrerlog.data.api

import de.aarondietz.lehrerlog.sync.PushChangesRequest
import de.aarondietz.lehrerlog.sync.PushChangesResponse
import de.aarondietz.lehrerlog.sync.SyncChangesResponse
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

/**
 * API client for synchronization endpoints.
 */
class SyncApi(private val client: HttpClient, private val baseUrl: String) {

    /**
     * Get changes from the server since a specific sync log ID.
     */
    suspend fun getChanges(sinceLogId: Long): SyncChangesResponse {
        return client.get("$baseUrl/api/sync/changes") {
            parameter("since", sinceLogId)
        }.body()
    }

    /**
     * Push local changes to the server.
     */
    suspend fun pushChanges(request: PushChangesRequest): PushChangesResponse {
        return client.post("$baseUrl/api/sync/push") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }
}
